package pt.ulisboa.tecnico.hdsledger.library;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestBalance;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestCreate;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestTransfer;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestBalance.ConsistencyMode;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.LogManager;

import com.google.gson.Gson;

import java.util.concurrent.atomic.AtomicInteger;

public class Library {

    private static final CustomLogger LOGGER = new CustomLogger(Library.class.getName());
    // Client configs
    private final ProcessConfig[] clientConfigs;
    // Nodes configs
    private final ProcessConfig[] nodeConfigs;
    // Config details of node leader
    private final ProcessConfig leader;
    // Client identifier
    private final ProcessConfig config;
    // Link to communicate with blockchain nodes
    private PerfectLink link;
    // Current client nonce
    private AtomicInteger nonce = new AtomicInteger(0);
    // Known consensus instance (for read-your-writes)
    private int knownConsensusInstance = 0;
    // Responses received from the nodes <nonce, responses[]>, its an array because
    // we wait for f+1 responses
    private final Map<Integer, List<LedgerResponse>> responses = new ConcurrentHashMap<>();
    // Messages sent by the client <nonce, message>
    private final Map<Integer, LedgerRequest> requests = new ConcurrentHashMap<>();
    // Small quorum size (f+1)
    private int smallQuorumSize;

    public Library(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs) {
        this(clientConfig, nodeConfigs, clientConfigs, true);
    }

    public Library(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs,
            boolean activateLogs)
            throws LedgerException {

        this.nodeConfigs = nodeConfigs;
        this.clientConfigs = clientConfigs;
        this.config = clientConfig;

        // Get leader from nodes information
        Optional<ProcessConfig> leader = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findFirst();
        if (leader.isEmpty())
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        this.leader = leader.get();

        // Create link to communicate with nodes
        this.link = new PerfectLink(clientConfig, clientConfig.getPort(), nodeConfigs, LedgerResponse.class,
                activateLogs);

        // Disable logs if necessary
        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }

        // Compute small quorum size
        this.smallQuorumSize = Math.floorDiv(nodeConfigs.length - 1, 3) + 1;
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
        Optional<ProcessConfig> accountConfig = Arrays.stream(this.clientConfigs)
                .filter(c -> c.getId().equals(accountId))
                .findFirst();
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
        LedgerRequestBalance requestRead = new LedgerRequestBalance(accountPubKey, consistencyMode, this.knownConsensusInstance);
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
                .filter(c -> c.getId().equals(destinationId))
                .findFirst();
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
     * Verify if the signatures within a LedgerResponse are all valid and have the
     * minimum size of the small quorum
     * 
     * @param response LedgerResponse to verify
     */
    private boolean verifyResponseSignatures(LedgerResponse response) {

        // Response must have at least small quorum size signatures
        if (response.getSignatures().size() < this.smallQuorumSize)
            return false;

        String accountUpdateSerialized = new Gson().toJson(response.getUpdateAccount());
        for (var signature : response.getSignatures().entrySet()) {
            // Find public key of node that signed the response
            Optional<ProcessConfig> nodeConfig = Arrays.stream(this.nodeConfigs)
                    .filter(c -> c.getId().equals(signature.getKey()))
                    .findFirst();

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
                        Message message = link.receive();
                        // Separate thread to handle each message
                        switch (message.getType()) {
                            case REPLY -> {
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} - Received REPLY message from {1}",
                                                config.getId(), message.getSenderId()));

                                LedgerResponse response = (LedgerResponse) message;
                                List<Integer> nonces = response.getUpdateAccount().getNonces();
                                // Each nonce represent a request sent by this client
                                for (int nonce : nonces) {
                                    // Get request associated with nonce
                                    LedgerRequest request = this.requests.get(nonce);

                                    // If request is not present, it means that it was already processed
                                    if (request != null) {

                                        // Add response to corresponding nonce
                                        this.responses.putIfAbsent(nonce, new ArrayList<>());
                                        List<LedgerResponse> ledgerResponses = this.responses.get(nonce);
                                        ledgerResponses.add(response);

                                        switch (request.getType()) {
                                            case CREATE -> {
                                                // Wait for f+1 responses
                                                if (ledgerResponses.size() >= this.smallQuorumSize) {
                                                    for (LedgerResponse r : ledgerResponses) {
                                                        if (!verifyResponseSignatures(r)) {
                                                            // do nothing
                                                        }
                                                        // Remove processed messages
                                                        // One LedgerResponse may be a reply to multiple requests
                                                        for (Integer n : r.getUpdateAccount().getNonces()) {
                                                            this.requests.remove(n);
                                                            this.responses.remove(n);
                                                        }
                                                        LOGGER.log(Level.INFO,
                                                                MessageFormat.format(
                                                                        "{0} - Account {1} was created with balance {2}",
                                                                        config.getId(), request.getSenderId(),
                                                                        response.getUpdateAccount()
                                                                                .getUpdatedBalance()));

                                                        this.knownConsensusInstance = response.getUpdateAccount()
                                                                .getConsensusInstance();
                                                        // Don't need to process other responses for this specific nonce
                                                        break;
                                                    }
                                                }

                                            }
                                            case TRANSFER -> {
                                                // Wait for f+1 responses
                                                if (ledgerResponses.size() >= this.smallQuorumSize) {
                                                    for (LedgerResponse r : ledgerResponses) {
                                                        if (!verifyResponseSignatures(r)) {
                                                            // do nothing
                                                        }
                                                        // Remove processed messages
                                                        // One LedgerResponse may be a reply to multiple requests
                                                        for (Integer n : r.getUpdateAccount().getNonces()) {
                                                            this.requests.remove(n);
                                                            this.responses.remove(n);
                                                        }
                                                        LOGGER.log(Level.INFO,
                                                                MessageFormat.format(
                                                                        "{0} - Transfer from {1} to {2} was successful. Current balance is {3}",
                                                                        config.getId(), request.getSenderId(),
                                                                        response.getUpdateAccount().getUpdatedBalance(),
                                                                        response.getUpdateAccount()
                                                                                .getUpdatedBalance()));

                                                        this.knownConsensusInstance = response.getUpdateAccount()
                                                                .getConsensusInstance();
                                                        // Don't need to process other responses for this specific nonce
                                                        break;
                                                    }
                                                }

                                            }
                                            case BALANCE -> {

                                                LedgerRequestBalance requestBalance = new Gson()
                                                        .fromJson(request.getMessage(), LedgerRequestBalance.class);

                                                // Soft -> wait for 1 response
                                                // Hard -> wait for f+1 responses
                                                if (requestBalance.getConsistencyMode().equals("soft")) {
                                                    // TODO
                                                } else {
                                                    // TODO
                                                }

                                                // To use above, just a template for now
                                                LOGGER.log(Level.INFO,
                                                        MessageFormat.format("{0} - Balance of account {1} is {2}",
                                                                config.getId(), request.getSenderId(),
                                                                response.getUpdateAccount().getUpdatedBalance()));
                                            }
                                            default -> {
                                                throw new LedgerException(ErrorMessage.CannotParseMessage);
                                            }
                                        }

                                    }
                                }

                            }
                            case ACK -> {
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} - Received ACK {1} message from {2}",
                                                config.getId(), message.getMessageId(), message.getSenderId()));
                            }
                            default -> {
                                throw new LedgerException(ErrorMessage.CannotParseMessage);
                            }
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}