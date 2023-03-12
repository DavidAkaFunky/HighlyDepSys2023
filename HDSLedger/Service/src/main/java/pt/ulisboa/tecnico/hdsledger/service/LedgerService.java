package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;

import java.io.IOException;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class LedgerService implements UDPService {

    private final NodeConfig config;
    private final NodeService service;

    // processos simples para identificar cada pedido: cliente calcula hash de
    // mensagem + timestamp + ip do cliente
    private final Set<String> nonces = ConcurrentHashMap.newKeySet();
    private final Logger logger = Logger.getLogger(LedgerService.class.getName());
    private Thread thread;

    public LedgerService(NodeConfig self, NodeService service) {
        this.config = self;
        this.service = service;
    }

    public void handleAppendRequest(String arg) {
        // TODO: REPLACE arg WITH NONCE AND CHECK IF IT IS VALID
        boolean isNew = nonces.add(arg);

        if (isNew) {
            service.startConsensus(arg);
        }

    }

    public void handleReadRequest() {

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

                // Packet to receive client requests
                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);

                logger.log(Level.INFO, "Started LedgerService on {0}:{1}",
                        new Object[] { address, port });

                for (;;) {

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
                                LedgerMessage message = new Gson().fromJson(new String(buffer), LedgerMessage.class);

                                // Handle client request
                                switch (message.getType()) {
                                    case APPEND -> {
                                        // TODO falta sacar resposta
                                        handleAppendRequest(message.getArg());
                                        break;
                                    }
                                    case READ -> {
                                        handleReadRequest();
                                        break;
                                    }
                                    default -> {
                                        throw new LedgerException(ErrorMessage.CannotParseMessage);
                                    }
                                }
                                // TODO buffer is not the response, it's the request
                                DatagramPacket response = new DatagramPacket(buffer, buffer.length, clientAddress,
                                        clientPort);

                                // Reply to client
                                socket.send(response);

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
