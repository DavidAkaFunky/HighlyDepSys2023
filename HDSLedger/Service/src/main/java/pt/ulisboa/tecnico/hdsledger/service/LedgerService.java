package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;

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

    private final NodeConfig config;
    private final NodeService service;

    private final Map<String, Set<Integer>> clientRequests = new ConcurrentHashMap<>();
    private static final CustomLogger LOGGER = new CustomLogger(LedgerService.class.getName());
    private Thread thread;

    public LedgerService(NodeConfig self, NodeService service) {
        this.config = self;
        this.service = service;
    }

    public Optional<LedgerResponse> handleAppendRequest(String clientId, int clientSeq,
            String value) {
        return requestConsensus(clientId, clientSeq, value);
    }

    public Optional<LedgerResponse> handleReadRequest(String clientId, int clientSeq) {
        return requestConsensus(clientId, clientSeq, "");
    }

    public Optional<LedgerResponse> requestConsensus(String clientId, int clientSeq, String value) {

        // Check if client has already sent this request
        clientRequests.putIfAbsent(clientId, ConcurrentHashMap.newKeySet());
        System.out.println("VALUE ALREADY EXISTS: " + clientRequests.get(clientId).contains(clientSeq));
        boolean isNewMessage = clientRequests.get(clientId).add(clientSeq);

        LOGGER.log(Level.INFO, "Request for consensus");

        if (isNewMessage) {

            System.out.println("client sequence:" + clientSeq);
            LOGGER.log(Level.INFO, "Starting consensus");

            // Start consensus instance
            int consensusInstance = service.startConsensus(value);
            // service.requestNewBlocks(latestInstanceKnownByClient);
            for (;;) {
                // Wait for consensus to finish
                Map<Integer, String> blockchain = service.getBlockchain();
                if (blockchain.size() >= consensusInstance)
                    break;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Consensus finished");
            System.out.println(service.getBlockchain().values());
            System.out.println(service.getBlockchainAsList());
            System.out.println(service.getBlockchainStartingAtInstance(consensusInstance));

            if (value.equals(""))
                return Optional.of(new LedgerResponse(consensusInstance, service.getBlockchainAsList()));
            else
                return Optional.of(new LedgerResponse(consensusInstance,
                        service.getBlockchainStartingAtInstance(consensusInstance)));
        }

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
        // Thread to listen on every request
        // This is not thread safe but it's okay because
        // a client only sends one request at a time
        // thread listening for client requests on clientPort {Append, Read}
        new Thread(() -> {

            try {

                // Create socket to listen for client requests
                int port = config.getClientPort();
                InetAddress address = InetAddress.getByName(config.getHostname());
                DatagramSocket socket = new DatagramSocket(port, address);

                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Started LedgerService on {1}:{2}",
                        config.getId(), address, port));

                for (;;) {

                    // Packet to receive client requests
                    // TODO: Can this be moved outside the loop?
                    DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);

                    // Receive client request
                    socket.receive(packet);

                    // Spawn thread to handle client request
                    // Runnable with parameters is used to avoid race conditions between receiving
                    // and reading data
                    // due to multiple packets being received at the same time
                    new Thread(new Runnable() {
                        private InetAddress clientAddress = packet.getAddress();
                        private int clientPort = packet.getPort();
                        private byte[] buffer = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());

                        @Override
                        public void run() {
                            try {
                                // Deserialize client request
                                LedgerRequest message = new Gson().fromJson(new String(buffer), LedgerRequest.class);

                                Optional<LedgerResponse> response;
                                // Handle client request
                                switch (message.getType()) {
                                    case APPEND -> {
                                        response = handleAppendRequest(message.getClientId(), message.getClientSeq(), message.getArg());
                                        break;
                                    }
                                    case READ -> {
                                        response = handleReadRequest(message.getClientId(), message.getClientSeq());
                                        break;
                                    }
                                    default -> {
                                        throw new LedgerException(ErrorMessage.CannotParseMessage);
                                    }
                                }

                                if (response.isEmpty()) {
                                    return;
                                }

                                LedgerResponse ledgerResponse = response.get();
                                System.out.println("Ledger response: " + ledgerResponse);
                                System.out.println(ledgerResponse.getConsensusInstance());
                                System.out.println(ledgerResponse.getValues());

                                byte[] jsonBytes = new Gson().toJson(response.get()).getBytes();
                                DatagramPacket packet = new DatagramPacket(jsonBytes, jsonBytes.length, clientAddress, clientPort);

                                // Reply to client
                                socket.send(packet);

                            } catch (IOException | JsonSyntaxException e) {
                                socket.close();
                                throw new LedgerException(ErrorMessage.CannotParseMessage);
                            }
                        }
                    }).start();
                }

            } catch (SocketException | UnknownHostException e) {
                throw new LedgerException(ErrorMessage.CannotOpenSocket);
            } catch (IOException e) {
                e.printStackTrace();
                // throw new LedgerException(ErrorMessage.SocketReceivingError);
            }
        }).start();
    }

}
