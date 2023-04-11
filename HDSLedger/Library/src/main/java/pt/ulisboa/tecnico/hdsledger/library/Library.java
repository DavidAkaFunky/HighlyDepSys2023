package pt.ulisboa.tecnico.hdsledger.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestBalance.ConsistencyMode;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogManager;

public class Library {

    private static final CustomLogger LOGGER = new CustomLogger(Library.class.getName());
    // Client configs
    private final ProcessConfig[] clientConfigs;
    // Nodes configs
    private final ProcessConfig[] nodeConfigs;
    // All configs
    private final ProcessConfig[] allConfigs;
    // Client identifier
    private final ProcessConfig config;
    // Responses received from the nodes <nonce, responses[]>, its an array because
    // we wait for f+1 responses
    private final Map<Integer, List<LedgerResponse>> responses = new ConcurrentHashMap<>();
    // Messages sent by the client <nonce, message>
    private final Map<Integer, LedgerRequest> requests = new ConcurrentHashMap<>();
    // Link to communicate with blockchain nodes
    private final PerfectLink link;
    // Current client nonce
    private final AtomicInteger nonce = new AtomicInteger(0);
    // Small quorum size (f+1)
    private final int smallQuorumSize;
    // Big quorum size (2f+1)
    private final int bigQuorumSize;
    // Known consensus instance (for read-your-writes)
    private int knownConsensusInstance = 0;

    public Library(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs) {
        this(clientConfig, nodeConfigs, clientConfigs, true);
    }

