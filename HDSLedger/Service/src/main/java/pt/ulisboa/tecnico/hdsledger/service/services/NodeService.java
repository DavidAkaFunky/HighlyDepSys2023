package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.models.Ledger;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestBalance;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestCreate;
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
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.logging.Level;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    private ProcessConfig[] clientsConfig;

    // Current node is leader
    private ProcessConfig config;

    // Consensus info
    private AtomicInteger consensusInstance = new AtomicInteger(0);
    private Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    private Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();

    // Blockchain
    private final Map<Integer, Block> blockchain = new ConcurrentHashMap<>();
    private final Ledger ledger = new Ledger();

    private ProcessConfig leaderConfig;

    private PerfectLink link;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;

    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;

    public NodeService(ProcessConfig[] clientsConfig, ProcessConfig config, PerfectLink link,
            ProcessConfig leaderConfig, int nodesLength) {
        this.clientsConfig = clientsConfig;
        this.config = config;
        this.link = link;
        this.leaderConfig = leaderConfig;
        this.prepareMessages = new MessageBucket(nodesLength);
        this.commitMessages = new MessageBucket(nodesLength);
    }

    public boolean tryAddBlock(int instance, Block block) {
        List<LedgerRequest> nonCreateRequests = new ArrayList<>();
        for (LedgerRequest request : block.getRequests()) {
            if (request.getType() != LedgerRequest.Type.CREATE) {
                nonCreateRequests.add(request);
            }
            else {
                LedgerRequestCreate create = request.deserializeCreate();
                if (!this.ledger.createAccount(create))
                return false;
            }
        }
        
        boolean isValid = true;
        List<LedgerRequestTransfer> appliedTransfers = new ArrayList<>();
        for (LedgerRequest request : nonCreateRequests) {
            switch (request.getType()) {
                case TRANSFER -> {
                    LedgerRequestTransfer transfer = request.deserializeTransfer();
                    if (!this.ledger.transfer(transfer))
                        isValid = false;
                    else
                        appliedTransfers.add(transfer);
                }
                case BALANCE -> {
                    LedgerRequestBalance balance = request.deserializeBalance();
                    // TODO
                }
                default -> {
                    // Should never happen
                    LOGGER.log(Level.INFO, "Invalid request type");
                    isValid = false;
                }
            }
            if (!isValid)
                break;
        }
        if (isValid) {
            this.blockchain.put(instance, block);
            return true;
        }
        ListIterator<LedgerRequestTransfer> li = appliedTransfers.listIterator(appliedTransfers.size());
        while (li.hasPrevious()) {
            this.ledger.revertTransfer(li.previous());
        }
        return false;
    }

    public int getConsensusInstance() {
        return consensusInstance.get();
    }

    /*
     * 
     */
    private boolean checkIfSignedByLeader(String block, String leaderMessage, String errorLog) {
        if (this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.NONE &&
                !RSAEncryption.verifySignature(block, leaderMessage,
                        this.leaderConfig.getPublicKeyPath())) {
            LOGGER.log(Level.INFO, errorLog);

            return false;
        }

        return true;
    }

    /*
     * Verify every transaction signature
     */
    private boolean verifyTransactions(List<LedgerRequest> requests, String senderId) {

        for (LedgerRequest request : requests) {
            String serializedRequest = request.getMessage();
            String clientBlockSignature = request.getClientSignature();

            String clientId = request.getSenderId();
            Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientsConfig)
                    .filter(client -> client.getId().equals(clientId)).findFirst();

            if (clientConfig.isEmpty() || !RSAEncryption.verifySignature(serializedRequest, clientBlockSignature,
                    clientConfig.get().getPublicKeyPath())) {
                LOGGER.log(Level.INFO, MessageFormat.format(
                        "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "@       WARNING: INVALID CLIENT SIGNATURE!      @\n"
                                + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                        senderId));
                return false;
            }
        }

        return true;
    }

    /*
     * Start an instance of consensus for a block of transactions
     * Only the current leader will start a consensus instance
     * the remaining nodes only update blocks.
     * 
     * @param inputBlock Block to be agreed upon
     */
    public int startConsensus(Block block) {

        // Set initial consensus blocks
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

            // Sign block
            String blockSignature;
            String blockJson = block.toJson();
            try {
                blockSignature = RSAEncryption.sign(blockJson, this.config.getPrivateKeyPath());
            } catch (Exception e) {
                LOGGER.log(Level.INFO, MessageFormat.format(
                        "{0} - Error signing block for consensus instance {1}", config.getId(),
                        localConsensusInstance));
                e.printStackTrace();
                return -1;
            }

            PrePrepareMessage prePrepareMessage = new PrePrepareMessage(blockJson, blockSignature);
            ConsensusMessage consensusMessage = new ConsensusMessage(config.getId(), Message.Type.PRE_PREPARE);
            consensusMessage.setConsensusInstance(localConsensusInstance);
            consensusMessage.setRound(instance.getCurrentRound());
            consensusMessage.setMessage(prePrepareMessage.toJson());

            LOGGER.log(Level.INFO, MessageFormat.format(
                    "{0} - Node is leader, sending PRE-PREPARE messages", config.getId()));

            this.link.broadcast(consensusMessage);
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
    public Optional<ConsensusMessage> uponPrePrepare(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

        Block block = Block.fromJson(prePrepareMessage.getBlock());

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}, Block {4}",
                        config.getId(), senderId, consensusInstance, round, block));

        String errorLog = MessageFormat.format(
                "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                        + "@     WARNING: PRE-PREPARE FROM NON LEADER!     @\n"
                        + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                        + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                senderId);

        // BYZANTINE_TESTS
        // Verify if block was signed by leader
        // Assumption: private keys not leaked
        if (!(checkIfSignedByLeader(prePrepareMessage.getBlock(), prePrepareMessage.getLeaderSignature(), errorLog)
                && verifyTransactions(block.getRequests(), senderId)))
            return Optional.empty();

        // Set instance blocks (node may not receive a call from the client)
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(block));

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, " +
                                    "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));
        }

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getBlock(),
                prePrepareMessage.getLeaderSignature());

        ConsensusMessage consensusMessage = new ConsensusMessage(config.getId(), Message.Type.PREPARE);
        consensusMessage.setConsensusInstance(consensusInstance);
        consensusMessage.setRound(round);
        consensusMessage.setMessage(prepareMessage.toJson());
        consensusMessage.setReplyTo(senderId);
        consensusMessage.setReplyToMessageId(senderMessageId);

        return Optional.of(consensusMessage);
    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     * 
     * @param message Message to be handled
     */
    public void uponPrepare(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrepareMessage prepareMessage = message.deserializePrepareMessage();

        Block block = Block.fromJson(prepareMessage.getBlock());

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}, Block {4}",
                        config.getId(), senderId, consensusInstance, round, block));

        String errorLog = MessageFormat.format(
                "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                        + "  @       WARNING: PREPARE FROM NON LEADER!       @\n"
                        + "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                        + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                senderId);

        // BYZANTINE_TESTS
        // Verify if block was signed by leader
        // Assumption: private keys not leaked
        if (!(checkIfSignedByLeader(prepareMessage.getBlock(), prepareMessage.getLeaderSignature(), errorLog)
           && verifyTransactions(block.getRequests(), senderId)))
            return;

        // Doesnt add duplicate messages
        prepareMessages.addMessage(message);

        // Set instance blocks
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(block));
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (instance.getPreparedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, " +
                                    "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));

            ConsensusMessage commitMessage = createCommitMessage(consensusInstance, round, senderId,
                    senderMessageId);
            link.send(senderId, commitMessage);
        }

        // Find block with valid quorum
        Optional<Block> preparedBlock = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
        if (preparedBlock.isPresent() && instance.getPreparedRound() < round) {

            prepareMessages.verifyReceivedPrepareMessage(preparedBlock.get(), consensusInstance, round);

            instance.setPreparedRound(round);
            instance.setPreparedBlock(block);
            instance.setPreparedBlock(preparedBlock.get());

            // Send commit messages to all nodes that sent prepare messages for this
            // instance and round (acknowledging that they have received the prepare)
            prepareMessages.getMessages(consensusInstance, round);
        }
    }

    private ConsensusMessage createCommitMessage(int consensusInstance, int round, String senderId,
            int senderMessageId) {
        ConsensusMessage commitMessage = new ConsensusMessage(config.getId(), ConsensusMessage.Type.COMMIT);
        commitMessage.setConsensusInstance(consensusInstance);
        commitMessage.setRound(round);
        commitMessage.setReplyTo(senderId);
        commitMessage.setReplyToMessageId(senderMessageId);
        return commitMessage;
    }

    /*
     * Handle commit messages and if there is a valid quorum decide
     * 
     * @param message Message to be handled
     */
    public void uponCommit(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            // Should never happen because only receives commit as a response to a prepare
            // message
            MessageFormat.format(
                    "{0} - CRITICAL: Received COMMIT message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                    config.getId(), message.getSenderId(), consensusInstance, round);
            return;
        }

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (instance.getCommittedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return;
        }

        Optional<String> committedBlock = commitMessages.hasValidCommitQuorum(config.getId(), consensusInstance, round);
        if (committedBlock.isPresent() && instance.getCommittedRound() < round) {

            commitMessages.verifyReceivedCommitMessage(committedBlock.get(), consensusInstance, round);

            // Add block to blockchain (in order)
            while (blockchain.size() < consensusInstance - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            boolean successfulAdd = this.tryAddBlock(consensusInstance, Block.fromJson(committedBlock.get()));

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Successful Add? {3}",
                            config.getId(), consensusInstance, round, successfulAdd));
        }
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

                            Optional<ConsensusMessage> consensusMessage = Optional.empty();

                            switch (message.getType()) {

                                case PRE_PREPARE -> {
                                    consensusMessage = uponPrePrepare((ConsensusMessage) message);
                                }

                                case PREPARE -> {
                                    uponPrepare((ConsensusMessage) message);
                                }

                                case COMMIT -> {
                                    uponCommit((ConsensusMessage) message);
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

                            if (consensusMessage.isPresent()) {

                                // BYZANTINE_TESTS
                                // May apply byzantine behavior for testing purposes
                                switch (config.getByzantineBehavior()) {
                                    /*
                                     * Passive byzantine nodes will behave normally minus the
                                     * verification of signatures and verification of leader ids
                                     */
                                    case NONE, PASSIVE -> {
                                        this.link.broadcast(consensusMessage.get());
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
                                        ConsensusMessage byzantineMessage = consensusMessage.get();
                                        byzantineMessage.setSenderId(this.leaderConfig.getId());
                                        this.link.broadcast(byzantineMessage);
                                    }
                                    /*
                                     * Since the byzantine node cant form a quorum of messages with this block,
                                     * the other nodes will never prepare/commit this fake block
                                     */
                                    case FAKE_VALUE, BAD_CONSENSUS -> {
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Byzantine Fake Block", config.getId()));
                                        ConsensusMessage byzantineMessage = consensusMessage.get();
                                        /*
                                         * TODO: fix me, need to find a way to send byzantine blocks
                                         * List<String> byzantineArgs = byzantineMessage.getArgs();
                                         * byzantineMessage.setArgs(byzantineArgs);
                                         * byzantineArgs.set(byzantineArgs.size() - 1, "BYZANTINE_VALUE");
                                         * this.link.broadcast(byzantineMessage);
                                         */
                                    }
                                    /*
                                     * Broadcast different messages to different nodes but same as FAKE_VALUE test
                                     * this will not affect the consensus
                                     */
                                    case BAD_BROADCAST -> {
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Byzantine Fake Block", config.getId()));
                                        // TODO: fix me, go see issue inside
                                        // this.link.badBroadcast(consensusMessage.get());
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
