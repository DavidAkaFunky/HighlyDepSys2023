package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
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

    private Timer timer = new Timer();

    private PerfectLink link;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;

    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;

    private static final int TIMER_PERIOD = 10000;

    public NodeService(String nodeId, boolean isLeader, PerfectLink link, int nodeCount) {
        this.isLeader = isLeader;
        this.nodeId = nodeId;
        this.link = link;
        this.prepareMessages = new MessageBucket(nodeCount);
        this.commitMessages = new MessageBucket(nodeCount);
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
                .filter((Map.Entry<Integer, String> entry) -> (entry.getKey() >= startInstance))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    // BIG TODO: What needs to be synchronized?

    /*
     * 
     */
    public int startConsensus(String inputValue) {

        //NOTE: Client is only sending  request to leader
        // Meaning that other nodes will not set consensusInstance
        

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

            Message prePrepareMessage = new Message(nodeId, this.messageCount++, Message.Type.PRE_PREPARE, messageArgs);

            this.link.broadcast(prePrepareMessage);
        } else {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "Node {0} is not leader, waiting for PRE-PREPARE message - THIS SHOULD NOT HAPPEN!", nodeId));
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
     * 
     */
    public void uponPrePrepare(Message message) {

        // TODO: PrePrepare came from leader AND JustifyPrePrepare(m)

        // Start timer
        // TODO: Cancel previous timer ?
        /*
         * timer.schedule(
         * new TimerTask() {
         * 
         * @Override
         * public void run() {
         * System.out.println("Timer ran in uponPrepare, trigger round change");
         * }
         * },
         * 0,
         * TIMER_PERIOD);
         */
        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));
        String value = message.getArgs().get(2);

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1}: Consensus Instance {2}, Round {3}, Value {4}",
                        nodeId, message.getSenderId(), consensusInstance, round, value));

        if (justifyPrePrepare(consensusInstance, round, value)) {
            Message prepareMessage = new Message(nodeId, this.messageCount++, Message.Type.PREPARE, message.getArgs());
            this.link.broadcast(prepareMessage);
        }
    }

    /*
     * 
     */
    public void uponPrepare(Message message) {

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

            Message commitMessage = new Message(nodeId, this.messageCount++, Message.Type.COMMIT, messageArgs);

            this.link.broadcast(commitMessage);
        }
    }

    /*
     * 
     */
    public void uponCommit(Message message) {

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
            // this.consensusInstance and this.preparedValue will always be the same as the
            // ones in the message
            // Decide(this.consensusInstance, this.preparedValue, Quorum (why?) )

            // Add block to blockchain
            while (blockchain.size() < consensusInstance - 1) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Very weird if it's absent
            this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            System.out.println("Going to add to blockchain");
            String block = committedValue.get();
            System.out.println("Block: " + block);
            this.addBlock(consensusInstance, block);
        }
    }

    void uponRoundChange(Message message) {
        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received ROUND-CHANGE message from {1}: Consensus Instance {2}, New Round {3}",
                        nodeId, message.getSenderId(), consensusInstance, round));
        // stage 2
    }

    private boolean justifyPrePrepare(int consensusInstance, int round, String value) {
        // TODO: There is no round change, so this is a primitive version of the
        // jusitification
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
                                    uponPrePrepare(message);
                                }

                                case PREPARE -> {
                                    uponPrepare(message);
                                }

                                case COMMIT -> {
                                    uponCommit(message);
                                }

                                case ROUND_CHANGE -> {
                                    uponRoundChange(message);
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
