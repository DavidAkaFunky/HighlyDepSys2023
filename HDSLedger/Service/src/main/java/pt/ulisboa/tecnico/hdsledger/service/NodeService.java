package pt.ulisboa.tecnico.hdsledger.service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class NodeService {

    private List<Block> blockchain;

    // hmm....
    private boolean isLeader;
    // TODO: Devia ser o PerfectLink a meter o counter na message ?
    // Probably um construtor para a mensagem sem o counter e depois é feito set
    private int messageCount;

    private int nodeId;
    private int consensusInstance;
    private int currentRound;
    private int preparedRound;
    private String preparedValue;
    private String inputValue;

    private Timer timer;

    private PerfectLink link;

    private static final int TIMER_PERIOD = 10000;

    public NodeService(int nodeId, boolean isLeader, PerfectLink link) {
        this.blockchain = new LinkedList<>();

        this.isLeader = isLeader;
        this.messageCount = 0;

        this.nodeId = nodeId;
        this.consensusInstance = 0;
        this.currentRound = 0;
        this.preparedRound = 0;
        this.preparedValue = null;
        this.inputValue = null;

        timer = new Timer();

        this.link = link;
    }

    public void addBlock(Block block) {
        blockchain.add(block);
    }

    public List<Block> getBlockchain() {
        return blockchain;
    }

    // BIG TODO: What needs to be Syncronized ?
    // TODO: store messages

    /*
     * 
     */
    public void startConsensus(Message message) {

        // Set initial consensus values
        this.consensusInstance = Integer.parseInt(message.getArgs().get(0));
        this.currentRound = 1;
        this.preparedRound = 0;
        this.preparedValue = null;
        this.inputValue = message.getArgs().get(1);

        // Leader broadcasts PRE-PREPARE message
        if (isLeader) {
            List<String> messageArgs = new ArrayList<>();
            messageArgs.add(String.valueOf(this.consensusInstance));
            messageArgs.add(String.valueOf(this.currentRound));
            messageArgs.add(this.inputValue);

            Message prePrepareMessage = new Message(nodeId, this.messageCount++, Message.Type.PRE_PREPARE, messageArgs);

            this.link.broadcast(prePrepareMessage);
        }

        // Start timer
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        System.out.println("Timer ran in startConsensus, trigger round change");
                    }
                },
                0,
                TIMER_PERIOD);
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

        // TODO: if received a quorum of PREPARE messages for this cons inst and round

        this.preparedRound = Integer.parseInt(message.getArgs().get(0));
        this.preparedValue = message.getArgs().get(1);

        List<String> messageArgs = new ArrayList<>();
        messageArgs.add(String.valueOf(this.consensusInstance));
        messageArgs.add(String.valueOf(this.currentRound));
        messageArgs.add(this.preparedValue);

        Message prePrepareMessage = new Message(nodeId, this.messageCount++, Message.Type.COMMIT, messageArgs);

        this.link.broadcast(prePrepareMessage);
    }

    /*
     * 
     */
    public void uponCommit(Message message) {

        // TODO: if received a quorum of COMMIT messages for this cons inst and round

        this.timer.cancel();

        // Decide(this.consensusInstance, this.preparedValue, Quorum (why?) )
    }
}
