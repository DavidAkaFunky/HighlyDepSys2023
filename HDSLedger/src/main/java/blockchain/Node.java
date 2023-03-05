package blockchain;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

import utils.ConfigParser;

public class Node {

    public static void main(String[] args) {

        try {
            // 1 L localhost 3000
            int id = Integer.parseInt(args[0]);
            boolean isLeader = args[1].equals("L");
            String hostname = args[2];
            int port = Integer.parseInt(args[3]);

            ConfigParser parser = new ConfigParser(id, "config.txt");
            HashMap<Integer, Entry<InetAddress, Integer>> nodes = parser.parse();
            PerfectLink link = new PerfectLink(hostname, port, id, nodes);

            System.out.println("Node " + id + " is leader: " + isLeader);
            if (isLeader) {
                link.broadcast(new Data(id, 0, "Captain Funky", 2));
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

        System.out.println("Hello World!");
        System.out.println(Arrays.stream(args).toList());

    }
}
