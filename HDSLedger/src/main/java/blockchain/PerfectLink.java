package blockchain;

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
import utils.Serializer;

public class PerfectLink {
    
    private int nodeId;
    private DatagramSocket socket;
    private HashMap<Integer, Entry<InetAddress, Integer>> nodes;
    private Set<Integer> receivedAcks = ConcurrentHashMap.newKeySet();

    public PerfectLink(String address, int port, int nodeId, HashMap<Integer, Entry<InetAddress, Integer>> nodes) throws UnknownHostException, SocketException {
        this.socket = new DatagramSocket(port, InetAddress.getByName(address));
        this.nodeId = nodeId;
        this.nodes = nodes;
    }

    public void broadcast(Data data) {
        nodes.values().forEach((node) -> {
            try {
                send(node.getKey(), node.getValue(), data);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public void send(InetAddress address, int port, Data data) throws IOException, InterruptedException {
        Thread t = new Thread(() -> {
            try {
                byte[] buf = Serializer.serialize(data);
                int messageId = data.getMessageId();
                int count = 0;
                while (!receivedAcks.contains(messageId)) {
                    System.out.println("Sending message for the " + ++count + " time");
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
                    socket.send(packet);
                    Thread.sleep(1000); // To avoid message spamming
                }
                receivedAcks.remove(messageId);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        t.start();
    }

    public void unreliableSend(InetAddress address, int port, Data data) throws IOException {
        byte[] buf = Serializer.serialize(data);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        socket.send(packet);
    }

    public Data receive() throws IOException, ClassNotFoundException {
        byte[] buf = new byte[256];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        socket.receive(packet);
        byte[] mockByteArr = new byte[packet.getLength()];

        System.arraycopy(packet.getData(), packet.getOffset(), mockByteArr, 0, packet.getLength());
        Data data = Serializer.deserialize(mockByteArr, Data.class);

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
