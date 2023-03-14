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

public class Library {

    private static final CustomLogger LOGGER = new CustomLogger(Library.class.getName());
    private static final String CONFIG = "../Service/src/main/resources/server_config.json";

    // Config details of node leader
    private final ProcessConfig leader;
    // Known blockchain
    private final List<String> blockchain = new ArrayList<>();
    // Client identifier
    private final ProcessConfig config;
    // Message sequence number
    private int messageId = 1;
    // Link to communicate with blockchain nodes
    private PerfectLink link;
    // Map of responses from nodes
    private final Map<Integer, LedgerResponse> responses = new HashMap<>();

    public Library(ProcessConfig clientConfig, ProcessConfig[] nodeConfigs) throws LedgerException {
        this.config = clientConfig;
        // Get leader from config file
        Optional<ProcessConfig> leader = Arrays.stream(new ProcessConfigBuilder().fromFile(CONFIG))
                .filter(ProcessConfig::isLeader).findFirst();
        if (leader.isEmpty())
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        this.leader = leader.get();
        this.link = new PerfectLink(clientConfig, clientConfig.getPort(), nodeConfigs, LedgerResponse.class);
    }

    public List<String> getBlockchain() {
        return blockchain;
    }

    private List<String> getBlockchainWithoutSpaces(List<String> blockchain) {
        List<String> blockchainWithoutSpaces = new ArrayList<>();
        for (String value : blockchain) {
            if(!value.equals(""))
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

        // Create message to send to blockchain service
        LedgerRequest request = new LedgerRequest(LedgerRequest.Type.APPEND, this.config.getId(),
                this.messageId++, value, this.blockchain.size());
        
        return requestBlockchainOperation(request);
    }

    /*
     * Read blockchain
     * This method is intentionally blocking
     *
     * @param value the value to be appended
     */
    public void read() {

        // Create message to send to blockchain service
        LedgerRequest request = new LedgerRequest(LedgerRequest.Type.APPEND, this.config.getId(),
                this.messageId++, "", this.blockchain.size());
        
        requestBlockchainOperation(request);
    }

    public List<String> requestBlockchainOperation(LedgerRequest request) {

        link.broadcast(request);

        LedgerResponse ledgerResponse;

        while ((ledgerResponse = responses.get(request.getMessageId())) == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Add new values to the blockchain
        List<String> blockchainValues = ledgerResponse.getValues();
        blockchain.addAll(ledgerResponse.getValues().stream().toList());

        responses.remove(request.getMessageId());
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
                        new Thread(() -> {
                            switch (message.getType()) {
                                case REPLY -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("Received REPLY message from {0}",
                                                    message.getSenderId()));
                                    System.out.println("Leader: " + leader.getId() + " Sender: " + message.getSenderId());
                                    if (!message.getSenderId().equals(leader.getId()))
                                        return;
                                    LedgerResponse response = (LedgerResponse) message;
                                    // Add new values to the blockchain
                                    responses.put(message.getMessageId(), response);
                                }
                                case ACK -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("Received ACK message from {0}",
                                                    message.getSenderId()));
                                    return;
                                }
                                default -> {
                                    throw new LedgerException(ErrorMessage.CannotParseMessage);
                                }
                            }

                        }).start();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* public List<String> read() throws LedgerException {

        // Create message to send to blockchain service
        LedgerRequest request = new LedgerRequest(LedgerRequest.Type.READ, this.config.getId(), this.messageId++,
                "",
                this.blockchain.size());

        try {
            Thread sendThread = new Thread(() -> {
                for (;;) {
                    try {
                        InetAddress address = InetAddress.getByName(leader.getHostname());

                        int port = leader.getClientPort();

                        // Sign message
                        String jsonString = new Gson().toJson(request);
                        Optional<String> signature;
                        try {
                            signature = Optional.of(RSAEncryption.sign(jsonString, config.getPrivateKeyPath()));
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new RuntimeException();
                        }

                        // Create UDP packet
                        SignedMessage message = new SignedMessage(jsonString, signature.get());
                        byte[] serializedMessage = new Gson().toJson(message).getBytes();
                        DatagramPacket packet = new DatagramPacket(serializedMessage, serializedMessage.length, address,
                                port);

                        // Send packet
                        socket.send(packet);
                        Thread.sleep(100);
                    } catch (IOException e) {
                        throw new LedgerException(ErrorMessage.SocketSendingError);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            sendThread.start();

            // Receive response
            DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
            socket.receive(response);
            sendThread.interrupt();

            // Verify signature
            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            SignedMessage responseData = new Gson().fromJson(new String(buffer), SignedMessage.class);
            if (!RSAEncryption.verifySignature(responseData.getMessage(), responseData.getSignature(),
                    leader.getPublicKeyPath())) {
                throw new LedgerException(ErrorMessage.SignatureDoesntMatch);
            }

            // Deserialize response
            LedgerResponse ledgerResponse = new Gson().fromJson(responseData.getMessage(), LedgerResponse.class);

            // Add new values to the blockchain
            List<String> blockchainValues = ledgerResponse.getValues();
            blockchain.addAll(ledgerResponse.getValues().stream().toList());

            return blockchainValues;

        } catch (IOException e) {
            throw new LedgerException(ErrorMessage.CannotOpenSocket);
        }
    } */

}