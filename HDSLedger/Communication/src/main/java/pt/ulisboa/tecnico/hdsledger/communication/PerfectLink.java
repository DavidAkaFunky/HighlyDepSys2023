package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.utilities.*;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig.ByzantineBehavior;

import java.io.IOException;
import java.net.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

public class PerfectLink {

    private static final CustomLogger LOGGER = new CustomLogger(PerfectLink.class.getName());
    // Time to wait for an ACK before resending the message
    private final int BASE_SLEEP_TIME;
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final Map<String, ProcessConfig> nodes = new ConcurrentHashMap<>();
    // Number of maximum byzantine nodes
    private final int maxByzantineNodeCount;
    // Reference to the node itself
    private final ProcessConfig config;
    // Class to deserialize messages to
    private final Class<? extends Message> messageClass;
    // Set of received messages from specific node (prevent duplicates)
    private final Map<String, CollapsingSet> receivedMessages = new ConcurrentHashMap<>();
    // Set of received ACKs from specific node
    private final CollapsingSet receivedAcks = new CollapsingSet();
    // Message counter
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    // Send messages to self by pushing to queue instead of through the network
    private final Queue<Message> localhostQueue = new ConcurrentLinkedQueue<>();

    public PerfectLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass) {
        this(self, port, nodes, messageClass, true, 200);
    }

    public PerfectLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass,
            boolean activateLogs, int baseSleepTime) {

        this.config = self;
        this.messageClass = messageClass;
        this.maxByzantineNodeCount = Math.floorDiv(nodes.length - 1, 3);
        this.BASE_SLEEP_TIME = baseSleepTime;

        Arrays.stream(nodes).forEach(node -> {
            String id = node.getId();
            this.nodes.put(id, node);
            receivedMessages.put(id, new CollapsingSet());
        });

        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(config.getHostname()));
        } catch (UnknownHostException | SocketException e) {
            throw new LedgerException(ErrorMessage.CannotOpenSocket);
        }
        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }
    }

    public void ackAll(List<Integer> messageIds) {
        receivedAcks.addAll(messageIds);
    }

    /*
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcasted
     */
    public void broadcast(Message data) {
        Gson gson = new Gson();
        nodes.forEach((destId, dest) -> send(destId, gson.fromJson(gson.toJson(data), data.getClass())));
    }

    /*
     * BYZANTINE_TESTS
     * Alternating broadcast of two messages to all nodes in the network
     * 
     * @param Message data1 The first message to be broadcasted
     * 
     * @param Message data2 The second message to be broadcasted
     */
    public void alternatingBroadcast(Message data1, Message data2) {
        Gson gson = new Gson();
        AtomicInteger parity = new AtomicInteger(1);
        nodes.forEach((destId, dest) -> {
            if (parity.getAndIncrement() % 2 == 0)
                send(destId, gson.fromJson(gson.toJson(data1), data1.getClass()));
            else
                send(destId, gson.fromJson(gson.toJson(data2), data2.getClass()));
        });
    }

    /*
     * Multicast to f+1 nodes in the network
     *
     * @param data The message to be broadcasted
     */
    public void smallQuorumMulticast(Message data) {
        multicast(data, maxByzantineNodeCount + 1);
    }

    /*
     * Multicast to 2f+1 nodes in the network
     *
     * @param data The message to be broadcasted
     */
    public void quorumMulticast(Message data) {
        multicast(data, 2 * maxByzantineNodeCount + 1);
    }

    /*
     * Multicast a message to N nodes in the network
     *
     * @param data The message to be broadcasted
     *
     * @param n The number of nodes to send the message to
     */
    public void multicast(Message data, int n) {
        List<String> nodeKeys = new ArrayList<>(nodes.keySet());

        if (n > nodeKeys.size())
            throw new LedgerException(ErrorMessage.NoLeader);
        if (n == nodeKeys.size())
            broadcast(data);

        Gson gson = new Gson();

        // Ensure that leader is always in the list
        Optional<Entry<String, ProcessConfig>> leader = nodes.entrySet().stream()
                .filter((config) -> config.getValue().isLeader()).findFirst();
        if (leader.isEmpty())
            throw new LedgerException(ErrorMessage.NoLeader);

        // Select n random nodes
        Random random = new Random();
        List<String> keys = new ArrayList<>();
        keys.add(leader.get().getKey());

        while (keys.size() < n) {
            String randomKey = nodeKeys.get(random.nextInt(nodeKeys.size()));
            if (!keys.contains(randomKey))
                keys.add(randomKey);
        }

        keys.forEach(destId -> send(destId, gson.fromJson(gson.toJson(data), data.getClass())));
    }

    /*
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId The node identifier
     *
     * @param data The message to be sent
     */
    public void send(String nodeId, Message data) {

        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                ProcessConfig node = nodes.get(nodeId);
                if (node == null)
                    throw new LedgerException(ErrorMessage.NoSuchNode);

                data.setMessageId(messageCounter.getAndIncrement());

                // If the message is not ACK, it will be resent
                InetAddress destAddress = InetAddress.getByName(node.getHostname());
                int destPort = node.getPort();
                int count = 1;
                int messageId = data.getMessageId();
                int sleepTime = BASE_SLEEP_TIME;

                // Send message to local queue instead of using network if destination in self
                if (nodeId.equals(this.config.getId())) {
                    this.localhostQueue.add(data);

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} - Message {1} (locally) sent to {2}:{3} successfully",
                                    config.getId(), data.getType(), destAddress, destPort));

                    return;
                }

                for (;;) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Sending {1} message to {2}:{3} with message ID {4} - Attempt #{5}", config.getId(),
                            data.getType(), destAddress, destPort, messageId, count++));

                    unreliableSend(destAddress, destPort, data);

                    // Wait (using exponential back-off), then look for ACK
                    Thread.sleep(sleepTime);

                    // receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;

                    // Might be a problem if it overflows (unlikely)
                    sleepTime <<= 1;
                }

                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Message {1} sent to {2}:{3} successfully",
                        config.getId(), data.getType(), destAddress, destPort));
            } catch (InterruptedException | UnknownHostException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /*
     * Sends a message to a specific node without guarantee of delivery
     * Mainly used to send ACKs, if they are lost, the original message will be
     * resent
     *
     * @param address The address of the destination node
     *
     * @param port The port of the destination node
     *
     * @param data The message to be sent
     */
    public void unreliableSend(InetAddress hostname, int port, Message data) {
        new Thread(() -> {
            try {

                // Sign message
                String jsonString = new Gson().toJson(data);
                Optional<String> signature;
                try {
                    signature = Optional.of(RSAEncryption.sign(jsonString, config.getPrivateKeyPath()));
                } catch (Exception e) {
                    throw new LedgerException(ErrorMessage.FailedToSignMessage);
                }

                // Serialize message
                SignedMessage message = new SignedMessage(jsonString, signature.get());
                byte[] buf = new Gson().toJson(message).getBytes();

                // Create UDP packet
                DatagramPacket packet = new DatagramPacket(buf, buf.length, hostname, port);

                socket.send(packet);

            } catch (IOException e) {
                e.printStackTrace();
                throw new LedgerException(ErrorMessage.SocketSendingError);
            }
        }).start();
    }

    /*
     * Receives a message from any node in the network (blocking)
     */
    public Message receive() throws IOException, ClassNotFoundException {

        Message message;
        Boolean local = false;
        SignedMessage responseData = null;
        DatagramPacket response = null;
        
        if (this.localhostQueue.size() > 0) {
            message = this.localhostQueue.poll();
            local = true; 
            this.receivedAcks.add(message.getMessageId());
        } else {
            byte[] buf = new byte[65535];
            response = new DatagramPacket(buf, buf.length);

            socket.receive(response);

            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            responseData = new Gson().fromJson(new String(buffer), SignedMessage.class);
            message = new Gson().fromJson(responseData.getMessage(), Message.class);

            // Verify signature (byzantine nodes will avoid it to cooperate with each other)
            // BYZANTINE_TESTS
            // Any byzantine node will not verify signatures
            if (config.getByzantineBehavior() == ByzantineBehavior.NONE
                    && !RSAEncryption.verifySignature(responseData.getMessage(), responseData.getSignature(),
                            nodes.get(message.getSenderId()).getPublicKeyPath())) {
                message.setType(Message.Type.IGNORE);

                LOGGER.log(Level.INFO, MessageFormat.format(
                        "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "@      WARNING: INVALID MESSAGE SIGNATURE!      @\n"
                                + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "IT IS POSSIBLE THAT NODE {0}:{1} IS DOING SOMETHING NASTY!",
                        InetAddress.getByName(response.getAddress().getHostName()), response.getPort()));

                return message;
            }
        }

        String senderId = message.getSenderId();
        int messageId = message.getMessageId();

        if (!nodes.containsKey(senderId))
            throw new LedgerException(ErrorMessage.NoSuchNode);

        // Handle ACKS, since it's possible to receive multiple acks from the same
        // message
        if (message.getType().equals(Message.Type.ACK)) {
            receivedAcks.add(messageId);
            return message;
        }

        // It's not an ACK -> Deserialize for the correct type
        if (!local)
            message = new Gson().fromJson(responseData.getMessage(), this.messageClass);

        boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
        Type originalType = message.getType();
        // Message already received (add returns false if already exists) => Discard
        if (isRepeated) {
            message.setType(Message.Type.IGNORE);
        }

        switch (message.getType()) {
            case CREATE, BALANCE, TRANSFER -> {
                return message;
            }
            case PRE_PREPARE -> {
                return message;
            }
            case IGNORE -> {
                if (!originalType.equals(Type.COMMIT) && !originalType.equals(Type.REPLY))
                    return message;
            }
            case PREPARE -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());

                return message;
            }
            case COMMIT -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());
            }
            case REPLY -> {
                LedgerResponse castedMessage = (LedgerResponse) message;
                receivedAcks.addAll(castedMessage.getRepliesTo());
            }
            default -> {
                System.out.println("WHAT: que mensagem vai responder com um ack: " + message.getType());
            }
        }

        if (!local) {
            InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
            int port = response.getPort();

            Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
            responseMessage.setMessageId(messageId);
            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates
            unreliableSend(address, port, responseMessage);
        }
        
        return message;
    }
}
