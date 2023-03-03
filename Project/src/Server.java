import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Server {

    public static void main(String[] args) {

        DatagramSocket socket;
        byte[] buf = new byte[256];

        try {
            
            // Receive packet
            socket = new DatagramSocket(4445);
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            byte[] mockByteArr = new byte[packet.getLength()];

            for (int i = 0; i < packet.getLength(); i++) {
                mockByteArr[i] = buf[i];
            }

            Mock mock = Mock.deserialize(mockByteArr);
            System.out.println(mock);

            // Uncomment to get sender's address and port and send response
            // InetAddress address = packet.getAddress();
            // int port = packet.getPort();
            // packet = new DatagramPacket(buf, buf.length, address, port);
            // socket.send(packet);
            
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
