package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.service.services.Mempool;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.service.models.Ledger;
import pt.ulisboa.tecnico.hdsledger.service.services.LedgerService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    // Hardcoded path to files
    private static String nodesConfigPath = "src/main/resources/";
    private static final String clientsConfigPath = "../Client/src/main/resources/client_config.json";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            nodesConfigPath += args[1];
            int blockSize = Integer.parseInt(args[2]);

            // Create configuration instances
            ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);
            ProcessConfig leaderConfig = Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).findAny().get();
            ProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId().equals(id)).findAny().get();

            // BYZANTINE_TESTS
            if (nodeConfig.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.BAD_CONSENSUS) {
                Arrays.stream(nodeConfigs).filter(ProcessConfig::isLeader).forEach(n -> n.setLeader(false));
                nodeConfig.setLeader(true);
            }

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}; behaviour: {3}; is leader: {4}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(),
                    nodeConfig.getByzantineBehavior(), nodeConfig.isLeader()));

            // Abstraction to send and receive messages
            PerfectLink linkToNodes = new PerfectLink(nodeConfig, nodeConfig.getPort(), nodeConfigs,
                    ConsensusMessage.class);
            PerfectLink linkToClients = new PerfectLink(nodeConfig, nodeConfig.getClientPort(), clientConfigs,
                    LedgerRequest.class);

            // Shared entities
            Ledger ledger = new Ledger();
            Mempool mempool = new Mempool(blockSize);

            // Services that implement listen from UDPService
            NodeService nodeService = new NodeService(clientConfigs, linkToNodes, nodeConfig, leaderConfig,
                    nodeConfigs.length, ledger, mempool);
            LedgerService ledgerService = new LedgerService(clientConfigs, linkToClients, nodeConfig,
                    nodeService, blockSize, ledger, mempool);

            nodeService.listen();
            ledgerService.listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
