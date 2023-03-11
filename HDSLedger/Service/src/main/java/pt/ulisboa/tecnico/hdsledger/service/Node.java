package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.Message;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Node {

    private static final Logger LOGGER = Logger.getLogger(Node.class.getName());
    private static final String CONFIG = "src/main/resources/config.txt";

    public static void main(String[] args) {

        try {
            // Single command line argument (id)
            String id = args[0];

            // TODO: Distinguish by server/client
            NodeConfig[] nodes = new NodeConfigBuilder().fromFile(CONFIG);
            // TODO: change this
            Optional<NodeConfig> config = Arrays.stream(nodes).filter(nodeConfig -> nodeConfig.getId().equals(id)).findAny();

            if (config.isEmpty()) throw new LedgerException(ErrorMessage.ConfigFileFormat);

            var nodeConfig = config.get();
            LOGGER.log(Level.INFO, "{0} - Node {0} at {1}:{2} is leader: {3}",
                    new Object[]{nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(), nodeConfig.isLeader()});

            // Abstraction to send and receive messages
            PerfectLink link = new PerfectLink(nodeConfig, nodes);

            // Service implementation
            NodeService nodeService = new NodeService(id, nodeConfig.isLeader(), link, nodes.length);
            nodeService.listen();
            new LedgerService(nodeConfig, nodeService).listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void listenToClients(PerfectLink link){

    }

    public static void listenToNodes(PerfectLink link){

    }
}
