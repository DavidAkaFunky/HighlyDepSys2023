package blockchain;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.io.IOException;
import utils.Serializer;

public class PerfectLink {
    
    private int nodeId;
    private DatagramSocket socket;
    private HashMap<Integer, Entry<InetAddress, Integer>> nodes = new HashMap<Integer, Entry<InetAddress, Integer>>();
    private ArrayList<Integer> receivedAcks = new ArrayList<Integer>();

    public PerfectLink(String address, int port, int nodeId) throws UnknownHostException, SocketException {
        this.socket = new DatagramSocket(port, InetAddress.getByName(address));
        this.nodeId = nodeId;
    }

    public void broadcast(Data data) {
        nodes.forEach((nodeId, node) -> {
            try {
                send(nodeId, node.getKey(), node.getValue(), data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void send(int nodeId, InetAddress address, int port, Data data) throws IOException {
        byte[] buf = Serializer.serialize(data);
        int messageId = data.getMessageId();
        while (!receivedAcks.contains(messageId)) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
            socket.send(packet);
        }
        receivedAcks.remove(messageId);
    }

    public Data receive() throws IOException, ClassNotFoundException {
        byte[] buf = new byte[256];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        socket.receive(packet);
        byte[] mockByteArr = new byte[packet.getLength()];

        System.arraycopy(packet.getData(), packet.getOffset(), mockByteArr, 0, packet.getLength());
        Data data = Serializer.deserialize(mockByteArr, Data.class);
        receivedAcks.add(data.getMessageId());

        return data;
    }

}
