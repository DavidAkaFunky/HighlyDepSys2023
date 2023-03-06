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

            LOGGER.log(Level.INFO, "{0} - Node {0} at {1}:{2} is leader: {3}", new Object[] { id, hostname, port, isLeader });

            // Parse config file to know where all nodes are
            ConfigParser parser = new ConfigParser(id);
            HashMap<Integer, Entry<InetAddress, Integer>> nodes = parser.parse();

            // Abstraction to send and receive messages
            PerfectLink link = new PerfectLink(hostname, port, id, nodes);

            if (isLeader) {
                LOGGER.log(Level.INFO, "{0} - Broadcasting message", id);
                link.broadcast(new Data(id, 0, "Days without UDP losing messages: 5", 2));
            }

            while (true) {
                try {
                    Data data = link.receive();
                    System.out.println(data);
                } catch (ClassNotFoundException | IOException e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
