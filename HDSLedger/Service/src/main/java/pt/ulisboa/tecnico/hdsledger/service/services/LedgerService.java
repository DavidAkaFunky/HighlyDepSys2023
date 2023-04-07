package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;
import pt.ulisboa.tecnico.hdsledger.service.models.Ledger;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.Arrays;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
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
    // Number of transactions per block
    private final int blockSize;
    // Store accounts and signatures of updates to accounts
    private Ledger ledger;
    // Map of unconfirmed transactions
    private final Mempool mempool;

    // Thread to run service
    private Thread thread;

    public LedgerService(ProcessConfig[] clientConfigs, PerfectLink link, ProcessConfig config,
            NodeService service, int blockSize, Ledger ledger, Mempool mempool) {
        this.clientConfigs = clientConfigs;
        this.link = link;
        this.config = config;
        this.service = service;
        this.blockSize = blockSize;

        this.ledger = ledger;
        this.mempool = mempool;
    }

    public Thread getThread() {
        return thread;
    }

    public void killThread() {
        thread.interrupt();
    }

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

    private void setTimer() {
        // Only non-leader nodes set the timer since leader will be the one
        // creating blocks with received transactions
        if (!this.config.isLeader())
            return;

        // Should be a map <transactionId -> timer>
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Timer ran out!");
            }
        }, 2 * 60 * 1000);
    }

    public void createAccount(LedgerRequest request) {
        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received LedgerRequestCreate from {1}", this.config.getId(),
                        request.getSenderId()));

        if (!verifyClientSignature(request)) {
            // TODO: reply to client
        }
        if (this.config.isLeader())
            startConsensusIfBlock(mempool.add(request));
        else
            mempool.accept(queue -> {
                queue.add(request);
            });
        setTimer();
    }

    public void transfer(LedgerRequest request) {
        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received LedgerRequestTransfer from {1}", this.config.getId(),
                        request.getSenderId()));

        if (!verifyClientSignature(request)) {
            // TODO: reply to client
        }

        if (this.config.isLeader())
            startConsensusIfBlock(mempool.add(request));
        else
            mempool.accept(queue -> {
                queue.add(request);
            });

        setTimer();
    }

    public void balance(LedgerRequest request) {
        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received LedgerRequestBalance from {1}", this.config.getId(),
                        request.getSenderId()));

        if (!verifyClientSignature(request)) {
            // TODO: reply to client
        }

        LedgerRequestBalance balanceRequest = request.deserializeBalance();

        switch (balanceRequest.getConsistencyMode()) {
            case STRONG -> {

            }
            case WEAK -> {

            }
        }
    }

    private void startConsensusIfBlock(Optional<Block> block) {
        if (block.isEmpty()) return;
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
