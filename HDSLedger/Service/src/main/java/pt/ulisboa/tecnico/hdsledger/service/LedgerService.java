package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    public Optional<LedgerResponse> requestConsensus(String clientId, int messageId, String value,
            int requestId, int clientKnownBlockchainSize) {

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
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (blockchain.size() >= consensusInstance)
                    break;
            }

            LOGGER.log(Level.INFO, "Consensus finished");
            LOGGER.log(Level.INFO, MessageFormat.format("New blockchain: {0}",service.getBlockchainAsList()));

            return Optional.of(new LedgerResponse(nodeId, requestId, consensusInstance, service.getBlockchainStartingAtInstance(clientKnownBlockchainSize)));
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
                                case REQUEST -> {
                                    LedgerRequest request = (LedgerRequest) message;
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received {1} message from {2}",
                                                    nodeId, request.getArg().equals("") ? "READ" : "APPEND", message.getSenderId()));
                                    response = requestConsensus(message.getSenderId(), message.getMessageId(),
                                            request.getArg(), request.getRequestId(), request.getBlockchainSize());
                                }
                                case ACK -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received ACK message from {1}",
                                                    nodeId, message.getSenderId()));
                                    return;
                                }
                                case IGNORE -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    nodeId, message.getSenderId()));
                                    return;
                                }
                                default -> {
                                    System.out.println(message.getType());
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

}
