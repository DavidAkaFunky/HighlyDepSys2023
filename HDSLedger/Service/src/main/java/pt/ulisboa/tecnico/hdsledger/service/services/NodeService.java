package pt.ulisboa.tecnico.hdsledger.service.services;

import com.google.gson.GsonBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.Account;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.Ledger;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    // Blockchain
    private final Map<Integer, Block> blockchain = new ConcurrentHashMap<>();
    // Clients configurations
    private final ProcessConfig[] clientsConfig;
    // Link to communicate with blockchain nodes
    private final PerfectLink link;
    // Current node is leader
    private final ProcessConfig config;
    // Leader configuration
    private final ProcessConfig leaderConfig;
    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;
    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;
    // Store accounts and signatures of updates to accounts
    private Ledger ledger;
    // Map of unconfirmed transactions
    private final Mempool mempool;

    // Current consensus instance
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
    // Consensus instance information per consensus instance
    private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Store if already received pre-prepare for a given <consensus, round>
    private final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();

    private final ProcessConfig[] nodesConfig;

    private final PerfectLink clientLink;


    public NodeService(ProcessConfig[] clientsConfig, PerfectLink link, PerfectLink clientLink, ProcessConfig config,
            ProcessConfig leaderConfig, ProcessConfig[] nodesConfig, Ledger ledger, Mempool mempool) {

        this.clientsConfig = clientsConfig;
        this.link = link;
        this.clientLink = clientLink;
        this.config = config;
        this.leaderConfig = leaderConfig;
        this.nodesConfig = nodesConfig;

        this.ledger = ledger;
        this.mempool = mempool;

        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    /*
     * Checks if a block can be added to the blockchain
     * 
     * @param instance - Consensus instance
     * 
     * @param block - Block to validate
     * 
     * @return - Map signature -> account update it's signing or empty map if block
     * is invalid
     */
    public Map<String, UpdateAccount> tryAddBlock(int instance, Block block) {

        // Public key hash -> {nonces}
        Map<String, List<Integer>> nonces = new ConcurrentHashMap<>();

        boolean isValid = true;

        List<LedgerRequest> requests = block.getRequests();

        // Process first all create account requests
        List<LedgerRequestCreate> appliedCreations = new ArrayList<>();
        for (LedgerRequest request : requests) {
            if (request.getType() == LedgerRequest.Type.CREATE) {
                LedgerRequestCreate create = request.deserializeCreate();
                Optional<Account> newAcc = this.ledger.createAccount(request.getSenderId(), create);
                if (newAcc.isEmpty()) {
                    isValid = false;
                    break;
                } else {
                    appliedCreations.add(create);
                    List<Integer> nonceSet = new ArrayList<>();
                    nonceSet.add(create.getNonce());
                    nonces.put(newAcc.get().getPublicKeyHash(), nonceSet);
                }
            }
        }

        // Check if any failed
        if (!isValid) {
            ListIterator<LedgerRequestCreate> li = appliedCreations.listIterator(appliedCreations.size());
            while (li.hasPrevious())
                this.ledger.revertCreateAccount(li.previous());
            return new HashMap<>();
        }

        // Process all transfer requests
        List<LedgerRequestTransfer> appliedTransfers = new ArrayList<>();
        for (LedgerRequest request : requests) {
            switch (request.getType()) {
                case CREATE -> {
                    /* Already processed */ }
                case TRANSFER -> {
                    LedgerRequestTransfer transfer = request.deserializeTransfer();
                    List<Account> accounts = this.ledger.transfer(instance, transfer);
                    if (accounts.size() == 0)
                        isValid = false;
                    else {
                        appliedTransfers.add(transfer);

                        Account srcAccount = accounts.get(0);
                        List<Integer> srcAccountNonces = nonces.getOrDefault(srcAccount, new ArrayList<>());
                        srcAccountNonces.add(transfer.getNonce());
                        nonces.put(srcAccount.getPublicKeyHash(), srcAccountNonces);

                        Account destAccount = accounts.get(1);
                        List<Integer> destAccountNonces = nonces.getOrDefault(destAccount, new ArrayList<>());
                        nonces.put(destAccount.getPublicKeyHash(), destAccountNonces);
                    }
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

        // Check if any failed
        if (!isValid) {
            ListIterator<LedgerRequestTransfer> li = appliedTransfers.listIterator(appliedTransfers.size());
            while (li.hasPrevious())
                this.ledger.revertTransfer(li.previous());
            return new HashMap<>();
        }

        // Create account updates and sign them
        HashMap<String, UpdateAccount> accountUpdates = new HashMap<>();

        for (Map.Entry<String, List<Integer>> entry : nonces.entrySet()) {
            Account account = this.ledger.getTemporaryAccount(entry.getKey());
            List<Integer> accountNonces = entry.getValue();
            String accountSignature;
            UpdateAccount upAcc = new UpdateAccount(account.getOwnerId(), account.getPublicKeyHash(),
                    account.getBalance(),
                    instance, accountNonces);
            try {
                accountSignature = RSAEncryption.sign(upAcc.toJson(), this.config.getPrivateKeyPath());
            } catch (Exception e) {
                LOGGER.log(Level.INFO,
                        MessageFormat.format("{0} - Error signing account update for consensus instance {1}",
                                config.getId(), consensusInstance));
                e.printStackTrace();
                return new HashMap<>();
            }

            accountUpdates.put(accountSignature, upAcc);

            this.ledger.addAccountUpdate(instance, account.getPublicKeyHash(), upAcc);
        }
        return accountUpdates;
    }

    /*
     * Verify if a block was signed by the leader
     */
    private boolean checkIfSignedByLeader(String block, String leaderMessage, String errorLog) {
        if (this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.NONE
                && !RSAEncryption.verifySignature(block, leaderMessage, this.leaderConfig.getPublicKeyPath())) {
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
                LOGGER.log(Level.INFO,
                        MessageFormat.format("  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "  @       WARNING: INVALID CLIENT SIGNATURE!      @\n"
                                + "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!", senderId));
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
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node already started consensus for instance {1}",
                    config.getId(), localConsensusInstance));
            return localConsensusInstance;
        }

        // Only start a consensus instance if the last one was decided
        // We need to be sure that the previous block has been decided
        // RIP Multi-paxos :'-(
        while (lastDecidedConsensusInstance.get() < localConsensusInstance - 1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Error signing block for consensus instance {1}",
                        config.getId(), localConsensusInstance));
                e.printStackTrace();
                return -1;
            }

            PrePrepareMessage prePrepareMessage = new PrePrepareMessage(blockJson, blockSignature);

            ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                    .setConsensusInstance(localConsensusInstance)
                    .setRound(instance.getCurrentRound())
                    .setMessage(prePrepareMessage.toJson())
                    .build();

            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE messages", config.getId()));

            this.link.broadcast(consensusMessage);
        } else {
            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
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
                "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                        + "  @     WARNING: PRE-PREPARE FROM NON LEADER!     @\n"
                        + "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
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
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));
        }

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getBlock(),
                prePrepareMessage.getLeaderSignature());

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setMessage(prepareMessage.toJson())
                .setReplyTo(senderId)
                .setReplyToMessageId(senderMessageId)
                .build();

        return Optional.of(consensusMessage);
    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     *
     * @param message Message to be handled
     */
    public synchronized void uponPrepare(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();

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

        // Doesn't add duplicate messages
        prepareMessages.addMessage(message);

        // Set instance blocks
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(block));
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        // Late prepare (consensus already ended for other nodes) only reply to him (as
        // an ACK)
        if (instance.getPreparedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));

            ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                    .setConsensusInstance(consensusInstance)
                    .setRound(round)
                    .setReplyTo(senderId)
                    .setReplyToMessageId(message.getMessageId())
                    .setMessage(instance.getCommitMessage().toJson())
                    .build();

            link.send(senderId, m);
            return;
        }

        // Find block with valid quorum
        Optional<Block> preparedBlock = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
        if (preparedBlock.isPresent() && instance.getPreparedRound() < round) {

            prepareMessages.verifyReceivedPrepareMessage(preparedBlock.get(), consensusInstance, round);

            instance.setPreparedBlock(preparedBlock.get());
            instance.setPreparedRound(round);

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round)
                    .values();

            // Verify transactions validity and update temporary state
            Map<String, UpdateAccount> updateAccount = this.tryAddBlock(consensusInstance, preparedBlock.get());

            if (updateAccount.size() == 0) {
                // Reply to every prepare message sender with the information that the block
                // prepared is invalid
                // and therefore no update account signatures will be sent
                CommitMessage c = new CommitMessage(false);
                instance.setCommitMessage(c);

                sendersMessage.stream().forEach(senderMessage -> {
                    ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                            .setConsensusInstance(consensusInstance)
                            .setRound(round)
                            .setReplyTo(senderMessage.getSenderId())
                            .setReplyToMessageId(senderMessage.getMessageId())
                            .setMessage(c.toJson())
                            .build();

                    System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(m));

                    link.send(senderMessage.getSenderId(), m);
                });
            } else {
                // Reply to every prepare message received with the signatures of the updated
                // account
                // This serves as proof that the update is valid (if a quorum of signatures is
                // obtained)
                CommitMessage c = new CommitMessage(true, updateAccount);
                instance.setCommitMessage(c);

                sendersMessage.stream().forEach(senderMessage -> {
                    ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                            .setConsensusInstance(consensusInstance)
                            .setRound(round)
                            .setReplyTo(senderMessage.getSenderId())
                            .setReplyToMessageId(senderMessage.getMessageId())
                            .setMessage(c.toJson())
                            .build();

                    link.send(senderMessage.getSenderId(), m);
                });
            }
        }
    }

    /*
     * Verify if the signatures of the updated accounts are valid
     * The signatures wi
     */
    private boolean verifyAccountSignatures(String senderId, int consensusInstance, CommitMessage message) {
        Map<String, UpdateAccount> accountSignatures = message.getUpdateAccountSignatures();


        if ((message.isValidBlock() && accountSignatures.size() == 0)
                || (!message.isValidBlock() && accountSignatures.size() != 0)) return false;


        // Get sender public key from config
        Optional<ProcessConfig> senderConfig = Arrays.stream(this.nodesConfig)
                .filter(node -> node.getId().equals(senderId)).findFirst();
        if (senderConfig.isEmpty()) {
            return false;
        }

        for (var entry : accountSignatures.entrySet()) {
            String signature = entry.getKey();
            UpdateAccount accountUpdate = entry.getValue();

            if (!RSAEncryption.verifySignature(accountUpdate.toJson(), signature,
                    senderConfig.get().getPublicKeyPath())) {
                return false;
            }
        }

        return true;
    }

    /*
     * Handle commit messages and decide if there is a valid quorum
     *
     * @param message Message to be handled
     */
    public void uponCommit(ConsensusMessage message) {

        String senderId = message.getSenderId();
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        CommitMessage commitMessage = message.deserializeCommitMessage();

        if (!verifyAccountSignatures(senderId, consensusInstance, commitMessage)) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                    + "  @  WARNING: INVALID ACCOUNT UPDATE SIGNATURES!  @\n"
                                    + "  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                    + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                            senderId));
            return;
        }

        commitMessages.addMessage(message);

        // Technically, we already received a prepare which created a instanceInfo
        // however, this may not be the case :-)
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

        Optional<List<ConsensusMessage>> commitQuorum = commitMessages.hasValidCommitQuorum(config.getId(),
                consensusInstance, round);

        if (commitQuorum.isPresent() && instance.getCommittedRound() < round) {

            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            // They are all the same, so we can just get the first one
            Map<String, UpdateAccount> accountUpdates = commitQuorum.get().get(0).deserializeCommitMessage()
                    .getUpdateAccountSignatures();

            boolean successfulAdd = accountUpdates.size() > 0;

            if (successfulAdd) {

                // Store signatures from other nodes
                commitQuorum.get().stream().forEach((m) -> {
                    String signerId = m.getSenderId();
                    Map<String, UpdateAccount> updates = m.deserializeCommitMessage().getUpdateAccountSignatures();
                    updates.entrySet().stream().forEach((entry) -> {
                        String signature = entry.getKey();
                        UpdateAccount accountUpdate = entry.getValue();
                        this.ledger.addAccountUpdateSignature(consensusInstance, accountUpdate.getHashPubKey(),
                                signerId, signature);
                    });
                });

                // Apply temporary transactions to account and append block to blockchain
                this.ledger.commitTransactions(consensusInstance);
                this.blockchain.put(consensusInstance, instance.getPreparedBlock());

                // Reply to clients with the updated account and list of signatures
                accountUpdates.values().stream()
                        .filter(updateAccount -> updateAccount.getNonces().size() > 0)
                        .forEach((updateAccount) -> {
                            try {
                                LedgerResponse response = new LedgerResponse(this.config.getId(), true, updateAccount,
                                        this.ledger.getAccountUpdateSignatures(consensusInstance, senderId));
                                this.clientLink.send(updateAccount.getOwnerId(), response);
                            } catch (Exception e) {
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format(
                                                "{0} - Error signing account update for consensus instance {1}",
                                                config.getId(), consensusInstance));
                                e.printStackTrace();
                            }
                        });
            } else {

                // Reply to every request saying that the block was not valid
                instance.getPreparedBlock().getRequests().stream()
                        .map(Message::getSenderId)
                        .forEach(id -> {
                            try {
                                LedgerResponse response = new LedgerResponse(this.config.getId(), false);
                                this.link.send(id, response);
                            } catch (Exception e) {
                                LOGGER.log(Level.INFO,
                                        MessageFormat.format(
                                                "{0} - Error signing account update for consensus instance {1}",
                                                config.getId(), consensusInstance));
                                e.printStackTrace();
                            }
                        });
            }

            lastDecidedConsensusInstance.getAndIncrement();

            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(ledger));
            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Decided on Consensus Instance {1}, Round {2}, Successful Add? {3}",
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
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
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
