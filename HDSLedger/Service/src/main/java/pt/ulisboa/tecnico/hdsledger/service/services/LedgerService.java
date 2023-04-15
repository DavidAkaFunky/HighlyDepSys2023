package pt.ulisboa.tecnico.hdsledger.service.services;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class LedgerService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(LedgerService.class.getName());
    // Clients configurations
    private final ProcessConfig[] clientConfigs;
    // Link to communicate with client nodes
    private final PerfectLink link;
    // Node configuration
    private final ProcessConfig config;
    // Node service that provides consensus interface
    private final NodeService service;
    // Map of unconfirmed transactions
    private final Mempool mempool;
    // Leader configuration
    private final ProcessConfig leaderConfig;
    // Used for BYZANTINE_TESTS
    private ProcessConfig censoredClient = null;

    public LedgerService(ProcessConfig[] clientConfigs, PerfectLink link, ProcessConfig config,
            NodeService service, Mempool mempool, ProcessConfig leaderConfig) {
        this.clientConfigs = clientConfigs;
        this.link = link;
        this.config = config;
        this.service = service;
        this.mempool = mempool;
        this.leaderConfig = leaderConfig;
        // BYZANTINE_TESTS
        if (this.config.isLeader()
                && this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.DICTATOR_LEADER) {
            Random r = new Random();
            int randomIndex = r.nextInt(this.clientConfigs.length);
            this.censoredClient = this.clientConfigs[randomIndex];
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - NOT ADDING REQUESTS FROM {1} TO THE MEMPOOL",
                    this.config.getId(), this.censoredClient.getId()));
        }
    }

    /*
     * Verifies if the client signature is valid by matching the sender id
     * public key with the signature inside the request
     * 
     * @param request LedgerRequest to verify
     */
    private boolean verifyClientSignature(LedgerRequest request) {

        // Find config of the sender
        Optional<ProcessConfig> clientConfig = Arrays.stream(this.clientConfigs)
                .filter(c -> c.getId().equals(request.getSenderId())).findFirst();
        if (clientConfig.isEmpty())
            throw new LedgerException(ErrorMessage.NoSuchClient);

        // Verify client action was signed by him
        if (RSAEncryption.verifySignature(request.getMessage(), request.getClientSignature(),
                clientConfig.get().getPublicKeyPath()))
            return true;

        LOGGER.log(Level.INFO, MessageFormat.format(
                "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                        + "@          WARNING: INVALID CLIENT SIGNATURE!        @\n"
                        + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                        + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                request.getSenderId()));
        return false;
    }

    /*
     * To detect byzantine leader cherry picking transactions, a timer is set
     * for each client request. The timer must be cancelled when the request is
     * processed, otherwise it will trigger a warning message.
     * 
     * @param request LedgerRequest to set timer for
     */
    private void setTimer(LedgerRequest request) {
        // Only non-leader nodes set the timer since leader will be the one
        // creating blocks with received transactions
        if (this.config.isLeader())
            return;

        String leaderId = this.leaderConfig.getId();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            // Logger
            CustomLogger LOGGER = LedgerService.LOGGER;

            @Override
            public void run() {
                LOGGER.log(Level.INFO, MessageFormat.format(
                        "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "@           WARNING: CLIENT REQUEST IGNORED!         @\n"
                                + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                        leaderId));
            }
        }, 30 * 1000);
        this.mempool.getTimers().put(request, timer);
    }

    public void createAccount(LedgerRequest request) {
        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received LedgerRequestCreate from {1}", this.config.getId(),
                        request.getSenderId()));

        // BYZANTINE_TESTS
        if (this.config.isLeader()
                && this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.DICTATOR_LEADER
                && this.censoredClient.getId().equals(request.getSenderId())) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - DICTATOR_LEADER, Ignoring LedgerRequestCreate from {1}",
                    this.config.getId(), request.getSenderId()));
            return;
        }

        if (!verifyClientSignature(request)) {
            return;
        }
        // BYZANTINE_TESTS
        if (this.config.isLeader()
                && this.config.getByzantineBehavior() != ProcessConfig.ByzantineBehavior.SILENT_LEADER)
            startConsensusIfBlock(mempool.add(request));
        else
            mempool.accept(queue -> {
                queue.add(request);
            });

        setTimer(request);
    }

    public void transfer(LedgerRequest request) {
        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received LedgerRequestTransfer from {1}", this.config.getId(),
                        request.getSenderId()));

        // BYZANTINE_TESTS
        if (this.config.isLeader()
                && this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.DICTATOR_LEADER
                && this.censoredClient.getId().equals(request.getSenderId())) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - DICTATOR_LEADER, Ignoring LedgerRequestTransfer from {1}",
                            this.config.getId(), request.getSenderId()));
            return;
        }

        if (!verifyClientSignature(request)) {
            return;
        }

        if (!checkAuthorIsOwner(request))
            return;

        // BYZANTINE_TESTS
        if (this.config.isLeader()
                && this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.HANDSY_LEADER
                && new Random().nextBoolean()) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Altered transfer request {1}", this.config.getId(),
                    request.getSenderId()));
            LedgerRequestTransfer transfer = request.deserializeTransfer();
            transfer.setAmount(new BigDecimal(transfer.getAmount().intValue() * 2 + 1));
            String requestTransferSerialized = new Gson().toJson(transfer);
            request.setMessage(requestTransferSerialized);
        }

        // BYZANTINE_TESTS
        if (this.config.isLeader()
                && this.config.getByzantineBehavior() == ProcessConfig.ByzantineBehavior.CORRUPT_LEADER) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Added transfer request {1}", this.config.getId(),
                    request.getSenderId()));

            LedgerRequestTransfer clientTransfer = request.deserializeTransfer();

            int currentNonce = 0;
            BigDecimal amount = clientTransfer.getAmount();
            PublicKey sourcePubKey = clientTransfer.getSourcePubKey();
            PublicKey destinationPubKey;
            try {
                destinationPubKey = RSAEncryption.readPublicKey(this.config.getPublicKeyPath());
            } catch (Exception e) {
                throw new LedgerException(ErrorMessage.FailedToReadPublicKey);
            }

            LedgerRequestTransfer requestTransfer = new LedgerRequestTransfer(currentNonce, sourcePubKey,
                    destinationPubKey,
                    amount);

            String requestTransferSerialized = new Gson().toJson(requestTransfer);
            String signature;
            try {
                signature = RSAEncryption.sign(requestTransferSerialized, config.getPrivateKeyPath());
            } catch (Exception e) {
                throw new LedgerException(ErrorMessage.FailedToSignMessage);
            }

            LedgerRequest fakeRequest = new LedgerRequest(this.config.getId(), Message.Type.TRANSFER,
                    requestTransferSerialized,
                    signature);

            startConsensusIfBlock(mempool.add(fakeRequest));
        }

        // BYZANTINE_TESTS
        if (this.config.isLeader()
                && this.config.getByzantineBehavior() != ProcessConfig.ByzantineBehavior.SILENT_LEADER)
            startConsensusIfBlock(mempool.add(request));
        else
            mempool.accept(queue -> {
                queue.add(request);
            });

        setTimer(request);
    }

    private boolean checkAuthorIsOwner(LedgerRequest request) {
        LedgerRequestTransfer transferRequest = request.deserializeTransfer();
        PublicKey pubKey = transferRequest.getSourcePubKey();
        boolean result = false;
        Optional<ProcessConfig> senderConfig = Arrays.stream(this.clientConfigs)
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

    public void balance(LedgerRequest request) {
        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received LedgerRequestBalance from {1}", this.config.getId(),
                        request.getSenderId()));

        if (!verifyClientSignature(request)) {
            return;
        }

        LedgerRequestBalance balanceRequest = request.deserializeBalance();

        switch (balanceRequest.getConsistencyMode()) {
            case STRONG, WEAK -> {
                this.service.read(request);
            }
            case CONSENSUS -> {
                if (this.config.isLeader())
                    startConsensusIfBlock(mempool.add(request));
                else
                    mempool.accept(queue -> {
                        queue.add(request);
                    });

                setTimer(request);
            }
        }
    }

    private void startConsensusIfBlock(Optional<Block> block) {
        if (block.isEmpty())
            return;
        this.service.startConsensus(block.get());
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
                                case CREATE -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received CREATE message from {1}",
                                                    this.config.getId(), message.getSenderId()));
                                    createAccount((LedgerRequest) message);
                                }
                                case TRANSFER -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received TRANSFER message from {1}",
                                                    this.config.getId(), message.getSenderId()));
                                    transfer((LedgerRequest) message);
                                }
                                case BALANCE -> {
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received BALANCE message from {1}",
                                                    this.config.getId(), message.getSenderId()));
                                    balance((LedgerRequest) message);
                                }
                                case ACK -> LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} - Received ACK message from {1}",
                                                this.config.getId(), message.getSenderId()));

                                case IGNORE -> LOGGER.log(Level.INFO,
                                        MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                this.config.getId(), message.getSenderId()));

                                default -> throw new LedgerException(ErrorMessage.CannotParseMessage);
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
