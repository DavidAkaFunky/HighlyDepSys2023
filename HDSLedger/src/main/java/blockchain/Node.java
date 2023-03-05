package blockchain;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

public class Node {

    public static void main(String[] args) {

        // 1 L localhost 3000
        int id = Integer.parseInt(args[0]);
        boolean isLeader = args[1].equals("L");
        String hostname = args[2];
        int port = Integer.parseInt(args[3]);
        HashMap<Integer, Entry<InetAddress, Integer>> nodes
            = new HashMap<Integer, Entry<InetAddress, Integer>>();
        
        // TODO: Parse config and add nodes
        
        try {
            PerfectLink link = new PerfectLink(hostname, port, id);

            while (true) {
                try {
                    Data data = link.receive();
                    System.out.println(data);
                } catch (ClassNotFoundException | IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        System.out.println("Hello World!");
        System.out.println(Arrays.stream(args).toList());

    }
}
