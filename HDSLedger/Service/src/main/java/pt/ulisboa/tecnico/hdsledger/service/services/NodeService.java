package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.models.NodeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestTransfer;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.logging.Level;
import java.util.stream.Collectors;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    private ProcessConfig[] clientsConfig;

    // Store blocks
    private final Map<Integer, Block> blockchain = new ConcurrentHashMap<>();

    // Current node is leader
    private ProcessConfig config;

    // Consensus info
    private AtomicInteger consensusInstance = new AtomicInteger(0);
    private Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    private Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();

    private String leaderId;

    private PerfectLink link;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;

    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;

    public NodeService(ProcessConfig[] clientsConfig, ProcessConfig config, PerfectLink link, String leaderId,
            int nodesLength) {
        this.clientsConfig = clientsConfig;
        this.config = config;
        this.link = link;
        this.leaderId = leaderId;
        this.prepareMessages = new MessageBucket(nodesLength);
        this.commitMessages = new MessageBucket(nodesLength);
    }

    public void addBlock(int instance, Block block) {
        blockchain.put(instance, block);
    }


    public int getConsensusInstance() {
        return consensusInstance.get();
    }

    /*
     * Start an instance of consensus for a block of transactions
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     * 
     * @param inputValue Value to be agreed upon
     */
    public int startConsensus(Block block) {

        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(block));

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "{0} - Node already started consensus for instance {1}", config.getId(), localConsensusInstance));
            return localConsensusInstance;
        }

        // Leader broadcasts PRE-PREPARE message
        if (this.config.isLeader()) {

            InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);

            NodeMessage prePrepareMessage = new NodeMessage(config.getId(),
                    pt.ulisboa.tecnico.hdsledger.service.models.NodeMessage.Type.PRE_PREPARE);
            prePrepareMessage.setConsensusInstance(localConsensusInstance);
            prePrepareMessage.setRound(instance.getCurrentRound());
            prePrepareMessage.setBlock(block);

            LOGGER.log(Level.INFO, MessageFormat.format(
                    "{0} - Node is leader, sending PRE-PREPARE messages", config.getId()));

            this.link.broadcast(prePrepareMessage);
        } else {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
        }

        return localConsensusInstance;
    }

    /*
     * Handle pre prepare messages and if the message
     * came from leader and is justified them broadcast prepare
     * 
     * @param message Message to be handled
     */
    public Optional<NodeMessage> uponPrePrepare(NodeMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        Block block = message.getBlock();
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}, Block {4}",
                        config.getId(), message.getSenderId(), consensusInstance, round, block));

        // BYZANTINE_TESTS
        if (this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.NONE
                && !messageFromLeader(senderId)) {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "@    WARNING: PRE-PREPARE FROM NON LEADER!      @\n"
                            + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                    senderId));
            return Optional.empty();
        }

        // Verify every transaction signature
        for (int i = 0; i < block.getRequests().size(); i++) {

            LedgerRequest request = block.getRequests().get(i);
            String serializedRequest = request.getMessage();
            String clientValueSignature = request.getClientSignature();

            String clientId = request.getSenderId();
            Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientsConfig)
                    .filter(client -> client.getId().equals(clientId)).findFirst();

            if (clientConfig.isEmpty() || !RSAEncryption.verifySignature(serializedRequest, clientValueSignature,
                    clientConfig.get().getPublicKeyPath())) {
                LOGGER.log(Level.INFO, MessageFormat.format(
                        "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "@       WARNING: INVALID CLIENT SIGNATURE!      @\n"
                                + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                        senderId));
                return Optional.empty();
            }
        }

        // removi o justify porque era bloat

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return Optional.empty();
        }

        NodeMessage prepareMessage = new NodeMessage(config.getId(), NodeMessage.Type.PREPARE);
        prepareMessage.setConsensusInstance(message.getConsensusInstance());
        prepareMessage.setRound(message.getRound());
        prepareMessage.setBlock(message.getBlock());

        return Optional.of(prepareMessage);
    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     * 
     * @param message Message to be handled
     */
    public Optional<NodeMessage> uponPrepare(NodeMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        Block block = message.getBlock();
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}, Block {4}",
                        config.getId(), message.getSenderId(), consensusInstance, round, block));

        // Verify every transaction signature
        for (int i = 0; i < block.getRequests().size(); i++) {

            LedgerRequest request = block.getRequests().get(i);
            String serializedRequest = request.getMessage();
            String clientValueSignature = request.getClientSignature();

            String clientId = request.getSenderId();
            Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientsConfig)
                    .filter(client -> client.getId().equals(clientId)).findFirst();

            if (clientConfig.isEmpty() || !RSAEncryption.verifySignature(serializedRequest, clientValueSignature,
                    clientConfig.get().getPublicKeyPath())) {
                LOGGER.log(Level.INFO, MessageFormat.format(
                        "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "@       WARNING: INVALID CLIENT SIGNATURE!      @\n"
                                + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                        senderId));
                return Optional.empty();
            }
        }

        prepareMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (instance.getPreparedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return Optional.empty();
        }

        // Find value with valid quorum
        Optional<Block> preparedBlock = prepareMessages.hasValidQuorum(config.getId(), consensusInstance, round);
        if (preparedBlock.isPresent() && (instance == null || instance.getPreparedRound() < round)) {

            prepareMessages.verifyReceivedMessages(preparedBlock.get(), consensusInstance, round);

            // Set instance values
            this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(block));
            instance = this.instanceInfo.get(consensusInstance);
            instance.setPreparedRound(round);
            instance.setPreparedBlock(block);
            instance.setPreparedBlock(preparedBlock.get());

            NodeMessage commitMessage = new NodeMessage(config.getId(), NodeMessage.Type.COMMIT);
            commitMessage.setConsensusInstance(message.getConsensusInstance());
            commitMessage.setRound(message.getRound());
            commitMessage.setBlock(message.getBlock());

            return Optional.of(commitMessage);
        }

        return Optional.empty();
    }

    /*
     * Handle commit messages and if there is a valid quorum decide
     * 
     * @param message Message to be handled
     */
    public void uponCommit(NodeMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        Block block = message.getBlock();
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}, Block {4}",
                        config.getId(), message.getSenderId(), consensusInstance, round, block));

        // Verify every transaction signature
        for (int i = 0; i < block.getRequests().size(); i++) {

            LedgerRequest request = block.getRequests().get(i);
            String serializedRequest = request.getMessage();
            String clientValueSignature = request.getClientSignature();

            String clientId = request.getSenderId();
            Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientsConfig)
                    .filter(client -> client.getId().equals(clientId)).findFirst();

            if (clientConfig.isEmpty() || !RSAEncryption.verifySignature(serializedRequest, clientValueSignature,
                    clientConfig.get().getPublicKeyPath())) {
                LOGGER.log(Level.INFO, MessageFormat.format(
                        "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "@       WARNING: INVALID CLIENT SIGNATURE!      @\n"
                                + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                        senderId));
                return;
            }
        }

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (instance.getCommittedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return;
        }

        Optional<Block> committedValue = commitMessages.hasValidQuorum(config.getId(), consensusInstance, round);
        if (committedValue.isPresent() && (instance == null || instance.getCommittedRound() < round)) {

            commitMessages.verifyReceivedMessages(committedValue.get(), consensusInstance, round);

            // Add block to blockchain (in order)
            while (blockchain.size() < consensusInstance - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Should never be absent
            this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(block));
            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            this.addBlock(consensusInstance, committedValue.get());

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Value {3}",
                            config.getId(), consensusInstance, round, block));
        }
    }

    private boolean messageFromLeader(String senderId) {
        return this.leaderId.equals(senderId);
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

                            Optional<NodeMessage> nodeMessage = Optional.empty();

                            switch (message.getType()) {

                                case PRE_PREPARE -> {
                                    nodeMessage = uponPrePrepare((NodeMessage) message);
                                }

                                case PREPARE -> {
                                    nodeMessage = uponPrepare((NodeMessage) message);
                                }

                                case COMMIT -> {
                                    uponCommit((NodeMessage) message);
                                }

                                case ACK -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received ACK message from {1}",
                                                    config.getId(), message.getSenderId()));
                                    // ignore
                                }

                                case IGNORE -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    config.getId(), message.getSenderId()));
                                    // ignore
                                }

                                default -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    config.getId(), message.getSenderId()));
                                    // ignore
                                }
                            }

                            if (nodeMessage.isPresent()) {

                                // BYZANTINE_TESTS
                                // May apply byzantine behavior for testing purposes
                                switch (config.getByzantineBehavior()) {
                                    /*
                                     * Passive byzantine nodes will behave normally minus the
                                     * verification of signatures and verification of leader ids
                                     */
                                    case NONE, PASSIVE -> {
                                        this.link.broadcast(nodeMessage.get());
                                    }
                                    /*
                                     * Sends ACK to incoming message but doesn't broadcast anything
                                     * Meaning that the other nodes will not be stuck waiting for a reply
                                     */
                                    case DROP -> {
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Byzantine Don't Reply", config.getId()));
                                        // don't reply
                                    }
                                    /*
                                     * Because other nodes fail to verify the signature, they will not
                                     * reply with ACK meaning that this node will be stuck sending messages
                                     * forever
                                     */
                                    case FAKE_LEADER -> {
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Byzantine Fake Leader", config.getId()));
                                        NodeMessage byzantineMessage = nodeMessage.get();
                                        byzantineMessage.setSenderId(leaderId);
                                        this.link.broadcast(byzantineMessage);
                                    }
                                    /*
                                     * Since the byzantine node cant form a quorum of messages with this value,
                                     * the other nodes will never prepare/commit this fake value
                                     */
                                    case FAKE_VALUE, BAD_CONSENSUS -> {
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Byzantine Fake Value", config.getId()));
                                        NodeMessage byzantineMessage = nodeMessage.get();
                                        /*  TODO: fix me, need to find a way to send byzantine blocks
                                        List<String> byzantineArgs = byzantineMessage.getArgs();
                                        byzantineMessage.setArgs(byzantineArgs);
                                        byzantineArgs.set(byzantineArgs.size() - 1, "BYZANTINE_VALUE");
                                        this.link.broadcast(byzantineMessage);
                                        */
                                    }
                                    /*
                                     * Broadcast different messages to different nodes but same as FAKE_VALUE test
                                     * this will not affect the consensus
                                     */
                                    case BAD_BROADCAST -> {
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Byzantine Fake Value", config.getId()));
                                        // TODO: fix me, go see issue inside
                                        //this.link.badBroadcast(nodeMessage.get());
                                    }
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
