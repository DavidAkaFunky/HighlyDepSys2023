package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    private static final String CONFIG = "src/main/resources/config.json";

    public static void main(String[] args) {
        
        try {
            // Single command line argument (id)
            String id = args[0];

            // TODO: Distinguish by server/client
            NodeConfig[] nodes = new NodeConfigBuilder().fromFile(CONFIG);
            // TODO: change this
            Optional<NodeConfig> config = Arrays.stream(nodes).filter(nodeConfig -> nodeConfig.getId().equals(id))
                    .findAny();

            if (config.isEmpty())
                throw new LedgerException(ErrorMessage.ConfigFileFormat);

            NodeConfig nodeConfig = config.get();
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}; is leader: {3}", nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(),
                            nodeConfig.isLeader()));

            // Abstraction to send and receive messages
            PerfectLink link = new PerfectLink(nodeConfig, nodes);

            // Services that implement listen from UDPService
            NodeService nodeService = new NodeService(id, nodeConfig.isLeader(), link, nodes.length);
            LedgerService ledgerService = new LedgerService(nodeConfig, nodeService);

            nodeService.listen();
            ledgerService.listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
