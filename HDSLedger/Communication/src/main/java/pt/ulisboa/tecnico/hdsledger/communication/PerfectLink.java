package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.Serializer;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;

public class PerfectLink {

    // Time to wait for an ACK before resending the message
    private static final int ACK_WAIT_TIME = 1000;
    private static final Logger LOGGER = Logger.getLogger(PerfectLink.class.getName());
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final ConcurrentHashMap<String, NodeConfig> nodes = new ConcurrentHashMap<>();
    // Map ID to a unidirectional link, in this case where this node is the sender
    private final ConcurrentHashMap<String, SimplexLink> senderLinks = new ConcurrentHashMap<>();
    // Map ID to a unidirectional link, in this case where this node is the receiver
    private final ConcurrentHashMap<String, SimplexLink> receiverLinks = new ConcurrentHashMap<>();
    // Reference to the node itself
    private final NodeConfig node;

    public PerfectLink(NodeConfig self, NodeConfig[] nodes) {
        this.node = self;
        Arrays.stream(nodes).forEach(node -> {
            this.nodes.put(node.getId(), node);
            this.senderLinks.put(node.getId(), new SimplexLink());
            this.receiverLinks.put(node.getId(), new SimplexLink());
        });
        try {
            this.socket = new DatagramSocket(node.getPort(), InetAddress.getByName(node.getHostname()));
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
        nodes.forEach((destId, dest) -> {
            send(dest.getHostname(), dest.getPort(), data);
        });
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
    public void send(String hostname, int port, Message data) {
        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                int messageId = data.getMessageId();
                SimplexLink link = senderLinks.get(node.getId());
                // If the message is not ACK, within 1 second it will be resent
                int count = 1;
                for (; ; ) {
                    LOGGER.log(Level.INFO, "Sending message for the " + count++ + " time");
                    unreliableSend(InetAddress.getByName(hostname), port, data);
                    if (link.isPast(messageId)) break;
                    Thread.sleep(ACK_WAIT_TIME);
                }
                link.updateAck(messageId);
                LOGGER.log(Level.INFO, "Message sent successfully");
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
                byte[] buf = Serializer.serialize(data);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, hostname, port);
                socket.send(packet);
            } catch (IOException e) {
                throw new LedgerException(ErrorMessage.SocketSendingError);
            }
        }).start();
    }


    private NodeConfig safeGet(String nodeId, InetAddress address, int port) {
        nodes.putIfAbsent(nodeId, new NodeConfig(nodeId, address.toString(), port));
        return nodes.get(nodeId);
    }

    /*
     * Receives a message from any node in the network
     */
    public Message receive() throws IOException, ClassNotFoundException {
        byte[] buf = new byte[512];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        socket.receive(packet);

        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        Message message = Serializer.deserialize(packet.getData(), Message.class);

        NodeConfig node = safeGet(message.getSenderId(), address, port);

        // ACK -> tratar logo
        if (message.getType().equals(Message.Type.ACK)) {
            SimplexLink link = senderLinks.get(node.getId());
            int lastAck = link.updateAck(message.getMessageId());
            if (lastAck != (message.getMessageId() + 1)) message.setType(Message.Type.IGNORE);
            return message;
        }

        // Normal -> if (nao ha buracos): devolve else: ignora
        SimplexLink link = receiverLinks.get(node.getId());
        if (link.isExpected(message.getMessageId())) {
            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates
            unreliableSend(address, port,
                    new Message(this.node.getId(), link.updateAck(message.getMessageId()), Message.Type.ACK, new ArrayList<>()));
            return message;
        }
        message.setType(Message.Type.IGNORE);
        return message;
    }

    class SimplexLink {
        private int lastAck = 0;

        public SimplexLink() {}

        public boolean isExpected(int seq) {
            synchronized (this) {
                return (lastAck + 1) == seq;
            }
        }

        public boolean isPast(int seq) {
            synchronized (this) {
                return lastAck >= seq;
            }
        }

        public int updateAck(int seq) {
            synchronized (this) {
                if ((lastAck + 1) == seq) lastAck++;
                return lastAck;
            }
        }
    }
}
