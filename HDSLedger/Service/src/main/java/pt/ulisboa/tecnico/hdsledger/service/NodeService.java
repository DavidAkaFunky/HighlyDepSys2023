package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Timer;
// import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Level;
import java.util.logging.Logger;

public class NodeService implements UDPService {

    private static final Logger LOGGER = Logger.getLogger(NodeService.class.getName());

    // Store strings
    private final Queue<String> blockchain = new ConcurrentLinkedQueue<>();

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

    public void addBlock(String block) {
        blockchain.add(block);
    }

    public Queue<String> getBlockchain() {
        return blockchain;
    }

    // BIG TODO: What needs to be synchronized?

    /*
     * 
     */
    public void startConsensus(String inputValue) {

        // Set initial consensus values

        this.consensusInstance = consensusInstance++;
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
        }

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

        prepareMessages.addMessage(consensusInstance, round, value);

        // Find value with valid quorum
        Optional<String> preparedValue = prepareMessages.hasValidQuorum(consensusInstance, round);
        if (preparedValue.isPresent()) {

            // Set instance values
            this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
            InstanceInfo instance = this.instanceInfo.get(consensusInstance);
            instance.setPreparedRound(round);
            instance.setPreparedValue(preparedValue.get());

            // Prepare message to broadcast
            List<String> messageArgs = new ArrayList<>();
            messageArgs.add(String.valueOf(this.consensusInstance));
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

        commitMessages.addMessage(consensusInstance, round, value);

        Optional<String> preparedValue = commitMessages.hasValidQuorum(consensusInstance, round);
        if (preparedValue.isPresent()) {
            // this.timer.cancel(); // Not needed for now
            // this.consensusInstance and this.preparedValue will always be the same as the
            // ones in the message
            // Decide(this.consensusInstance, this.preparedValue, Quorum (why?) )

            // Add block to blockchain
            while (blockchain.size() < consensusInstance) {
                this.addBlock(preparedValue.get());
            }

        }
    }

    private boolean justifyPrePrepare(int consensusInstance, int round, String value) {
        // TODO: There is no round change, so this is a primitive version of the jusitification
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
                        String id = message.getSenderId();
                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {

                                case PRE_PREPARE -> {
                                    LOGGER.log(Level.INFO, "{0} - Received PRE-PREPARE message from {1}",
                                            new Object[] { id, message.getSenderId() });
                                    uponPrePrepare(message);
                                }

                                case PREPARE -> {
                                    LOGGER.log(Level.INFO, "{0} - Received PREPARE message from {1}",
                                            new Object[] { id, message.getSenderId() });
                                    uponPrepare(message);
                                }

                                case COMMIT -> {
                                    LOGGER.log(Level.INFO, "{0} - Received COMMIT message from {1}",
                                            new Object[] { id, message.getSenderId() });
                                    uponCommit(message);
                                }

                                case ROUND_CHANGE -> {
                                    LOGGER.log(Level.INFO, "{0} - Received ROUND-CHANGE message from {1}",
                                            new Object[] { id, message.getSenderId() });
                                    // stage 2
                                }

                                case ACK -> {
                                    LOGGER.log(Level.INFO, "{0} - Received ACK message from {1}",
                                            new Object[] { id, message.getSenderId() });
                                    // ignore
                                }

                                case IGNORE -> {
                                    LOGGER.log(Level.INFO, "{0} - Received IGNORE message from {1}",
                                            new Object[] { id, message.getSenderId() });
                                    // ignore
                                }

                                default -> {
                                    LOGGER.log(Level.INFO, "{0} - Received unknown message from {1}",
                                            new Object[] { id, message.getSenderId() });
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
