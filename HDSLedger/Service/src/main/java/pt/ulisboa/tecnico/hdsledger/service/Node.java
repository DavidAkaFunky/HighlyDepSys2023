package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestTransfer;
import pt.ulisboa.tecnico.hdsledger.communication.NodeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.service.services.LedgerService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    private static final String clientsConfigPath = "../Client/src/main/resources/client_config.json";
    private static String nodesConfigPath = "src/main/resources/";

    public static void main(String[] args) {

        try {
            // Single command line argument (id)
            String id = args[0];
            nodesConfigPath += args[1];

            ProcessConfig[] otherNodes = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            String leaderId = Arrays.stream(otherNodes).filter(ProcessConfig::isLeader).findAny().get().getId();

            Optional<ProcessConfig> node = Arrays.stream(otherNodes).filter(nodeConfig -> nodeConfig.getId().equals(id))
                    .findAny();

            if (node.isEmpty())
                throw new LedgerException(ErrorMessage.ConfigFileFormat);

            ProcessConfig nodeConfig = node.get();
            // BYZANTINE_TESTS
            // BAD_CONSENSUS: Forget the actual leader and pretend to be the actual leader
            if (nodeConfig.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.BAD_CONSENSUS) {
                Arrays.stream(otherNodes).filter(ProcessConfig::isLeader).forEach(n -> n.setLeader(false));
                nodeConfig.setLeader(true);
            }
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}; behaviour: {3}; is leader: {4}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(),
                    nodeConfig.getByzantineBehavior(), nodeConfig.isLeader()));

            ProcessConfig[] clients = new ProcessConfigBuilder().fromFile(clientsConfigPath);

            // Abstraction to send and receive messages
            PerfectLink linkToNodes = new PerfectLink(nodeConfig, nodeConfig.getPort(), otherNodes, NodeMessage.class);
            PerfectLink linkToClients = new PerfectLink(nodeConfig, nodeConfig.getClientPort(), clients,
                    LedgerRequestTransfer.class);

            // Services that implement listen from UDPService
            NodeService nodeService = new NodeService(clients, nodeConfig, linkToNodes, leaderId, otherNodes.length);
            LedgerService ledgerService = new LedgerService(clients, id, nodeService, linkToClients);

            nodeService.listen();
            ledgerService.listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
