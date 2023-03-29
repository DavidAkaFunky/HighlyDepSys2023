package pt.ulisboa.tecnico.hdsledger.library;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestTransfer;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogManager;

import com.google.gson.Gson;

import java.util.concurrent.atomic.AtomicInteger;

public class Library {

    private static final CustomLogger LOGGER = new CustomLogger(Library.class.getName());
    // Client configs
    private final ProcessConfig[] clientConfigs;
    // Config details of node leader
    private final ProcessConfig leader;
    // Client identifier
    private final ProcessConfig config;
    // Link to communicate with blockchain nodes
    private PerfectLink link;
    // Map of responses from nodes
    private final Map<Integer, LedgerResponse> responses = new HashMap<>();
    // Current client nonce
    private AtomicInteger nonce = new AtomicInteger(0);

    public Library(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs) {
        this(clientConfig, nodeConfigs, clientConfigs, true);
    }

    public Library(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs, ProcessConfig[] clientConfigs,
            boolean activateLogs)
            throws LedgerException {

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
    }

    /*
     * Creates a new account in the ledger
     */
    public void create(String accountId) {

    }

    /*
     * Read account balance
     */
    public void read(String accountId, String consistencyMode) {

    }

    /*
     * Transfer money from one account to another
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

        // Create transfer request and sign it
        LedgerRequestTransfer requestTransfer = new LedgerRequestTransfer(currentNonce, sourcePubKey, destinationPubKey,
                amount);

        String requestTransferSerialized = new Gson().toJson(requestTransfer);

        String signature;
        try {
            signature = RSAEncryption.sign(requestTransferSerialized, config.getPrivateKeyPath());
        } catch (Exception e) {
            throw new LedgerException(ErrorMessage.FailedToSignMessage);
        }

        // Send generic ledger request with signature
        LedgerRequest request = new LedgerRequest(this.config.getId(), Message.Type.TRANSFER, requestTransferSerialized,
                signature);

        this.link.broadcast(request);
    }

    public void listen() {
        try {
            // Thread to listen on every request
            // This is not thread safe but it's okay because
            // a client only sends one request at a time
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

                                // Ignore replies from non-leader nodes
                                if (!message.getSenderId().equals(leader.getId()))
                                    continue;

                                // Add new values to the blockchain
                                LedgerResponse response = (LedgerResponse) message;
                                responses.put(response.getNonce(), response);
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