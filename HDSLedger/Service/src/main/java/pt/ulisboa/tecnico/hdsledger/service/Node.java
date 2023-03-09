package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.utilities.ConfigParser;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Node {

    private static final Logger LOGGER = Logger.getLogger(Node.class.getName());

    public static void main(String[] args) {

        try {
            // Command line arguments (e.g. 1 L localhost 3000)
            int id = Integer.parseInt(args[0]);
            boolean isLeader = args[1].equals("L");
            String hostname = args[2];
            int port = Integer.parseInt(args[3]);

            LOGGER.log(Level.INFO, "{0} - Node {0} at {1}:{2} is leader: {3}",
                    new Object[] { id, hostname, port, isLeader });

            // Parse config file to know where all nodes are
            ConfigParser parser = new ConfigParser();
            HashMap<Integer, Entry<InetAddress, Integer>> nodes = parser.parse();

            // Abstraction to send and receive messages
            PerfectLink link = new PerfectLink(hostname, port, id, nodes);

            // Service implementation
            NodeService service = new NodeService(id, isLeader, link, nodes.size());

            while (true) {
                try {
                    Message message = link.receive();

                    // Separate thread to handle each message
                    new Thread(() -> {
                        switch (message.getType()) {
                            case START -> {
                                LOGGER.log(Level.INFO, "{0} - Received START message from {1}",
                                        new Object[] { id, message.getSenderId() });
                                service.startConsensus(message);
                            }
                            case PRE_PREPARE -> {
                                LOGGER.log(Level.INFO, "{0} - Received PRE-PREPARE message from {1}",
                                        new Object[] { id, message.getSenderId() });
                                service.uponPrePrepare(message);
                            }

                            case PREPARE -> {
                                LOGGER.log(Level.INFO, "{0} - Received PREPARE message from {1}",
                                        new Object[] { id, message.getSenderId() });
                                service.uponPrepare(message);
                            }

                            case COMMIT -> {
                                LOGGER.log(Level.INFO, "{0} - Received COMMIT message from {1}",
                                        new Object[] { id, message.getSenderId() });
                                service.uponCommit(message);
                            }

                            case ROUND_CHANGE -> {
                                LOGGER.log(Level.INFO, "{0} - Received ROUND-CHANGE message from {1}",
                                        new Object[] { id, message.getSenderId() });
                                // stage 2
                            }

                            case ACK -> {
                                LOGGER.log(Level.INFO, "{0} - Received ACK message from {1}",
                                        new Object[] { id, message.getSenderId() });
                                // ....
                            }

                            case DUPLICATE -> {
                                LOGGER.log(Level.INFO, "{0} - Received DUPLICATE message from {1}",
                                        new Object[] { id, message.getSenderId() });
                                // ignore
                            }

                            default -> {
                                LOGGER.log(Level.INFO, "{0} - Received unknown message from {1}",
                                        new Object[] { id, message.getSenderId() });
                                // ignore
                            }
                        }
                    }).start();

                } catch (ClassNotFoundException | IOException e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
