package pt.ulisboa.tecnico.hdsledger.utilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Map.Entry;

public class ConfigParser {

    private int nodeId;
    private String path;

    public ConfigParser(int nodeId) {
        this.nodeId = nodeId;
        this.path = "src/main/resources/config.txt";
    }
    
    public HashMap<Integer, Entry<InetAddress, Integer>> parse() throws FileNotFoundException, UnknownHostException {
        HashMap<Integer, Entry<InetAddress, Integer>> nodes
            = new HashMap<Integer, Entry<InetAddress, Integer>>();

        Scanner scanner = new Scanner(new File(path));

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] splitLine = line.split(" ");
            int id = Integer.parseInt(splitLine[0]);
            if (id == nodeId) {
                continue;
            }
            String hostname = splitLine[2];
            int port = Integer.parseInt(splitLine[3]);
            nodes.put(id, Map.entry(InetAddress.getByName(hostname), port));
        }

        scanner.close();

        return nodes;
    }
}
