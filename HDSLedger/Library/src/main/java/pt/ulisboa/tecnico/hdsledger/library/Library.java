package pt.ulisboa.tecnico.hdsledger.library;

import com.google.gson.Gson;

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
                activateLogs, 1000);

        // Disable logs if necessary
        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }
    }

    /*
     * Creates a new account in the ledger
     * The request is not blocking and the response is received asynchronously
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

        if (consistencyMode.equals(ConsistencyMode.WEAK)){
            this.link.smallQuorumMulticast(request);
        } else {
            this.link.quorumMulticast(request);
        }
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
    private void logRequestResponse(LedgerRequest request, LedgerResponse response, boolean isSuccessful) {
        switch (request.getType()) {
            case CREATE -> {
                if (isSuccessful) {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format(
                                    "{0} - Account {1} was created with balance {2}",
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
                if (isSuccessful) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Transfer from {1} to {2} was successful. Current balance is {3}",
                            config.getId(), requestTransfer.getSourcePubKey(),
                            requestTransfer.getDestinationPubKey().toString(),
                            response.getUpdateAccount().getUpdatedBalance()));
                } else {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format(
                                    "{0} - Transfer from {1} to {2} was unsuccessful.",
                                    config.getId(), requestTransfer.getSourcePubKey().toString(),
                                    requestTransfer.getDestinationPubKey().toString()));
                }
            }
            case BALANCE -> {
                if (isSuccessful) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Balance of {1} is {2}",
                            config.getId(), request.getSenderId(),
                            response.getUpdateAccount().getUpdatedBalance()));
                } else {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format(
                                    "{0} - Failed to read balance of {1}",
                                    config.getId(), request.getSenderId()));
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
                        System.out.println("REPLIES TO: " + response.getRepliesTo());
                        Integer readNonce = response.getNonce();
                        if (readNonce != null) { // Means it's a response to a read request

                            // Get pending request associated with nonce
                            LedgerRequest request = this.requests.get(readNonce);
                            // If request is not present, it means that it was already processed
                            if (request == null)
                                continue;

                            if (request.getType() != Message.Type.BALANCE)
                                throw new LedgerException(ErrorMessage.InvalidResponse);

                            // Add response to corresponding nonce
                            this.responses.putIfAbsent(readNonce, new ArrayList<>());
                            List<LedgerResponse> ledgerResponses = this.responses.get(readNonce);
                            ledgerResponses.add(response);

                            LedgerRequestBalance requestBalance = new Gson().fromJson(request.getMessage(),
                                    LedgerRequestBalance.class);

                            switch (requestBalance.getConsistencyMode()) {
                                case WEAK -> {
                                    boolean isValidLedgerResponse = this.verifyResponseSignatures(response);

                                    if (!isValidLedgerResponse)
                                        throw new LedgerException(ErrorMessage.InvalidResponse);

                                    this.logRequestResponse(request, response, response.isSuccessful());
                                    this.knownConsensusInstance = response.getUpdateAccount()
                                            .getConsensusInstance();

                                    this.requests.remove(readNonce);
                                    this.responses.remove(readNonce);
                                }
                                case STRONG -> {
                                    if (ledgerResponses.size() < this.bigQuorumSize)
                                        break;

                                    boolean areResponsesValid = ledgerResponses.stream().map(LedgerResponse::getUpdateAccount).distinct().count() <= 1;

                                    if (!areResponsesValid) {
                                        // TODO
                                        // request consensus for read
                                    } 

                                    this.logRequestResponse(request, response, response.isSuccessful());
                                    this.knownConsensusInstance = response.getUpdateAccount()
                                            .getConsensusInstance();

                                    this.requests.remove(readNonce);
                                    this.responses.remove(readNonce);
                                }
                                case CONSENSUS -> {
                                    // TODO
                                }
                            }

                            continue;
                        }

                        /*
                         * If the block that contains the response is valid, an update account with
                         * the updated information about the client is returned with all the signatures
                         * of the nodes that have processed the block and the nonces of all the client
                         * requests that were processed in the block
                         * 
                         * If the block is not valid, the update account still contains the signatures
                         * and nodes but the balance is set to zero
                         */
                        List<Integer> nonces = response.getUpdateAccount().getNonces();

                        // Each nonce represent a request sent by this client
                        for (int nonce : nonces) {

                            // Get pending request associated with nonce
                            LedgerRequest request = this.requests.get(nonce);
                            // If request is not present, it means that it was already processed
                            if (request == null)
                                continue;

                            // Add response to corresponding nonce
                            this.responses.putIfAbsent(nonce, new ArrayList<>());
                            List<LedgerResponse> ledgerResponses = this.responses.get(nonce);
                            ledgerResponses.add(response);

                            switch (request.getType()) {
                                case CREATE, TRANSFER -> {
                                    // Wait for f+1 responses
                                    if (ledgerResponses.size() < this.smallQuorumSize)
                                        break;

                                    // At least one response will come from a correct node
                                    // If multiple responses, all of them should be the same
                                    LedgerResponse validLedgerResponse = ledgerResponses.stream()
                                            .filter(this::verifyResponseSignatures)
                                            .findFirst()
                                            .orElseThrow(() -> new LedgerException(ErrorMessage.InvalidResponse));

                                    // Clean to avoid unbounded memory usage
                                    validLedgerResponse.getUpdateAccount().getNonces().forEach(n -> {
                                        this.requests.remove(n);
                                        this.responses.remove(n);
                                    });

                                    this.logRequestResponse(request, validLedgerResponse,
                                            validLedgerResponse.isSuccessful());
                                    this.knownConsensusInstance = validLedgerResponse.getUpdateAccount()
                                            .getConsensusInstance();
                                }
                                default -> {
                                    throw new LedgerException(ErrorMessage.CannotParseMessage);
                                }
                            }

                        }
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