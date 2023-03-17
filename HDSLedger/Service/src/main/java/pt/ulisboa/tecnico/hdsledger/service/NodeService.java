package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.NodeMessage;
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

    // Store strings
    private final Map<Integer, String> blockchain = new ConcurrentHashMap<>();

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

    // Not needed yet
    // private Timer timer = new Timer();
    // private static final int TIMER_PERIOD = 10000;

    public NodeService(ProcessConfig[] clientsConfig, ProcessConfig config, PerfectLink link, String leaderId,
            int nodesLength) {
        this.clientsConfig = clientsConfig;
        this.config = config;
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
        LOGGER.log(Level.INFO,
                MessageFormat.format("Blockchain from node {0}: {1}", config.getId(), getBlockchain().values()));
    }

    public int getConsensusInstance() {
        return consensusInstance.get();
    }

    public List<String> getBlockchainStartingAtInstance(int startInstance) {

        return getBlockchain()
                .entrySet()
                .stream()
                .filter((Map.Entry<Integer, String> entry) -> (entry.getKey() > startInstance))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /*
     * Start an instance of consensus for value inputValue
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     * 
     * @param inputValue Value to be agreed upon
     */
    public int startConsensus(LedgerRequest request) {

        String inputValue = request.getValue();
        String inputValueSignature = request.getClientSignature();
        String clientId = request.getSenderId();

        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(inputValue));

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "{0} - Node already started consensus for instance {1}", config.getId(), localConsensusInstance));
            return localConsensusInstance;
        }

        // Leader broadcasts PRE-PREPARE message
        if (this.config.isLeader()) {

            InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
            List<String> messageArgs = new ArrayList<>();
            messageArgs.add(String.valueOf(localConsensusInstance));
            messageArgs.add(String.valueOf(instance.getCurrentRound()));
            messageArgs.add(instance.getInputValue());

            NodeMessage prePrepareMessage = new NodeMessage(config.getId(), NodeMessage.Type.PRE_PREPARE, messageArgs);
            prePrepareMessage.setClientId(clientId);
            prePrepareMessage.setValueSignature(inputValueSignature);

            LOGGER.log(Level.INFO, MessageFormat.format(
                    "{0} - Node is leader, sending PRE-PREPARE messages", config.getId()));

            this.link.broadcast(prePrepareMessage);
        } else {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
        }

        // Start timer (needed for round change)

        return localConsensusInstance;
    }

    /*
     * Handle pre prepare messages and if the message
     * came from leader and is justified them broadcast prepare
     * 
     * @param message Message to be handled
     */
    public Optional<NodeMessage> uponPrePrepare(NodeMessage message) {

        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));
        String value = message.getArgs().get(2);
        String senderId = message.getSenderId();

        String clientId = message.getClientId();
        String clientValueSignature = message.getValueSignature();

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

        Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientsConfig)
                .filter(client -> client.getId().equals(clientId)).findFirst();

        if (clientConfig.isEmpty() || !RSAEncryption.verifySignature(value, clientValueSignature,
                clientConfig.get().getPublicKeyPath())) {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "@       WARNING: INVALID CLIENT SIGNATURE!      @\n"
                            + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                    senderId));
            return Optional.empty();
        }

        if (!justifyPrePrepare(consensusInstance, round, value)) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}, Value {4} but not justified, ignoring",
                            config.getId(), message.getSenderId(), consensusInstance, round, value));
            return Optional.empty();
        }

        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return Optional.empty();
        }

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}, Value {4}",
                        config.getId(), message.getSenderId(), consensusInstance, round, value));

        NodeMessage prepareMessage = new NodeMessage(config.getId(), NodeMessage.Type.PREPARE, message.getArgs());
        prepareMessage.setClientId(clientId);
        prepareMessage.setValueSignature(clientValueSignature);

        // Cancel previous timer and start new one (needed for round change)

        return Optional.of(prepareMessage);
    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     * 
     * @param message Message to be handled
     */
    public Optional<NodeMessage> uponPrepare(NodeMessage message) {

        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));
        String value = message.getArgs().get(2);
        String clientValueSignature = message.getValueSignature();
        String clientId = message.getClientId();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}, Value {4}",
                        config.getId(), message.getSenderId(), consensusInstance, round, value));

        Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientsConfig)
                .filter(client -> client.getId().equals(clientId)).findFirst();
        if (clientConfig.isEmpty() || !RSAEncryption.verifySignature(value, clientValueSignature,
                clientConfig.get().getPublicKeyPath())) {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "@       WARNING: INVALID CLIENT SIGNATURE!      @\n"
                            + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                    message.getSenderId()));
            return Optional.empty();
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
        Optional<String> preparedValue = prepareMessages.hasValidQuorum(config.getId(), consensusInstance, round);
        if (preparedValue.isPresent() && (instance == null || instance.getPreparedRound() < round)) {

            prepareMessages.verifyReceivedMessages(preparedValue.get(), consensusInstance, round);

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

            NodeMessage commitMessage = new NodeMessage(config.getId(), NodeMessage.Type.COMMIT, messageArgs);
            commitMessage.setClientId(clientId);
            commitMessage.setValueSignature(clientValueSignature);

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

        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));
        String value = message.getArgs().get(2);
        String clientValueSignature = message.getValueSignature();
        String clientId = message.getClientId();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}, Value {4}",
                        config.getId(), message.getSenderId(), consensusInstance, round, value));

        Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientsConfig)
                .filter(client -> client.getId().equals(clientId)).findFirst();
        if (clientConfig.isEmpty() || !RSAEncryption.verifySignature(value, clientValueSignature,
                clientConfig.get().getPublicKeyPath())) {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "@       WARNING: INVALID CLIENT SIGNATURE!      @\n"
                            + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                    message.getSenderId()));
            return;
        }

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once for any round r
        if (instance.getCommittedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return;
        }

        Optional<String> committedValue = commitMessages.hasValidQuorum(config.getId(), consensusInstance, round);
        if (committedValue.isPresent() && (instance == null || instance.getCommittedRound() < round)) {

            commitMessages.verifyReceivedMessages(committedValue.get(), consensusInstance, round);

            // this.timer.cancel(); // Not needed for now

            // Add block to blockchain
            while (blockchain.size() < consensusInstance - 1) {
                try {
                    Thread.sleep(1000);
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
                            config.getId(), consensusInstance, round, value));
        }
    }

    void uponRoundChange(NodeMessage message) {
        int consensusInstance = Integer.parseInt(message.getArgs().get(0));
        int round = Integer.parseInt(message.getArgs().get(1));

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received ROUND-CHANGE message from {1}: Consensus Instance {2}, New Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        // NOT IMPLEMENTED
    }

    private boolean messageFromLeader(String senderId) {
        return this.leaderId.equals(senderId);
    }

    private boolean justifyPrePrepare(int consensusInstance, int round, String value) {
        // Round change is not implemented, therefore pre prepare should always be from round 1
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

                                case ROUND_CHANGE -> {
                                    uponRoundChange((NodeMessage) message);
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
                                        List<String> byzantineArgs = byzantineMessage.getArgs();
                                        byzantineMessage.setArgs(byzantineArgs);
                                        byzantineArgs.set(byzantineArgs.size() - 1, "BYZANTINE_VALUE");
                                        this.link.broadcast(byzantineMessage);
                                    }
                                    /*
                                     * Broadcast different messages to different nodes but same as FAKE_VALUE test
                                     * this will not affect the consensus
                                     */
                                    case BAD_BROADCAST -> {
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Byzantine Fake Value", config.getId()));
                                        this.link.badBroadcast(nodeMessage.get());
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
