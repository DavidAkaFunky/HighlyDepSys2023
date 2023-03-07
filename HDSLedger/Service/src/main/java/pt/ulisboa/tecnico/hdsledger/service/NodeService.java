package pt.ulisboa.tecnico.hdsledger.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

public class NodeService {

    private final Queue<Block> blockchain = new ConcurrentLinkedQueue<>();

    // hmm....
    private boolean isLeader;
    // TODO: Devia ser o PerfectLink a meter o counter na message ?
    // Probably um construtor para a mensagem sem o counter e depois é feito set
    private int messageCount = 0;

    private int nodeId;
    private int consensusInstance = 0;
    private Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();

    private Timer timer = new Timer();

    private PerfectLink link;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;

    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;

    private static final int TIMER_PERIOD = 10000;

    public NodeService(int nodeId, boolean isLeader, PerfectLink link, int nodeCount) {
        this.isLeader = isLeader;
        this.nodeId = nodeId;
        this.link = link;
        this.prepareMessages = new MessageBucket(nodeCount);
        this.commitMessages = new MessageBucket(nodeCount);
    }

    public void addBlock(Block block) {
        blockchain.add(block);
    }

    public Queue<Block> getBlockchain() {
        return blockchain;
    }

    // BIG TODO: What needs to be synchronized?

    /*
     * 
     */
    public void startConsensus(Message message) {

        // Set initial consensus values
        int consensusInstance = Integer.parseInt(message.getArgs().get(0));

        if (this.instanceInfo.containsKey(consensusInstance)) {
            return; // Already started consensus for this instance
        }

        this.consensusInstance = consensusInstance;
        this.instanceInfo.put(this.consensusInstance, new InstanceInfo(message.getArgs().get(1)));

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
        /* timer.schedule(
            new TimerTask() {
                @Override
                public void run() {
                    System.out.println("Timer ran in startConsensus, trigger round change");
                }
            },
            0,
            TIMER_PERIOD); */
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

        Message prepareMessage = new Message(nodeId, this.messageCount++, Message.Type.PREPARE, message.getArgs());

        this.link.broadcast(prepareMessage);
    }

    /*
     * 
     */
    public void uponPrepare(Message message) {

        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));
        String value = message.getArgs().get(2);

        // TODO: Remove x -> true and add check for value
        if (prepareMessages.quorum(consensusInstance, round, (x -> true))) {
            InstanceInfo instance = this.instanceInfo.get(consensusInstance);
            instance.setPreparedRound(round);
            instance.setPreparedValue(value);

            List<String> messageArgs = new ArrayList<>();
            messageArgs.add(String.valueOf(this.consensusInstance));
            messageArgs.add(String.valueOf(instance.getCurrentRound()));
            messageArgs.add(instance.getPreparedValue());

            Message prePrepareMessage = new Message(nodeId, this.messageCount++, Message.Type.COMMIT, messageArgs);

            this.link.broadcast(prePrepareMessage);
        }

    }

    /*
     * 
     */
    public void uponCommit(Message message) {

        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));
        String value = message.getArgs().get(2);

        // TODO: Remove x -> true and add check for value
        if (commitMessages.quorum(consensusInstance, round, (x -> true))){
            // this.timer.cancel(); // Not needed for now
            // this.consensusInstance and this.preparedValue will always be the same as the ones in the message
            // Decide(this.consensusInstance, this.preparedValue, Quorum (why?) )
        }

    }

    private boolean justifyPrePrepare(Message message) {
        // Unnecessary check but just in case
        if (message.getType() != Message.Type.PRE_PREPARE)
            return false;

        List<String> messageArgs = message.getArgs();
        int consensusInstance = Integer.parseInt(messageArgs.get(0));
        int round = Integer.parseInt(messageArgs.get(0));
        String value = messageArgs.get(2);

        // TODO: There is no round change, so this is a primitive version of the check
        return round == 1;
    }

}
