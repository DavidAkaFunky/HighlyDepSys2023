package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.NodeMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
// import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Level;
import java.util.stream.Collectors;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    // Store strings
    private final Map<Integer, String> blockchain = new ConcurrentHashMap<>();

    private boolean isLeader;
    // TODO: Devia ser o PerfectLink a meter o counter na message ?
    // Probably um construtor para a mensagem sem o counter e depois é feito set
    private int messageCount = 0;

    private String nodeId;
    private int consensusInstance = 0;
    private Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();

    private String leaderId;

    private PerfectLink link;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;

    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;

    // Not needed yet
    // private Timer timer = new Timer();
    // private static final int TIMER_PERIOD = 10000;

    public NodeService(String nodeId, boolean isLeader, PerfectLink link, String leaderId, int nodesLength) {
        this.isLeader = isLeader;
        this.nodeId = nodeId;
        this.link = link;
        this.leaderId = leaderId;
        this.prepareMessages = new MessageBucket(nodesLength);
        this.commitMessages = new MessageBucket(nodesLength);
    }

    public void addBlock(int instance, String block) {
        blockchain.put(instance, block);
    }

    public Map<Integer, String> getBlockchain() {
        return blockchain;
    }

    public List<String> getBlockchainAsList() {
        return new ArrayList<>(blockchain.values());
    }

    public void printBlockchain() {
        LOGGER.log(Level.INFO, MessageFormat.format("Blockchain from node {0}: {1}", nodeId, getBlockchain().values()));
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public List<String> getBlockchainStartingAtInstance(int startInstance) {

        return getBlockchain()
                .entrySet()
                .stream()
                .filter((Map.Entry<Integer, String> entry) -> (entry.getKey() > startInstance))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    // BIG TODO: What needs to be synchronized?

    /*
     * Start an instance of consensus for value inputValue
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     * 
     * @param inputValue Value to be agreed upon
     */
    public int startConsensus(String inputValue) {

        // Set initial consensus values
        this.consensusInstance++;
        this.instanceInfo.put(this.consensusInstance, new InstanceInfo(inputValue));

        // Leader broadcasts PRE-PREPARE message
        if (isLeader) {

            InstanceInfo instance = this.instanceInfo.get(this.consensusInstance);
            List<String> messageArgs = new ArrayList<>();
            messageArgs.add(String.valueOf(this.consensusInstance));
            messageArgs.add(String.valueOf(instance.getCurrentRound()));
            messageArgs.add(instance.getInputValue());

            NodeMessage prePrepareMessage = new NodeMessage(nodeId, this.messageCount++, NodeMessage.Type.PRE_PREPARE, messageArgs);

            LOGGER.log(Level.INFO, MessageFormat.format(
                    "{0} - Node is leader, sending PRE-PREPARE messages", nodeId));

            this.link.broadcast(prePrepareMessage);
        } else {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "{0} - Node is not leader, waiting for PRE-PREPARE message", nodeId));
        }

        return this.consensusInstance;

        // Start timer (not needed for now)
        /*
         * timer.schedule(
         * new TimerTask() {
         * 
         * @Override
         * public void run() {
         * System.out.println("Timer ran in startConsensus, trigger round change");
         * }
         * },
         * 0,
         * TIMER_PERIOD);
         */
    }

    /*
     * Handle pre prepare messages and if the message
     * came from leader and is justified them broadcast prepare
     * 
     * @param message Message to be handled
     */
    public void uponPrePrepare(NodeMessage message) {

        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));
        String value = message.getArgs().get(2);
        String senderId = message.getSenderId();

        if (!messageFromLeader(senderId)) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}, Value {4} but node is not leader, ignoring",
                            nodeId, message.getSenderId(), consensusInstance, round, value));
            return;
        }

        if (!justifyPrePrepare(consensusInstance, round, value)) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}, Value {4} but not justified, ignoring",
                            nodeId, message.getSenderId(), consensusInstance, round, value));
            return;
        }

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}, Value {4}",
                        nodeId, message.getSenderId(), consensusInstance, round, value));

        NodeMessage prepareMessage = new NodeMessage(nodeId, this.messageCount++, NodeMessage.Type.PREPARE, message.getArgs());
        this.link.broadcast(prepareMessage);

        // Cancel previous timer and start new one
        /*
         * timer.schedule(
         * new TimerTask() {
         * 
         * @Override
         * public void run() {
         * System.out.println("Timer ran in uponPrePrepare, trigger round change");
         * }
         * },
         * 0,
         * TIMER_PERIOD);
         */
    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     * 
     * @param message Message to be handled
     */
    public void uponPrepare(NodeMessage message) {

        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));
        String value = message.getArgs().get(2);

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}, Value {4}",
                        nodeId, message.getSenderId(), consensusInstance, round, value));

        prepareMessages.addMessage(consensusInstance, round, value);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Find value with valid quorum
        Optional<String> preparedValue = prepareMessages.hasValidQuorum(consensusInstance, round);
        if (preparedValue.isPresent() && (instance == null || instance.getPreparedRound() < round)) {

            // Set instance values
            this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
            instance = this.instanceInfo.get(consensusInstance);
            instance.setPreparedRound(round);
            instance.setPreparedValue(preparedValue.get());

            // Prepare message to broadcast
            List<String> messageArgs = new ArrayList<>();
            messageArgs.add(String.valueOf(consensusInstance));
            messageArgs.add(String.valueOf(instance.getCurrentRound()));
            messageArgs.add(instance.getPreparedValue());

            NodeMessage commitMessage = new NodeMessage(nodeId, this.messageCount++, NodeMessage.Type.COMMIT, messageArgs);

            this.link.broadcast(commitMessage);
        }
    }

    /*
     * Handle commit messages and if there is a valid quorum decide
     * 
     * @param message Message to be handled
     */
    public void uponCommit(NodeMessage message) {

        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));
        String value = message.getArgs().get(2);

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}, Value {4}",
                        nodeId, message.getSenderId(), consensusInstance, round, value));

        commitMessages.addMessage(consensusInstance, round, value);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        Optional<String> committedValue = commitMessages.hasValidQuorum(consensusInstance, round);
        if (committedValue.isPresent() && (instance == null || instance.getCommittedRound() < round)) {

            // this.timer.cancel(); // Not needed for now

            // Add block to blockchain
            while (blockchain.size() < consensusInstance - 1) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Should never be absent
            this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            String block = committedValue.get();
            this.addBlock(consensusInstance, block);

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Value {3}",
                            nodeId, consensusInstance, round, value));
        }
    }

    void uponRoundChange(NodeMessage message) {
        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received ROUND-CHANGE message from {1}: Consensus Instance {2}, New Round {3}",
                        nodeId, message.getSenderId(), consensusInstance, round));

        // NOT IMPLEMENTED
    }

    private boolean messageFromLeader(String nodeId) {
        return this.leaderId.equals(nodeId);
    }

    private boolean justifyPrePrepare(int consensusInstance, int round, String value) {
        // Round change is not implemented, therefore pre prepare should always be from
        // round 1
        return round == 1;
    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            // This is not thread safe but it's okay because
            // a client only sends one request at a time
            // thread listening for client requests on clientPort {Append, Read}
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();
                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {

                                case PRE_PREPARE -> {
                                    uponPrePrepare((NodeMessage) message);
                                }

                                case PREPARE -> {
                                    uponPrepare((NodeMessage) message);
                                }

                                case COMMIT -> {
                                    uponCommit((NodeMessage) message);
                                }

                                case ROUND_CHANGE -> {
                                    uponRoundChange((NodeMessage) message);
                                }

                                case ACK -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received ACK message from {1}",
                                                    nodeId, message.getSenderId()));
                                    // ignore
                                }

                                case IGNORE -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    nodeId, message.getSenderId()));
                                    // ignore
                                }

                                default -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    nodeId, message.getSenderId()));
                                    // ignore
                                }
                            }
                        }).start();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
