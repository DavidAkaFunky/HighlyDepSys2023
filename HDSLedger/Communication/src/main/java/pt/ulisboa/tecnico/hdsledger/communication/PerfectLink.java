package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.Serializer;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PerfectLink {

    // Time to wait for an ACK before resending the message
    private static final int ACK_WAIT_TIME = 1000;
    private static final Logger LOGGER = Logger.getLogger(PerfectLink.class.getName());
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final Map<String, NodeConfig> nodes = new ConcurrentHashMap<>();
    // Map ID to a unidirectional link, in this case where this node is the sender
    private final Map<String, SimplexLink> senderLinks = new ConcurrentHashMap<>();
    // Map ID to a unidirectional link, in this case where this node is the receiver
    private final Map<String, SimplexLink> receiverLinks = new ConcurrentHashMap<>();
    // Reference to the node itself
    private final NodeConfig config;

    public PerfectLink(NodeConfig self, NodeConfig[] nodes) {
        this.config = self;
        Arrays.stream(nodes).forEach(node -> {
            this.nodes.put(node.getId(), node);
            // extremely scuffed as it makes us impose that port numbers are different in every host,
            // but it is what it is
            this.senderLinks.put(node.getId(),
                    new SimplexLinkBuilder().setSourceNodeConfig(config).setDestinationNodeConfig(node).build()
            );
            this.receiverLinks.put(node.getId(),
                    new SimplexLinkBuilder().setSourceNodeConfig(node).setDestinationNodeConfig(config).build()
            );

        });
        try {
            this.socket = new DatagramSocket(config.getPort(), InetAddress.getByName(config.getHostname()));
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
                SimplexLink recLink = receiverLinks.get(nodeId);
                if (sendLink == null || recLink == null)
                    throw new LedgerException(ErrorMessage.NoSuchNode);
                // move message stamping to perfect links
                data.setMessageId(sendLink.stampMessage());
                // If the message is not ACK, within 1 second it will be resent
                InetAddress destAddress = InetAddress.getByName(sendLink.getDestinationNodeConfig().getHostname());
                int destPort = sendLink.getDestinationNodeConfig().getPort();
                int count = 1;

                for (;;) {
                    LOGGER.log(Level.INFO, "Sending message for the " + count++ + " time");
                    unreliableSend(destAddress, destPort, data);
                    // update with 0 as the argument is just a get (update only happens when arg == lastAck + 1)
                    if (sendLink.updateSeq(0) >= data.getMessageId()) break;
                    Thread.sleep(ACK_WAIT_TIME);
                }
                // link.updateAck(messageId);
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

    /*
     * Receives a message from any node in the network
     */
    public Message receive(DatagramPacket packet) {

        Message message;
        try {
            message = Serializer.deserialize(packet.getData(), Message.class);
        } catch (IOException | ClassNotFoundException e) {
            throw new LedgerException(ErrorMessage.SocketReceivingError);
        }

        SimplexLink sendLink = senderLinks.get(message.getSenderId());
        SimplexLink recLink = receiverLinks.get(message.getSenderId());
        if (sendLink == null || recLink == null)
            throw new LedgerException(ErrorMessage.NoSuchNode);
        // ACK -> tratar logo
        if (message.getType().equals(Message.Type.ACK)) {
            int lastAck = sendLink.updateSeq(message.getMessageId());
            if (lastAck != message.getMessageId()) message.setType(Message.Type.IGNORE);
            return message;
        }

        // Normal -> if (nao ha buracos): devolve else: ignora
        if (recLink.updateSeq(message.getMessageId()) == message.getMessageId()){
            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates
            try { // this will never error
                var address = InetAddress.getByName(recLink.getSourceNodeConfig().getHostname());
                var port = recLink.getSourceNodeConfig().getPort();
                unreliableSend(address, port, new Message(this.config.getId(), message.getMessageId(), Message.Type.ACK, new ArrayList<>()));
            } catch(UnknownHostException e) {
                throw new LedgerException(ErrorMessage.NoSuchNode);
            }
            return message;
        }
        message.setType(Message.Type.IGNORE);
        return message;
    }
}
