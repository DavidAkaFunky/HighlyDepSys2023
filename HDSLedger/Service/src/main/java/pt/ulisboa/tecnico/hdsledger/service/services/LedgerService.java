package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestCreate;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestTransfer;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class LedgerService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(LedgerService.class.getName());
    // Clients configurations
    private final ProcessConfig[] clientConfigs;
    // Node identifier
    private final String nodeId;
    // Node service
    private final NodeService service;
    // Link to communicate with blockchain nodes
    private final PerfectLink link;
    // Map of requests from clients
    private final Map<String, Integer> clientRequests = new ConcurrentHashMap<>();
    // Thread to run service
    private Thread thread;
    // Map of unconfirmed transactions
    private final Map<String, LedgerRequest> unconfirmedTransactions = new ConcurrentHashMap<>();
    // Block size
    private final int blockSize;

    public LedgerService(ProcessConfig[] clientConfigs, String nodeId, NodeService service, PerfectLink link, int blockSize) {
        this.clientConfigs = clientConfigs;
        this.nodeId = nodeId;
        this.service = service;
        this.link = link;
        this.blockSize = blockSize;
    }

    public Thread getThread() {
        return thread;
    }

    public void killThread() {
        thread.interrupt();
    }

    private boolean verifyClientSignature(LedgerRequest request) {

        // Find config of the sender
        Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientConfigs)
                .filter(c -> c.getId().equals(request.getSenderId())).findFirst();
        if (clientConfig.isEmpty())
            throw new LedgerException(ErrorMessage.NoSuchClient);

        // Verify client action was signed by him
        if (RSAEncryption.verifySignature(request.getMessage(), request.getClientSignature(),
                clientConfig.get().getPublicKeyPath()))
            return true;

        LOGGER.log(Level.INFO, MessageFormat.format(
                "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                        + "@          WARNING: INVALID CLIENT SIGNATURE!        @\n"
                        + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                        + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                request.getSenderId()));
        return false;
    }

    public Optional<LedgerResponse> createAccount(LedgerRequest request) {
        if (!verifyClientSignature(request))
            return Optional.empty();
        
    }

    public Optional<LedgerResponse> transfer(LedgerRequest request) {
        if (!verifyClientSignature(request))
            return Optional.empty();
        
    }

    private void checkBlockSize() {
        if (unconfirmedTransactions.size() >= blockSize) {
            // Create block
            Block block = new Block();
            for (Entry<String, LedgerRequest> item : unconfirmedTransactions.entrySet()) {
                if (item.getKey() != null) {
                    block.add(unconfirmedTransactions.remove(item.getKey()));
                }
            }
            // Start consensus to add block to blockchain
            service.startConsensus(block);
        }
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

                            Optional<LedgerResponse> response = Optional.empty();
                            switch (message.getType()) {
                                case CREATE -> {
                                    createAccount((LedgerRequest) message);
                                }
                                case TRANSFER -> {
                                    transfer((LedgerRequest) message);
                                }
                                case BALANCE -> {

                                }
                                case ACK -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received ACK message from {1}",
                                                    nodeId, message.getSenderId()));
                                }
                                case IGNORE -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    nodeId, message.getSenderId()));
                                }
                                default -> {
                                    throw new LedgerException(ErrorMessage.CannotParseMessage);
                                }
                            }

                            if (response.isEmpty())
                                return;

                            // Reply to a specific client
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
