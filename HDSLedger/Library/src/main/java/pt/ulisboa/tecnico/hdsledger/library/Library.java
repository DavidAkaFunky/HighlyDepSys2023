package pt.ulisboa.tecnico.hdsledger.library;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.concurrent.atomic.AtomicInteger;

public class Library {

    private static final CustomLogger LOGGER = new CustomLogger(Library.class.getName());
    // Config details of node leader
    private final ProcessConfig leader;
    // Known blockchain
    private final List<String> blockchain = new ArrayList<>();
    // Client identifier
    private final ProcessConfig config;
    // Link to communicate with blockchain nodes
    private PerfectLink link;
    // Map of responses from nodes
    private final Map<Integer, LedgerResponse> responses = new HashMap<>();
    // Current request ID
    private AtomicInteger requestId = new AtomicInteger(0);

    public Library(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs) {
        this(clientConfig, nodeConfigs, true);
    }

    public Library(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs, boolean activateLogs)
            throws LedgerException {

        this.config = clientConfig;

        // Get leader from nodes information
        Optional<ProcessConfig> leader = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findFirst();
        if (leader.isEmpty())
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        this.leader = leader.get();
        
        this.link = new PerfectLink(clientConfig, clientConfig.getPort(), nodeConfigs, LedgerResponse.class,
                activateLogs);

        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }
    }

    public List<String> getBlockchain() {
        return blockchain;
    }

    private List<String> getBlockchainWithoutSpaces(List<String> blockchain) {
        List<String> blockchainWithoutSpaces = new ArrayList<>();
        for (String value : blockchain) {
            if (!value.equals(""))
                blockchainWithoutSpaces.add(value);
        }
        return blockchainWithoutSpaces;
    }

    /*
     * Print the known blockchain content
     */
    public void printBlockchain() {
        System.out.println("Known blockchain content: " + getBlockchainWithoutSpaces(getBlockchain()));
    }

    /*
     * Print the new blockchain content
     *
     * @param blockchainValues the new blockchain content
     */
    public void printNewBlockchainValues(List<String> blockchainValues) {
        System.out.println("New blockchain content: " + getBlockchainWithoutSpaces(blockchainValues));
    }

    /*
     * Append a value to the blockchain
     * This method is intentionally blocking
     *
     * @param value the value to be appended
     */
    public List<String> append(String value) {

        return requestBlockchainOperation(value);
    }

    /*
     * Read blockchain
     * This method is intentionally blocking
     *
     * @param value the value to be appended
     */
    public void read() {

        requestBlockchainOperation("");
    }

    public List<String> requestBlockchainOperation(String value) {

        int currentRequestId = this.requestId.getAndIncrement();

        // Sign client input
        String signature;
        try{
            signature = RSAEncryption.sign(value, config.getPrivateKeyPath());
        } catch (Exception e) {
            throw new LedgerException(ErrorMessage.FailedToSignMessage);
        }

        LedgerRequest request = new LedgerRequest(LedgerRequest.Type.REQUEST, this.config.getId(), currentRequestId,
                value, this.blockchain.size());

        request.setClientSignature(signature);

        this.link.broadcast(request);

        LedgerResponse ledgerResponse;

        while ((ledgerResponse = responses.get(currentRequestId)) == null) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Add new values to the blockchain
        List<String> blockchainValues = ledgerResponse.getValues();
        blockchain.addAll(ledgerResponse.getValues().stream().toList());

        responses.remove(currentRequestId);
        return blockchainValues;

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
                                responses.put(response.getRequestId(), response);
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