    public Library(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs,
            boolean activateLogs) throws LedgerException {

        this.nodeConfigs = nodeConfigs;
        this.clientConfigs = clientConfigs;
        this.config = clientConfig;

        int f = Math.floorDiv(nodeConfigs.length - 1, 3);
        this.smallQuorumSize = f + 1;
        this.bigQuorumSize = Math.floorDiv(nodeConfigs.length + f, 2) + 1;

        // n = 3f + 1 <=> f = (n - 1) / 3 => 2f + 1 = (2n + 1) / 3
        this.allConfigs = new ProcessConfig[nodeConfigs.length + clientConfigs.length];
        System.arraycopy(nodeConfigs, 0, this.allConfigs, 0, nodeConfigs.length);
        System.arraycopy(clientConfigs, 0, this.allConfigs, nodeConfigs.length, clientConfigs.length);

        // Create link to communicate with nodes
        this.link = new PerfectLink(clientConfig, clientConfig.getPort(), nodeConfigs, LedgerResponse.class,
                activateLogs, 5000);

        // Disable logs if necessary
        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }
    }

    /*
     * Creates a new account in the ledger
     * The request is not blocking and the response is received asynchronously
     * The request will be sent to a small quorum of nodes that includes the leader
     * and will wait for a small quorum of responses
     *
     * @param accountId Account identifier
     */
    public void create() {

        int currentNonce = this.nonce.getAndIncrement();

        PublicKey accountPubKey;
        try {
            accountPubKey = RSAEncryption.readPublicKey(this.config.getPublicKeyPath());
        } catch (Exception e) {
            throw new LedgerException(ErrorMessage.FailedToReadPublicKey);
        }

        // Each LedgerRequest receives a specific ledger request which is serialized and
        // signed
        LedgerRequestCreate requestCreate = new LedgerRequestCreate(currentNonce, accountPubKey);
        String serializedCreateRequest = new Gson().toJson(requestCreate);
        String signature;
        try {
            signature = RSAEncryption.sign(serializedCreateRequest, config.getPrivateKeyPath());
        } catch (Exception e) {
            throw new LedgerException(ErrorMessage.FailedToSignMessage);
        }

        // Send generic ledger request
        LedgerRequest request = new LedgerRequest(this.config.getId(), Message.Type.CREATE, serializedCreateRequest,
                signature);

        // Add to pending requests map
        this.requests.put(currentNonce, request);

        this.link.smallQuorumMulticast(request);
    }

    /*
     * Transfer money from one account to another
     * The request is not blocking and the response is received asynchronously
     * The request will be sent to a small quorum of nodes that includes the leader
     * and will wait for a small quorum of responses
     *
     * @param sourceId Source account identifier
     *
     * @param destinationId Destination account identifier
     *
     * @param amount Amount to transfer
     */
    public void transfer(String sourceId, String destinationId, BigDecimal amount) {

        int currentNonce = this.nonce.getAndIncrement();

        // Get source and destination public keys
        Optional<ProcessConfig> sourceConfig = Arrays.stream(this.clientConfigs).filter(c -> c.getId().equals(sourceId))
                .findFirst();
        Optional<ProcessConfig> destinationConfig = Arrays.stream(this.clientConfigs)
                .filter(c -> c.getId().equals(destinationId)).findFirst();

        if (sourceConfig.isEmpty() || destinationConfig.isEmpty())
            throw new LedgerException(ErrorMessage.InvalidAccount);

        PublicKey sourcePubKey, destinationPubKey;
        try {
            sourcePubKey = RSAEncryption.readPublicKey(sourceConfig.get().getPublicKeyPath());
            destinationPubKey = RSAEncryption.readPublicKey(destinationConfig.get().getPublicKeyPath());
        } catch (Exception e) {
            throw new LedgerException(ErrorMessage.FailedToReadPublicKey);
        }

        // Each LedgerRequest receives a specific ledger request which is serialized and
        // signed
        LedgerRequestTransfer requestTransfer = new LedgerRequestTransfer(currentNonce, sourcePubKey, destinationPubKey,
                amount);
        String requestTransferSerialized = new Gson().toJson(requestTransfer);
        String signature;
        try {
            signature = RSAEncryption.sign(requestTransferSerialized, config.getPrivateKeyPath());
        } catch (Exception e) {
            throw new LedgerException(ErrorMessage.FailedToSignMessage);
        }

        // Send generic ledger request
        LedgerRequest request = new LedgerRequest(this.config.getId(), Message.Type.TRANSFER, requestTransferSerialized,
                signature);

        // Add to pending requests map
        this.requests.put(currentNonce, request);

        this.link.smallQuorumMulticast(request);
    }

    /*
     * Read account balance
     * The request is not blocking and the response is received asynchronously
     * If the consistency mode is WEAK the request will be sent to a small quorum
     * that
     * includes the leader and will wait for a single response
     * If the consistency mode is STRONG the request will be sent to every node and
     * will wait for a small quroum of responses.
     * The strong read may fail if not all nodes respond with the same value and
     * in that case it fallbacks to a CONSENSUS read which is a requeset that
     * will be inserted in a block and will be executed by the consensus protocol.
     *
     * @param accountId Account identifier
     *
     * @param consistencyMode Consistency mode
     */
    public void balance(String accountId, ConsistencyMode consistencyMode) {

        int currentNonce = this.nonce.getAndIncrement();

        // Get account public key
        Optional<ProcessConfig> accountConfig = Arrays.stream(this.allConfigs)
                .filter(c -> c.getId().equals(accountId)).findFirst();
        if (accountConfig.isEmpty())
            throw new LedgerException(ErrorMessage.InvalidAccount);
        PublicKey accountPubKey;
        try {
            accountPubKey = RSAEncryption.readPublicKey(accountConfig.get().getPublicKeyPath());
        } catch (Exception e) {
            throw new LedgerException(ErrorMessage.FailedToReadPublicKey);
        }

        // Each LedgerRequest receives a specific ledger request which is serialized and
        // signed
        consistencyMode = ConsistencyMode.CONSENSUS;
        LedgerRequestBalance requestRead = new LedgerRequestBalance(accountPubKey, consistencyMode,
                this.knownConsensusInstance, currentNonce);
        String requestTransferSerialized = new Gson().toJson(requestRead);
        String signature;
        try {
            signature = RSAEncryption.sign(requestTransferSerialized, config.getPrivateKeyPath());
        } catch (Exception e) {
            throw new LedgerException(ErrorMessage.FailedToSignMessage);
        }

        // Send generic ledger request
        LedgerRequest request = new LedgerRequest(this.config.getId(), Message.Type.BALANCE, requestTransferSerialized,
                signature);

        // Add to pending requests map
        this.requests.put(currentNonce, request);

        if (consistencyMode.equals(ConsistencyMode.WEAK)) {
            this.link.smallQuorumMulticast(request);
        } else {
            this.link.quorumMulticast(request);
        }
    }

    /*
     * Find id by public key
     * 
     * @param publicKey Public key
     */
    private String findIdByPublicKey(PublicKey publicKey) {
        for (ProcessConfig config : this.allConfigs) {
            try {
                PublicKey accountPubKey = RSAEncryption.readPublicKey(config.getPublicKeyPath());
                if (accountPubKey.equals(publicKey)) {
                    return config.getId();
                }
            } catch (Exception e) {
                throw new LedgerException(ErrorMessage.FailedToReadPublicKey);
            }
        }
        return null;
    }

    /*
     * Log request response to console
     * 
     * @param request Request
     * 
     * @param response Response
     * 
     * @param isSuccessful True if the request was successful
     */
    private void logRequestResponse(LedgerRequest request, LedgerResponse response, boolean isSuccessful,
            boolean isValid) {
        switch (request.getType()) {
            case CREATE -> {
                if (!isValid) {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format(
                                    "{0} - Invalid response to account creation request of {1}.",
                                    config.getId(), request.getSenderId()));
                } else if (isSuccessful) {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format(
                                    "{0} - Account {1} was created. Current balance is {2}",
                                    config.getId(), request.getSenderId(),
                                    response.getUpdateAccount().getUpdatedBalance()));
                } else {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format(
                                    "{0} - Failed to create account {1}",
                                    config.getId(), request.getSenderId()));
                }
            }
            case TRANSFER -> {
                LedgerRequestTransfer requestTransfer = request.deserializeTransfer();
                if (!isValid) {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format(
                                    "{0} - Invalid response to transfer request to {1}.",
                                    config.getId(), findIdByPublicKey(requestTransfer.getDestinationPubKey())));
                } else if (isSuccessful) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Transfer to {1} was successful. Current balance is {2}",
                            config.getId(), findIdByPublicKey(requestTransfer.getDestinationPubKey()),
                            response.getUpdateAccount().getUpdatedBalance()));
                } else {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format(
                                    "{0} - Transfer to {1} was unsuccessful.",
                                    config.getId(), findIdByPublicKey(requestTransfer.getDestinationPubKey())));
                }
            }
            case BALANCE -> {
                LedgerRequestBalance requestBalance = request.deserializeBalance();
                System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(response));
                if (!isValid) {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format(
                                    "{0} - Invalid response to balance request of {1}.",
                                    config.getId(), findIdByPublicKey(requestBalance.getAccountPubKey())));
                } else if (isSuccessful) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Balance of {1} is {2}",
                            config.getId(), findIdByPublicKey(requestBalance.getAccountPubKey()),
                            response.getUpdateAccount().getUpdatedBalance()));
                } else {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format(
                                    "{0} - Failed to read balance of {1}",
                                    config.getId(), findIdByPublicKey(requestBalance.getAccountPubKey())));
                }
            }
            default -> {
                LOGGER.log(Level.INFO, MessageFormat.format(
                        "{0} - Unknown request type {1}",
                        config.getId(), request.getType()));
            }
        }
    }

    /*
     * Verify if the signatures within a LedgerResponse are all valid and have the
     * minimum size of the small quorum
     *
     * @param response LedgerResponse to verify
     */
    private boolean verifyResponseSignatures(LedgerResponse response) {

        // if(response.getUpdateAccount().getNonces().equals(response.getNonces()))

        // Response must have at least small quorum size signatures
        if (response.getSignatures().size() < this.smallQuorumSize)
            return false;

        String accountUpdateSerialized = new Gson().toJson(response.getUpdateAccount());
        for (var signature : response.getSignatures().entrySet()) {
            // Find public key of node that signed the response
            Optional<ProcessConfig> nodeConfig = Arrays.stream(this.nodeConfigs)
                    .filter(c -> c.getId().equals(signature.getKey())).findFirst();

            if (nodeConfig.isEmpty())
                return false;

            // Verify signature
            try {
                if (!RSAEncryption.verifySignature(accountUpdateSerialized, signature.getValue(),
                        nodeConfig.get().getPublicKeyPath()))
                    return false;
            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    private void handleReadResponse(LedgerResponse response, Integer readNonce) {

        // Get pending request associated with nonce
        LedgerRequest request = this.requests.get(readNonce);
        // If request is not present, it means that it was already processed
        if (request == null)
            return;

        // Requests that have a readNonce must be a response to a balance request
        if (request.getType() != Message.Type.BALANCE)
            throw new LedgerException(ErrorMessage.InvalidResponse);

        // Add response to corresponding nonce
        this.responses.putIfAbsent(readNonce, new ArrayList<>());
        List<LedgerResponse> ledgerResponses = this.responses.get(readNonce);
        ledgerResponses.add(response);

        // Get original request
        LedgerRequestBalance requestBalance = request.deserializeBalance();
        switch (requestBalance.getConsistencyMode()) {
            case WEAK -> {
                // Wait for a single valid response
                boolean isValidLedgerResponse = this.verifyResponseSignatures(response);

                this.logRequestResponse(request, response, response.isSuccessful(), isValidLedgerResponse);

                if (!isValidLedgerResponse) {
                    return;
                }

                this.knownConsensusInstance = response.getUpdateAccount().getConsensusInstance();
                this.requests.remove(readNonce);
                this.responses.remove(readNonce);
            }
            case STRONG -> {
                // Wait for 2f+1 responses
                if (ledgerResponses.size() < this.bigQuorumSize)
                    break;

                boolean areResponsesValid = ledgerResponses.stream()
                        .map(LedgerResponse::getUpdateAccount).distinct().count() <= 1;

                this.logRequestResponse(request, response, response.isSuccessful(), areResponsesValid);

                if (!areResponsesValid) {
                    // TODO: Should send CONSENSUS request
                    return;
                }

                this.knownConsensusInstance = response.getUpdateAccount().getConsensusInstance();
                this.requests.remove(readNonce);
                this.responses.remove(readNonce);
            }
            case CONSENSUS -> {
                boolean isValidLedgerResponse = this.verifyResponseSignatures(response);

                this.logRequestResponse(request, response, response.isSuccessful(),
                        isValidLedgerResponse);

                if (!isValidLedgerResponse) {
                    return;
                }

                this.knownConsensusInstance = response.getUpdateAccount().getConsensusInstance();
                this.requests.remove(readNonce);
                this.responses.remove(readNonce);
            }
        }
    }

    private void handleCreateOrTransferResponse(LedgerResponse response) {

        // Get nonces of requests that were processed in the block
        List<Integer> nonces = response.getUpdateAccount().getNonces();

        // Each nonce represent a request sent by this client
        for (int nonce : nonces) {

            // Get pending request associated with nonce
            LedgerRequest request = this.requests.get(nonce);
            // If request is not present, it means that it was already processed
            if (request == null)
                return;

            // Add response to corresponding nonce
            this.responses.putIfAbsent(nonce, new ArrayList<>());
            List<LedgerResponse> ledgerResponses = this.responses.get(nonce);
            ledgerResponses.add(response);

            switch (request.getType()) {
                case CREATE, TRANSFER -> {
                    // Wait for a single valid response
                    boolean isValidLedgerResponse = this.verifyResponseSignatures(response);

                    this.logRequestResponse(request, response, response.isSuccessful(),
                            isValidLedgerResponse);

                    if (!isValidLedgerResponse) {
                        return;
                    }

                    this.knownConsensusInstance = response.getUpdateAccount().getConsensusInstance();
                    this.requests.remove(nonce);
                    this.responses.remove(nonce);
                }
                case BALANCE -> {
                    //
                }
                default -> {
                    throw new LedgerException(ErrorMessage.CannotParseMessage);
                }
            }

        }
    }

    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {

                        // Receive message from nodes
                        Message message = link.receive();

                        switch (message.getType()) {
                            case ACK -> {
                                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK {1} message from {2}",
                                        config.getId(), message.getMessageId(), message.getSenderId()));
                                continue;
                            }
                            case IGNORE -> {
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} - Received IGNORE {1} message from {2}",
                                                config.getId(), message.getMessageId(), message.getSenderId()));
                                continue;
                            }
                            case REPLY -> {
                                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received REPLY message from {1}",
                                        config.getId(), message.getSenderId()));
                            }
                            default -> {
                                throw new LedgerException(ErrorMessage.CannotParseMessage);
                            }
                        }

                        LedgerResponse response = (LedgerResponse) message;

                        // ReadNonce parameter is only set for balance requests
                        Integer readNonce = response.getNonce();
                        if (readNonce != null) {
                            this.handleReadResponse(response, readNonce);
                            continue;
                        }

                        this.handleCreateOrTransferResponse(response);
                    }
                } catch (LedgerException e) {
                    e.printStackTrace();
                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} - EXCEPTION: {1}", config.getId(), e.getMessage()));
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}