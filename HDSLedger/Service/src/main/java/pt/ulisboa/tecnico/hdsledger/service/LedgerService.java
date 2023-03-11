package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;

import java.io.IOException;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LedgerService implements UDPService {

    private final NodeConfig config;

    private final Logger logger = Logger.getLogger(LedgerService.class.getName());
    private Thread thread;

    public LedgerService(NodeConfig self) {
        this.config = self;
    }

    public void handleAppendRequest(Message message) {

    }

    public void handleReadRequest(Message message) {

    }

    public Thread getThread() {
        return thread;
    }

    public void killThread() {
        thread.interrupt();
    }

    @Override
    public void listen() {
        try (DatagramSocket socket = new DatagramSocket(config.getClientPort(), InetAddress.getByName(config.getHostname()))) {
            thread = new Thread(() -> {
                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                try {
                    logger.log(Level.INFO, "Started LedgerService on {0}:{1}",
                            new Object[]{socket.getInetAddress(), socket.getPort()});
                    socket.receive(packet);
                    // spawn a thread
                    // middleware, something like Perfect Links ...
                    // handle the message itself
                } catch (IOException e) {
                    throw new LedgerException(ErrorMessage.SocketReceivingError);
                }
            });
            thread.start();
        } catch (SocketException | UnknownHostException e) {
            throw new LedgerException(ErrorMessage.CannotOpenSocket);
        }
    }

}
