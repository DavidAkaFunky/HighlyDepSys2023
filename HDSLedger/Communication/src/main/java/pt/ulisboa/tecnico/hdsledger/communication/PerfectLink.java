package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.Serializer;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.io.FileNotFoundException;
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

    // Time to wait for an ACK before resending the message
    private static final int ACK_WAIT_TIME = 1000;
    private static final CustomLogger LOGGER = new CustomLogger(PerfectLink.class.getName());
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
                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Sending {1} message to {2}:{3} - Attempt #{4}", config.getId(), data.getType(), destAddress, destPort, count++));
                    unreliableSend(destAddress, destPort, data);
                    // update with 0 as the argument is just a get (update only happens when arg == lastAck + 1)
                    if (sendLink.tryUpdateSeq(0) >= data.getMessageId()) break;
                    Thread.sleep(ACK_WAIT_TIME);
                }
                // link.updateAck(messageId);
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Message {1} sent to {2}:{3} - successfully", config.getId(), data.getType(), destAddress, destPort));
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
                // Create UDP packet
                String jsonString = new Gson().toJson(data);
                Optional<String> signature;
                try {
                    signature = Optional.of(RSAEncryption.sign(jsonString, config.getPrivateKeyPath()));
                } catch (FileNotFoundException e) {
                    throw new LedgerException(ErrorMessage.ConfigFileNotFound);
                } catch (Exception e) {
                    // sorry DM
                    throw new RuntimeException();
                }

                SignedMessage message = new SignedMessage(jsonString, signature.get());
                byte[] buf = Serializer.serialize(message);
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
    public Message receive() throws IOException, ClassNotFoundException {

        byte[] buf = new byte[1024];
        DatagramPacket response = new DatagramPacket(buf, buf.length);

        socket.receive(response);
        
        byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
        SignedMessage responseData = new Gson().fromJson(new String(buffer), SignedMessage.class);
        Message message = new Gson().fromJson(responseData.getMessage(), Message.class);
        if (RSAEncryption.verifySignature(responseData.getMessage(), responseData.getSignature(), nodes.get(message.getSenderId()).getPublicKeyPath())) {
            message.setType(Message.Type.IGNORE);
            return message;
        }

        SimplexLink sendLink = senderLinks.get(message.getSenderId());
        SimplexLink recLink = receiverLinks.get(message.getSenderId());
        if (sendLink == null || recLink == null)
            throw new LedgerException(ErrorMessage.NoSuchNode);

        // ACK -> tratar logo
        if (message.getType().equals(Message.Type.ACK)) {
            int lastAck = sendLink.tryUpdateSeq(message.getMessageId());
            if (lastAck != message.getMessageId()) message.setType(Message.Type.IGNORE);
            return message;
        }

        // Normal -> if (nao ha buracos): devolve else: ignora
        if (recLink.tryUpdateSeq(message.getMessageId()) == message.getMessageId()){
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
