package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.SignedMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class LedgerService implements UDPService {

    private final String nodeId;
    private final NodeService service;
    private final PerfectLink link;

    private final Map<String, Set<Integer>> clientRequests = new ConcurrentHashMap<>();
    private static final CustomLogger LOGGER = new CustomLogger(LedgerService.class.getName());
    private Thread thread;

    public LedgerService(String nodeId, NodeService service, PerfectLink link) {
        this.nodeId = nodeId;
        this.service = service;
        this.link = link;
    }

    public Optional<LedgerResponse> handleAppendRequest(String clientId, int messageId,
            String value, int clientKnownBlockchainSize) {
        return requestConsensus(clientId, messageId, value, clientKnownBlockchainSize);
    }

    public Optional<LedgerResponse> handleReadRequest(String clientId, int messageId, int clientKnownBlockchainSize) {
        return requestConsensus(clientId, messageId, "", clientKnownBlockchainSize);
    }

    public Optional<LedgerResponse> requestConsensus(String clientId, int messageId, String value,
            int clientKnownBlockchainSize) {

        // Check if client has already sent this request
        clientRequests.putIfAbsent(clientId, ConcurrentHashMap.newKeySet());
        System.out.println("VALUE ALREADY EXISTS: " + clientRequests.get(clientId).contains(messageId));
        boolean isNewMessage = clientRequests.get(clientId).add(messageId);

        LOGGER.log(Level.INFO, "Request for consensus");

        if (isNewMessage) {

            System.out.println("client sequence:" + messageId);
            LOGGER.log(Level.INFO, "Starting consensus");

            // Start consensus instance
            int consensusInstance = service.startConsensus(value);
            Map<Integer, String> blockchain;
            for (;;) {
                // Wait for consensus to finish
                blockchain = service.getBlockchain();
                System.out.println("BLOCKCHAIN SIZE: " + blockchain.size());
                System.out.println("CONSENSUS INSTANCE: " + consensusInstance);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (blockchain.size() >= consensusInstance)
                    break;
            }

            LOGGER.log(Level.INFO, "Consensus finished");
            LOGGER.log(Level.INFO, MessageFormat.format("New blockchain: {0}",service.getBlockchainAsList()));

            return Optional.of(new LedgerResponse(nodeId, messageId,
                    service.getBlockchainStartingAtInstance(clientKnownBlockchainSize)));
        }

        LOGGER.log(Level.INFO, "Not a new request, ignoring");
        return Optional.empty();
    }

    public Thread getThread() {
        return thread;
    }

    public void killThread() {
        thread.interrupt();
    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            // This is not thread safe but it's okay because
            // a client only sends one request at a time
            // thread listening for client requests on clientPort {Append, Read}
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();
                        // Separate thread to handle each message
                        new Thread(() -> {
                            Optional<LedgerResponse> response;
                            switch (message.getType()) {

                                case APPEND -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received APPEND message from {1}",
                                                    nodeId, message.getSenderId()));
                                    LedgerRequest request = (LedgerRequest) message;
                                    response = handleAppendRequest(message.getSenderId(), message.getMessageId(),
                                            request.getArg(), request.getBlockchainSize());
                                }
                                case READ -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received READ message from {1}",
                                                    nodeId, message.getSenderId()));
                                    LedgerRequest request = (LedgerRequest) message;
                                    response = handleReadRequest(message.getSenderId(), message.getMessageId(),
                                        request.getBlockchainSize());
                                }
                                case ACK -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received ACK message from {1}",
                                                    nodeId, message.getSenderId()));
                                    return;
                                }
                                default -> {
                                    throw new LedgerException(ErrorMessage.CannotParseMessage);
                                }
                            }

                            if (response.isEmpty())
                                return;

                            link.send(message.getSenderId(), response.get());

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

    //@Override
    //public void listen() {
    //    // Thread to listen on every request
    //    // This is not thread safe but it's okay because
    //    // a client only sends one request at a time
    //    // thread listening for client requests on clientPort {Append, Read}
    //    new Thread(() -> {

    //        try {

    //            // Create socket to listen for client requests
    //            int port = config.getClientPort();
    //            InetAddress address = InetAddress.getByName(config.getHostname());
    //            DatagramSocket socket = new DatagramSocket(port, address);

    //            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Started LedgerService on {1}:{2}",
    //                    config.getId(), address, port));

    //            for (;;) {

    //                // Packet to receive client requests
    //                // TODO: Can this be moved outside the loop?
    //                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);

    //                // Receive client request
    //                socket.receive(packet);

    //                // Spawn thread to handle client request
    //                // Runnable with parameters is used to avoid race conditions between receiving
    //                // and reading data
    //                // due to multiple packets being received at the same time
    //                new Thread(new Runnable() {
    //                    private InetAddress clientAddress = packet.getAddress();
    //                    private int clientPort = packet.getPort();
    //                    private byte[] buffer = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());

    //                    @Override
    //                    public void run() {
    //                        try {
    //                            // Deserialize client request
    //                            SignedMessage requestData = new Gson().fromJson(new String(buffer),
    //                                    SignedMessage.class);
    //                            // TODO: Uncomment block below to verify signature
    //                            /*
    //                             * if (!RSAEncryption.verifySignature(responseData.getMessage(),
    //                             * responseData.getSignature(), leader.getPublicKeyPath())) {
    //                             * throw new LedgerException(ErrorMessage.SignatureDoesntMatch);
    //                             * }
    //                             */
    //                            LedgerRequest message = new Gson().fromJson(requestData.getMessage(),
    //                                    LedgerRequest.class);

    //                            Optional<LedgerResponse> response;
    //                            // Handle client request
    //                            switch (message.getType()) {
    //                                case APPEND -> {
    //                                    response = handleAppendRequest(message.getSenderId(), message.getClientSeq(),
    //                                            message.getArg(), message.getBlockchainSize());
    //                                    break;
    //                                }
    //                                case READ -> {
    //                                    response = handleReadRequest(message.getSenderId(), message.getClientSeq(),
    //                                            message.getBlockchainSize());
    //                                    break;
    //                                }
    //                                default -> {
    //                                    throw new LedgerException(ErrorMessage.CannotParseMessage);
    //                                }
    //                            }

    //                            if (response.isEmpty()) {
    //                                return;
    //                            }

    //                            LedgerResponse ledgerResponse = response.get();
    //                            System.out.println("Ledger response:");
    //                            System.out.println(ledgerResponse.getConsensusInstance());
    //                            System.out.println(ledgerResponse.getValues());

    //                            String jsonString = new Gson().toJson(ledgerResponse);
    //                            Optional<String> signature;
    //                            try {
    //                                signature = Optional.of(RSAEncryption.sign(jsonString, config.getPrivateKeyPath()));
    //                            } catch (FileNotFoundException e) {
    //                                throw new LedgerException(ErrorMessage.ConfigFileNotFound);
    //                            } catch (Exception e) {
    //                                e.printStackTrace();
    //                                throw new RuntimeException();
    //                            }

    //                            SignedMessage signedMessage = new SignedMessage(jsonString, signature.get());
    //                            byte[] serializedMessage = new Gson().toJson(signedMessage).getBytes();
    //                            DatagramPacket packet = new DatagramPacket(serializedMessage, serializedMessage.length,
    //                                    clientAddress, clientPort);

    //                            // Reply to client
    //                            socket.send(packet);

    //                        } catch (IOException | JsonSyntaxException e) {
    //                            socket.close();
    //                            throw new LedgerException(ErrorMessage.CannotParseMessage);
    //                        }
    //                    }
    //                }).start();
    //            }

    //        } catch (SocketException | UnknownHostException e) {
    //            throw new LedgerException(ErrorMessage.CannotOpenSocket);
    //        } catch (IOException e) {
    //            e.printStackTrace();
    //            // throw new LedgerException(ErrorMessage.SocketReceivingError);
    //        }
    //    }).start();
    //}

}
