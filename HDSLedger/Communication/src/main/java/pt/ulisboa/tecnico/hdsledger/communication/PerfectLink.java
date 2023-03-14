package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.io.IOException;
import java.net.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.google.gson.Gson;

public class PerfectLink {

    private static final CustomLogger LOGGER = new CustomLogger(PerfectLink.class.getName());
    // Time to wait for an ACK before resending the message
    private static final int ACK_WAIT_TIME = 100;
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final Map<String, ProcessConfig> nodes = new ConcurrentHashMap<>();
    // Map ID to a unidirectional link, in this case where this node is the sender
    private final Map<String, SimplexLink> senderLinks = new ConcurrentHashMap<>();
    // Map ID to a unidirectional link, in this case where this node is the receiver
    private final Map<String, SimplexLink> receiverLinks = new ConcurrentHashMap<>();
    // Reference to the node itself
    private final ProcessConfig config;
    // Class to deserialize messages to
    private final Class<? extends Message> messageClass;

    public PerfectLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass) {
        this.config = self;
        this.messageClass = messageClass;

        Arrays.stream(nodes).forEach(node -> {
            this.nodes.put(node.getId(), node);
            // extremely scuffed as it makes us impose that port numbers are different in
            // every host, but it is what it is
            this.senderLinks.put(node.getId(),
                    new SimplexLinkBuilder().setSourceNodeConfig(config).setDestinationNodeConfig(node).build());
            this.receiverLinks.put(node.getId(),
                    new SimplexLinkBuilder().setSourceNodeConfig(node).setDestinationNodeConfig(config).build());
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
        nodes.forEach((destId, dest) -> send(destId, data));
    }

    /*
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param address The address of the destination node
     *
     * @param port The port of the destination node
     *
     * @param data The message to be sent
     */
    public void send(String nodeId, Message data) {
        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                // this validates input
                SimplexLink sendLink = senderLinks.get(nodeId);
                SimplexLink recvLink = receiverLinks.get(nodeId);
                if (sendLink == null || recvLink == null)
                    throw new LedgerException(ErrorMessage.NoSuchNode);

                // move message stamping to perfect links
                data.setMessageId(sendLink.stampMessage());

                // If the message is not ACK, within 1 second it will be resent
                InetAddress destAddress = InetAddress.getByName(sendLink.getDestinationNodeConfig().getHostname());
                int destPort = sendLink.getDestinationNodeConfig().getPort();
                int count = 1;

                for (;;) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Sending {1} message to {2}:{3} with message ID {4} (current ack is {5}) - Attempt #{6}", config.getId(),
                            data.getType(), destAddress, destPort, data.getMessageId(), sendLink.getSequenceNumber(), count++));

                    unreliableSend(destAddress, destPort, data);

                    // Wait, then look for ACK
                    Thread.sleep(ACK_WAIT_TIME);

                    if (sendLink.getLastAckedSeq() >= data.getMessageId())
                        break;
                }
                // link.updateAck(messageId);
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - NodeMessage {1} sent to {2}:{3} successfully", config.getId(), data.getType(), destAddress, destPort));
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

        byte[] buf = new byte[1024];
        DatagramPacket response = new DatagramPacket(buf, buf.length);

        socket.receive(response);

        byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
        SignedMessage responseData = new Gson().fromJson(new String(buffer), SignedMessage.class);
        Message message = new Gson().fromJson(responseData.getMessage(), Message.class);
        if (!RSAEncryption.verifySignature(responseData.getMessage(), responseData.getSignature(),
                nodes.get(message.getSenderId()).getPublicKeyPath())) {
            message.setType(NodeMessage.Type.IGNORE);
            // TODO: get response hostname
            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - NodeMessage {1} from localhost:{2} was incorrectly signed, ignoring",
                            config.getId(), response.getPort()));
            return message;
        }

        SimplexLink sendLink = senderLinks.get(message.getSenderId());
        SimplexLink recvLink = receiverLinks.get(message.getSenderId());
        if (sendLink == null || recvLink == null)
            throw new LedgerException(ErrorMessage.NoSuchNode);

        // ACK -> tratar logo
        if (message.getType().equals(NodeMessage.Type.ACK)) {
            int lastAck = sendLink.tryUpdateAck(message.getMessageId());
            if (lastAck != message.getMessageId()) {
                // TODO: get response hostname
                LOGGER.log(Level.INFO,
                        MessageFormat.format("{0} - NodeMessage {1} from localhost:{2} was out of order, ignoring",
                                config.getId(), message.getType(), response.getPort()));
                message.setType(NodeMessage.Type.IGNORE);
            }
            return message;
        }

        // It's not an ACK -> Deserialize for the correct type
        message = new Gson().fromJson(responseData.getMessage(), messageClass);

        // Normal -> if (nao ha buracos): devolve else: ignora
        if (recvLink.tryUpdateSeq(message.getMessageId()) == message.getMessageId()) {
            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates
            try {
                var address = InetAddress.getByName(response.getAddress().getHostAddress());
                var port = response.getPort();
                LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - ACKING {1} - ID {2} message to {3}:{4}", config.getId(),
                            message.getType(), message.getMessageId(), address, port));
                unreliableSend(address, port,
                        new Message(this.config.getId(), message.getMessageId(), NodeMessage.Type.ACK));
            } catch (UnknownHostException e) {
                throw new LedgerException(ErrorMessage.NoSuchNode);
            }
            return message;
        }
        // TODO: get response hostname
        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - NodeMessage {1} from localhost:{2} was out of order, ignoring",
                        config.getId(), message.getType(), response.getPort()));
        message.setType(NodeMessage.Type.IGNORE);
        return message;
    }
}
