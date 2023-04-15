package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.*;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig.ByzantineBehavior;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    // Nodes configurations
    private final ProcessConfig[] nodesConfig;
    // Clients configurations
    private final ProcessConfig[] clientsConfig;
    // Current node is leader
    private final ProcessConfig config;
    // Leader configuration
    private final ProcessConfig leaderConfig;
    // Leader public key and hash
    private final PublicKey leaderPublicKey;
    private final String leaderPublicKeyHash;

    // Link to communicate with blockchain nodes
    private final PerfectLink link;
    // Link to communicate with client nodes
    private final PerfectLink clientLink;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;
    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;

    // Store if already received pre-prepare for a given <consensus, round>
    private final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();
    // Consensus instance information per consensus instance
    private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Current consensus instance
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
    // Store accounts and signatures of updates to accounts
    private final Ledger ledger;
    // Map of unconfirmed transactions
    private final Mempool mempool;
    // Account refresh threshold (# instances)
    private final int refreshThreshold = 2;

    public NodeService(ProcessConfig[] clientsConfig, PerfectLink link, PerfectLink clientLink, ProcessConfig config,
            ProcessConfig leaderConfig, ProcessConfig[] nodesConfig, Mempool mempool) {

        this.clientsConfig = clientsConfig;
        this.link = link;
        this.clientLink = clientLink;
        this.config = config;
        this.leaderConfig = leaderConfig;
        this.nodesConfig = nodesConfig;

        this.mempool = mempool;

        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);

        try {
            this.leaderPublicKey = RSAEncryption.readPublicKey(leaderConfig.getPublicKeyPath());
            this.leaderPublicKeyHash = RSAEncryption.digest(this.leaderPublicKey.toString());
        } catch (Exception e) {
            throw new LedgerException(ErrorMessage.FailedToReadPublicKey);
        }

        this.ledger = new Ledger(this.leaderConfig.getId(), this.leaderPublicKeyHash);

        // BYZANTINE_TESTS
        if (this.config.isLeader()
                && this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.LANDLORD_LEADER) {
            this.ledger.setFee(this.ledger.getFee().intValue() * 2);
        }

    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public void read(LedgerRequest request) {
        LedgerRequestBalance requestBalance = request.deserializeBalance();

        String publicKeyHash;
        try {
            publicKeyHash = RSAEncryption.digest(requestBalance.getAccountPubKey().toString());
        } catch (NoSuchAlgorithmException e) {
            return;
        }

        // Get latest account update and corresponding signatures
        Account account = this.ledger.getAccount(publicKeyHash);
        UpdateAccount accountUpdate = account.getMostRecentAccountUpdate();

        Map<String, String> signatures = this.ledger.getAccountUpdateSignatures(accountUpdate.getConsensusInstance(),
                publicKeyHash);

        // BYZANTINE_TESTS
        if (this.config.getByzantineBehavior() == ByzantineBehavior.FAKE_WEAK) {
            accountUpdate = new UpdateAccount(accountUpdate);
            accountUpdate.setBalance(accountUpdate.getBalance().subtract(BigDecimal.ONE));
        } else if (this.config.getByzantineBehavior() == ByzantineBehavior.FORCE_CONSENSUS_READ) {
            accountUpdate = new UpdateAccount(accountUpdate);
            accountUpdate.setBalance(accountUpdate.getBalance().add(BigDecimal.valueOf(this.config.getPort())));
        }

        LedgerResponse response = new LedgerResponse(this.config.getId(), accountUpdate.isValid(), accountUpdate,
                signatures,
                requestBalance.getNonce());

        List<Integer> repliesTo = new ArrayList<>();
        repliesTo.add(request.getMessageId());
        response.setRepliesTo(repliesTo);

        this.clientLink.send(request.getSenderId(), response);
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
    private Map<String, UpdateAccount> tryAddBlock(int instance, Block block) {

        // Public key hash -> {nonces}
        Map<String, List<Integer>> nonces = new HashMap<>();

        boolean isValid = true;

        if (instance == 1) {
            Arrays.stream(this.clientsConfig).forEach(client -> {
                PublicKey pubKey;
                try {
                    pubKey = RSAEncryption.readPublicKey(client.getPublicKeyPath());
                } catch (Exception e) {
                    throw new LedgerException(ErrorMessage.FailedToReadPublicKey);
                }
                Optional<Account> account = this.ledger.createAccount(client.getId(), pubKey);
                if (account.isEmpty()) {
                    throw new LedgerException(ErrorMessage.InvalidAccount);
                }
                nonces.putIfAbsent(account.get().getPublicKeyHash(), new ArrayList<>());
            });
            PublicKey pubKey;
            try {
                pubKey = RSAEncryption.readPublicKey(this.leaderConfig.getPublicKeyPath());
            } catch (Exception e) {
                throw new LedgerException(ErrorMessage.FailedToReadPublicKey);
            }
            Optional<Account> account = this.ledger.createAccount(this.leaderConfig.getId(), pubKey);
            account.get().activate();
            nonces.putIfAbsent(account.get().getPublicKeyHash(), new ArrayList<>());
            /*
             * Will create UpdateAccount with valid: False.
             * This will create and UpdateAccount for accounts that do not exist yet
             * when the client tries to read a non existing account he will get
             * an invalid update account with the corresponding signatures
             * and is able to confirm that that account does not exist
             */
            isValid = false;
        } else {

            List<LedgerRequest> requests = block.getRequests();

            // Process first all create account requests
            List<LedgerRequestCreate> appliedCreations = new ArrayList<>();
            for (LedgerRequest request : requests) {
                if (request.getType() == LedgerRequest.Type.CREATE) {
                    LedgerRequestCreate create = request.deserializeCreate();
                    Optional<Account> newAcc = this.ledger.activateAccount(request.getSenderId(),
                            create.getAccountPubKey(),
                            this.leaderPublicKey);
                    if (newAcc.isEmpty()) {
                        isValid = false;
                        break;
                    } else {
                        appliedCreations.add(create);
                        List<Integer> nonceSet = new ArrayList<>();
                        nonceSet.add(create.getNonce());
                        nonces.put(newAcc.get().getPublicKeyHash(), nonceSet);

                        // create update account for leader account
                        nonces.putIfAbsent(this.leaderPublicKeyHash, new ArrayList<>());
                    }
                }
            }

            // Check if any failed
            if (!isValid) {
                ListIterator<LedgerRequestCreate> li = appliedCreations.listIterator(appliedCreations.size());
                while (li.hasPrevious())
                    this.ledger.revertCreateAccount(li.previous());
                return this.createEmptyUpdateAccounts(instance, block);
            }

            // Process all transfer requests
            List<LedgerRequestTransfer> appliedTransfers = new ArrayList<>();
            for (LedgerRequest request : requests) {
                switch (request.getType()) {
                    case CREATE -> {
                        /* Already processed */ }
                    case TRANSFER -> {
                        LedgerRequestTransfer transfer = request.deserializeTransfer();
                        List<Account> accounts = this.ledger.transfer(instance, transfer.getAmount(),
                                transfer.getSourcePubKey(),
                                transfer.getDestinationPubKey(),
                                this.leaderPublicKey);
                        if (accounts.size() == 0) {
                            isValid = false;
                            break;
                        } else {
                            appliedTransfers.add(transfer);

                            // Create two UpdateAccounts (one with a nonce and the other empty)
                            String srcAccount = accounts.get(0).getPublicKeyHash();
                            nonces.putIfAbsent(srcAccount, new ArrayList<>());
                            nonces.get(srcAccount).add(transfer.getNonce());

                            String destAccount = accounts.get(1).getPublicKeyHash();
                            nonces.putIfAbsent(destAccount, new ArrayList<>());

                            // create update account for leader account
                            nonces.putIfAbsent(this.leaderPublicKeyHash, new ArrayList<>());
                        }
                    }
                    case BALANCE -> {
                        /* Ignore, used as a fallback for strong read */
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
                return this.createEmptyUpdateAccounts(instance, block);
            }

            // Refresh stale update accounts
            for (Account account : this.ledger.getAccounts().values()) {
                if (!account.isActive())
                    continue;
                UpdateAccount mostRecentUpdateAccount = account.getMostRecentAccountUpdate();
                if (mostRecentUpdateAccount != null
                        && instance - mostRecentUpdateAccount.getConsensusInstance() >= this.refreshThreshold) {
                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} - Refreshing signatures for account {1}",
                                    config.getId(), account.getOwnerId()));
                    nonces.putIfAbsent(account.getPublicKeyHash(), new ArrayList<>());
                }
            }
        }

        // Create account updates and sign them
        HashMap<String, UpdateAccount> accountUpdates = new HashMap<>();

        for (Map.Entry<String, List<Integer>> entry : nonces.entrySet()) {
            Account account = this.ledger.getTemporaryAccount(entry.getKey());
            List<Integer> accountNonces = entry.getValue();
            String accountSignature;
            UpdateAccount upAcc = new UpdateAccount(account.getOwnerId(), account.getPublicKeyHash(),
                    account.getBalance(), instance, accountNonces, instance == 1 ? account.isActive() : isValid);
            try {
                accountSignature = RSAEncryption.sign(upAcc.toJson(), this.config.getPrivateKeyPath());
            } catch (Exception e) {
                LOGGER.log(Level.INFO,
                        MessageFormat.format("{0} - Error signing account update for consensus instance {1}",
                                config.getId(), consensusInstance));
                e.printStackTrace();
                return this.createEmptyUpdateAccounts(instance, block);
            }

            accountUpdates.put(accountSignature, upAcc);

            this.ledger.addAccountUpdate(instance, account.getPublicKeyHash(), upAcc);
        }
        return accountUpdates;
    }

    private String hashPubKey(PublicKey pubKey) {
        String pubKeyHash;
        try {
            pubKeyHash = RSAEncryption.digest(pubKey.toString());
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
        return pubKeyHash;
    }

    /*
     * 
     */
    private Map<String, UpdateAccount> createEmptyUpdateAccounts(int instance, Block block) {

        // senderId -> pubKeyHash
        Map<String, String> senderToPubKeyHash = new HashMap<>();
        // pubKeyHash -> {nonces}
        Map<String, List<Integer>> nonces = new HashMap<>();

        List<LedgerRequest> requests = block.getRequests();
        for (LedgerRequest request : requests) {
            switch (request.getType()) {
                case CREATE -> {
                    LedgerRequestCreate create = request.deserializeCreate();
                    String pubKeyHash = hashPubKey(create.getAccountPubKey());
                    nonces.putIfAbsent(pubKeyHash, new ArrayList<>());
                    nonces.get(pubKeyHash).add(create.getNonce());
                    senderToPubKeyHash.put(request.getSenderId(), pubKeyHash);
                }
                case TRANSFER -> {
                    LedgerRequestTransfer transfer = request.deserializeTransfer();
                    String pubKeyHash = hashPubKey(transfer.getSourcePubKey());
                    nonces.putIfAbsent(pubKeyHash, new ArrayList<>());
                    nonces.get(pubKeyHash).add(transfer.getNonce());
                    senderToPubKeyHash.put(request.getSenderId(), pubKeyHash);
                }
                case BALANCE -> {
                    // do nothing
                }
                default -> {
                    // Should never happen
                    LOGGER.log(Level.INFO, "Invalid request type");
                }
            }
        }

        // signature -> update account
        Map<String, UpdateAccount> accountUpdates = new HashMap<>();
        for (Map.Entry<String, String> entry : senderToPubKeyHash.entrySet()) {
            String senderId = entry.getKey();
            String pubKeyHash = entry.getValue();

            List<Integer> senderNonces = nonces.get(pubKeyHash);

            UpdateAccount upAcc = new UpdateAccount(senderId, pubKeyHash, BigDecimal.ZERO, instance, senderNonces,
                    false);

            String accountSignature;
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

            this.ledger.addAccountUpdate(instance, pubKeyHash, upAcc);
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

    public void applyByzantineBehaviour(ConsensusMessage consensusMessage) {
        /*
         * Because other nodes fail to verify the signature, they will not
         * reply with ACK meaning that this node will be stuck sending messages
         * forever
         */
        switch (config.getByzantineBehavior()) {
            case FAKE_LEADER -> {
                LOGGER.log(Level.INFO,
                        MessageFormat.format("{0} - Byzantine Fake Leader", config.getId()));
                consensusMessage.setSenderId(this.leaderConfig.getId());
            }
            default -> {
                // Do nothing
            }
        }
    }

    public ConsensusMessage createConsensusMessage(Block block, int instance, int round) {
        // Sign block
        String blockSignature;
        String blockJson = block.toJson();
        try {
            blockSignature = RSAEncryption.sign(blockJson, this.config.getPrivateKeyPath());
        } catch (Exception e) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Error signing block for consensus instance {1}",
                    config.getId(), instance));
            e.printStackTrace();
            throw new LedgerException(ErrorMessage.FailedToSignMessage);
        }

        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(blockJson, blockSignature);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prePrepareMessage.toJson())
                .build();

        return consensusMessage;
    }

    /*
     * Start an instance of consensus for a block of transactions
     * Only the current leader will start a consensus instance
     * the remaining nodes only update blocks.
     *
     * @param inputBlock Block to be agreed upon
     */
    public void startConsensus(Block block) {

        // Set initial consensus blocks
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(block));

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node already started consensus for instance {1}",
                    config.getId(), localConsensusInstance));
            return;
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

            if (this.config.getByzantineBehavior() == ByzantineBehavior.BAD_BROADCAST && localConsensusInstance != 1) {
                LOGGER.log(Level.INFO,
                        MessageFormat.format("{0} - Node is Byzantine leader, sending alternating PRE-PREPARE messages", config.getId()));
                
                int numberOfRequests = block.getRequests().size();
                List<LedgerRequest> requests = block.getRequests();
                Block oddBlock = new Block();
                oddBlock.setConsensusInstance(localConsensusInstance);
                oddBlock.setRequests(requests.subList(0, numberOfRequests / 2));

                Block evenBlock = new Block();
                evenBlock.setConsensusInstance(localConsensusInstance);
                evenBlock.setRequests(requests.subList(numberOfRequests / 2, numberOfRequests));

                this.link.alternatingBroadcast(this.createConsensusMessage(oddBlock, localConsensusInstance, instance.getCurrentRound()),
                                               this.createConsensusMessage(evenBlock, localConsensusInstance, instance.getCurrentRound()));
            } else {
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));
                this.link.broadcast(this.createConsensusMessage(block, localConsensusInstance, instance.getCurrentRound()));
            }
        } else {
            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
        }
    }

    /*
     * Handle pre prepare messages and if the message
     * came from leader and is justified them broadcast prepare
     *
     * @param message Message to be handled
     */
    public void uponPrePrepare(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

        Block block = Block.fromJson(prePrepareMessage.getBlock());

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

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
            return;

        for (var req : block.getRequests())
            if ((req.getType() == Type.TRANSFER || req.getType() == Type.CREATE) && !checkAuthorIsOwner(req))
                return;

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

        applyByzantineBehaviour(consensusMessage);
        this.link.broadcast(consensusMessage);
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
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

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
            Map<String, UpdateAccount> accountUpdates = this.tryAddBlock(consensusInstance, preparedBlock.get());

            // If block is invalid, create "invalid" updateAccount with the requests nonce
            // to reply to the client requests, this instance will not update the blockchain
            // but the updateAccounts will be stored (as invalid)
            boolean isValidBlock = true;
            if (accountUpdates.values().size() == 0) {
                accountUpdates = new HashMap<>();
            } else if (consensusInstance != 1 && !accountUpdates.values().stream().toList().get(0).isValid()) {
                isValidBlock = false;
            }

            // Reply to every prepare message received with the signatures of the updated
            // account
            // This serves as proof that the update is valid (if a quorum of signatures is
            // obtained)
            CommitMessage c = new CommitMessage(isValidBlock, accountUpdates);
            instance.setCommitMessage(c);

            sendersMessage.forEach(senderMessage -> {
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

    private boolean checkAuthorIsOwner(LedgerRequest request) {
        PublicKey pubKey;
        if (request.getType() == Type.CREATE) {
            LedgerRequestCreate createRequest = request.deserializeCreate();
            pubKey = createRequest.getAccountPubKey();
        } else {
            LedgerRequestTransfer transferRequest = request.deserializeTransfer();
            pubKey = transferRequest.getSourcePubKey();
        }
        boolean result = false;
        Optional<ProcessConfig> senderConfig = Arrays.stream(this.clientsConfig)
                .filter(config -> config.getId().equals(request.getSenderId())).findAny();
        if (senderConfig.isEmpty()) {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "@          WARNING: SENDER IS NOT PRESENT IN CONFIG! @\n"
                            + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "IT IS POSSIBLE THAT CLIENT {0} IS DOING SOMETHING NASTY!",
                    request.getSenderId()));
            return result;
        }
        try {
            result = RSAEncryption.readPublicKey(senderConfig.get().getPublicKeyPath()).equals(pubKey);
        } catch (Exception e) {
            throw new LedgerException(ErrorMessage.FailedToReadPublicKey);
        }

        if (!result) {
            LOGGER.log(Level.INFO, MessageFormat.format(
                    "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "@          WARNING: SENDER IS NOT SOURCE ACCOUNT!   @\n"
                            + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                            + "IT IS POSSIBLE THAT CLIENT {0} IS DOING SOMETHING NASTY!",
                    request.getSenderId()));
        }

        return result;
    }

    /*
     * Verify if the signatures of the updated accounts are valid
     */
    private boolean verifyAccountSignatures(String senderId, int consensusInstance, CommitMessage message) {
        Map<String, UpdateAccount> accountSignatures = message.getUpdateAccountSignatures();

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
    public synchronized void uponCommit(ConsensusMessage message) {

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
            CommitMessage quorumCommitMessage = commitQuorum.get().stream().toList().get(0).deserializeCommitMessage();

            // Check if any of the commit messages received was different
            commitMessages.verifyReceivedCommitMessage(quorumCommitMessage, consensusInstance, round);

            // Verify if update accounts are valid or not
            boolean successfulAdd = quorumCommitMessage.isValidBlock();

            // Store signatures from other nodes
            commitQuorum.get().forEach((m) -> {
                String signerId = m.getSenderId();
                Map<String, UpdateAccount> updates = m.deserializeCommitMessage().getUpdateAccountSignatures();
                updates.forEach((signature, accountUpdate) -> this.ledger.addAccountUpdateSignature(consensusInstance,
                        accountUpdate.getHashPubKey(), signerId, signature));
            });

            if (successfulAdd) {
                // Apply temporary transactions to account and append block to blockchain
                this.ledger.commitTransactions(consensusInstance);
            }

            /*
             * What we have
             * {HashPubKey -> UpdateAccount}
             * LedgerRequests[]
             * 
             * What we want
             * Create a LedgerResponse with UpdateAccount and nonces that lead to that
             * and a LedgerResponse for each
             * 
             * For create and transfer we respond in bulk
             * For balance we respond individually
             */

            Map<String, LedgerResponse> responses = new HashMap<>();

            this.instanceInfo.get(consensusInstance).getPreparedBlock().getRequests()
                    .forEach(request -> {
                        switch (request.getType()) {
                            case CREATE, TRANSFER -> {
                                String accountHashPublicKey;
                                if (request.getType().equals(Type.CREATE))
                                    accountHashPublicKey = hashPubKey(request.deserializeCreate().getAccountPubKey());
                                else
                                    accountHashPublicKey = hashPubKey(request.deserializeTransfer().getSourcePubKey());

                                LedgerResponse response = responses.get(request.getSenderId());
                                if (response == null) {
                                    UpdateAccount updateAccount = this.ledger.getAccountUpdate(consensusInstance, accountHashPublicKey);

                                    response = new LedgerResponse(this.config.getId(), successfulAdd,
                                            updateAccount,
                                            this.ledger.getAccountUpdateSignatures(
                                                    updateAccount.getConsensusInstance(),
                                                    accountHashPublicKey));

                                    responses.put(request.getSenderId(), response);
                                }

                                if (this.config.isLeader())
                                    responses.get(request.getSenderId()).addReplyTo(request.getMessageId());
                                else {
                                    mempool.accept(queue -> {
                                        for (var storedRequest : queue) {
                                            if (storedRequest.getMessage().equals(request.getMessage())) {
                                                responses.get(request.getSenderId())
                                                        .addReplyTo(storedRequest.getMessageId());
                                                mempool.removeRequest(storedRequest);
                                                return;
                                            }
                                        }
                                    });
                                }
                            }
                            case BALANCE -> {
                                LedgerRequestBalance balance = request.deserializeBalance();
                                String accountHashPublicKey = hashPubKey(balance.getAccountPubKey());

                                Account acc = this.ledger.getAccount(accountHashPublicKey);

                                UpdateAccount accountUpdate = acc.getMostRecentAccountUpdate();

                                LedgerResponse response = new LedgerResponse(this.config.getId(),
                                        accountUpdate.isValid(),
                                        accountUpdate,
                                        this.ledger.getAccountUpdateSignatures(
                                                accountUpdate.getConsensusInstance(),
                                                accountHashPublicKey),
                                        balance.getNonce());

                                if (this.config.isLeader()) {
                                    response.addReplyTo(request.getMessageId());
                                } else {
                                    mempool.accept(queue -> {
                                        for (var storedRequest : queue) {
                                            if (storedRequest.getMessage().equals(request.getMessage())) {
                                                response.addReplyTo(storedRequest.getMessageId());
                                                mempool.removeRequest(storedRequest);
                                                return;
                                            }
                                        }
                                    });
                                }

                                this.clientLink.send(request.getSenderId(), response);
                            }
                            default -> {
                                // Should not happen
                                System.out.println("UNKNOWN REQUEST TYPE");
                            }
                        }
                    });

            for (var entry : responses.entrySet()) {
                this.clientLink.send(entry.getKey(), entry.getValue());
            }

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                            config.getId(), consensusInstance, round, successfulAdd));
        }
    }

    @Override
    public void listen() {
        // Create Genesis block (amen) to ensure all states are signed
        Block genesisBlock = new Block();
        genesisBlock.setConsensusInstance(0);
        this.startConsensus(genesisBlock);
        try {
            // Thread to listen on every request
            // This is not thread safe but it's okay because
            // a client only sends one request at a time
            // thread listening for client requests on clientPort {Append, Read}
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();

                        /*
                         * Sends ACK to incoming message but doesn't broadcast anything
                         * Meaning that the other nodes will not be stuck waiting for a reply
                         */
                        if (config.getByzantineBehavior() == ByzantineBehavior.DROP) {
                            LOGGER.log(Level.INFO,
                                    MessageFormat.format("{0} - Byzantine Don't Reply", config.getId()));
                            // don't reply
                            continue;
                        }

                        // Separate thread to handle each message
                        new Thread(() -> {

                            switch (message.getType()) {

                                case PRE_PREPARE ->
                                    uponPrePrepare((ConsensusMessage) message);


                                case PREPARE ->
                                    uponPrepare((ConsensusMessage) message);


                                case COMMIT ->
                                    uponCommit((ConsensusMessage) message);


                                case ACK ->
                                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                            config.getId(), message.getSenderId()));
                                    // ignore


                                case IGNORE ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                    config.getId(), message.getSenderId()));
                                    // ignore


                                default ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    config.getId(), message.getSenderId()));
                                    // ignore

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
