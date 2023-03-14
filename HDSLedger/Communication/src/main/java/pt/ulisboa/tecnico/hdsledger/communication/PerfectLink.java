package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;


import com.google.gson.Gson;

public class PerfectLink {

    private static final CustomLogger LOGGER = new CustomLogger(PerfectLink.class.getName());
    // Time to wait for an ACK before resending the message
    private static final int ACK_WAIT_TIME = 1000;
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final Map<String, ProcessConfig> nodes = new ConcurrentHashMap<>();
    // Set of received messages from specifi node (prevent duplicates)
    private Map<String, Set<Integer>> receivedMessages = new ConcurrentHashMap<>();
    // Set of received ACKs from specific node
    private Set<Integer> receivedAcks = ConcurrentHashMap.newKeySet();
    // Message counter
    private AtomicInteger messageCounter = new AtomicInteger(0);
    // Reference to the node itself
    private final ProcessConfig config;
    // Class to deserialize messages to
    private final Class<? extends Message> messageClass;

    public PerfectLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass) {
        this.config = self;
        this.messageClass = messageClass;
        Arrays.stream(nodes).forEach(node -> {
            String id = node.getId();
            this.nodes.put(id, node);
            receivedMessages.put(id, new HashSet<>());
        });

        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(config.getHostname()));
        } catch (UnknownHostException | SocketException e) {
            throw new LedgerException(ErrorMessage.CannotOpenSocket);
        }
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

                for (;;) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Sending {1} message to {2}:{3} with message ID {4} - Attempt #{5}", config.getId(),
                            data.getType(), destAddress, destPort, messageId, count++));

                    unreliableSend(destAddress, destPort, data);

                    // Wait, then look for ACK
                    Thread.sleep(ACK_WAIT_TIME);

                    // receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;
                }
                // link.updateAck(messageId);
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - NodeMessage {1} sent to {2}:{3} successfully",
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
                System.out.println(data.getMessageId() + " SENDING: " + jsonString);
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

        byte[] buf = new byte[1024];
        DatagramPacket response = new DatagramPacket(buf, buf.length);

        socket.receive(response);

        byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
        SignedMessage responseData = new Gson().fromJson(new String(buffer), SignedMessage.class);
        Message message = new Gson().fromJson(responseData.getMessage(), Message.class);
        System.out.println("RECEIVED: " + new Gson().toJson(message));

        // Verify signature
        if (!RSAEncryption.verifySignature(responseData.getMessage(), responseData.getSignature(),
                nodes.get(message.getSenderId()).getPublicKeyPath())) {
            message.setType(NodeMessage.Type.IGNORE);
            // TODO: get response hostname
            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - NodeMessage {1} from localhost:{2} was incorrectly signed, ignoring",
                            config.getId(), response.getPort()));
            return message;
        }

        String senderId = message.getSenderId();
        int messageId = message.getMessageId();

        if (!nodes.containsKey(senderId))
            throw new LedgerException(ErrorMessage.NoSuchNode);

        // Handle ACKS, since it's possible to receive multiple acks from the same message
        if (message.getType().equals(NodeMessage.Type.ACK)) {
            receivedAcks.add(messageId);
            return message;
        }

        // It's not an ACK -> Deserialize for the correct type
        message = new Gson().fromJson(responseData.getMessage(), messageClass);

        // Message already received (add returns false if already exists) => Discard
        if (!receivedMessages.get(message.getSenderId()).add(messageId)) {
            message.setType(NodeMessage.Type.IGNORE);
        }

        InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
        int port = response.getPort();

        Message responseMessage = new Message(this.config.getId(), NodeMessage.Type.ACK);
        responseMessage.setMessageId(message.getMessageId());

        // ACK is sent without needing for another ACK because
        // we're assuming an eventually synchronous network
        // Even if a node receives the message multiple times,
        // it will discard duplicates
        unreliableSend(address, port, responseMessage);
        return message;
    }
}
