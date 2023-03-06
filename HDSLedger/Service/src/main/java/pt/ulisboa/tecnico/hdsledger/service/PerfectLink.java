package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.utilities.Serializer;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

public class PerfectLink {

    // Node identifier
    private int nodeId;
    // UDP Socket
    private DatagramSocket socket;
    // Map of all nodes in the network
    private HashMap<Integer, Entry<InetAddress, Integer>> nodes;
    // Set of received ACKs
    private Set<Integer> receivedAcks = ConcurrentHashMap.newKeySet();
    // Time to wait for an ACK before resending the message
    static final private int ACK_WAIT_TIME = 1000;

    public PerfectLink(String address, int port, int nodeId, HashMap<Integer, Entry<InetAddress, Integer>> nodes)
            throws UnknownHostException, SocketException {
        this.socket = new DatagramSocket(port, InetAddress.getByName(address));
        this.nodeId = nodeId;
        this.nodes = nodes;
    }

    /*
     * Broadcasts a message to all nodes in the network
     * 
     * @param data The message to be broadcasted
     */
    public void broadcast(Data data) {

        nodes.values().forEach((node) -> {
            try {
                send(node.getKey(), node.getValue(), data);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
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
    public void send(InetAddress address, int port, Data data) throws IOException, InterruptedException {
        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {

                byte[] buf = Serializer.serialize(data);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
                int messageId = data.getMessageId();

                // If the message is not ACK, within 1 second it will be resent
                int count = 0;
                while (!receivedAcks.contains(messageId)) {
                    System.out.println("Sending message for the " + ++count + " time");
                    socket.send(packet);
                    Thread.sleep(ACK_WAIT_TIME);
                }

                receivedAcks.remove(messageId);

            } catch (IOException | InterruptedException e) {
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
    public void unreliableSend(InetAddress address, int port, Data data) throws IOException {
        byte[] buf = Serializer.serialize(data);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        socket.send(packet);
    }

    /*
     * Receives a message from any node in the network
     */
    public Data receive() throws IOException, ClassNotFoundException {
        byte[] buf = new byte[256];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        socket.receive(packet);
        byte[] mockByteArr = new byte[packet.getLength()];

        System.arraycopy(packet.getData(), packet.getOffset(), mockByteArr, 0, packet.getLength());
        Data data = Serializer.deserialize(mockByteArr, Data.class);

        // TODO: If already received message, discard it

        if (data.getName().equals("ACK")) {
            receivedAcks.add(data.getMessageId());
        } else {
            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates
            unreliableSend(packet.getAddress(), packet.getPort(), new Data(nodeId, 0, "ACK", data.getMessageId()));
        }

        return data;
    }

}
