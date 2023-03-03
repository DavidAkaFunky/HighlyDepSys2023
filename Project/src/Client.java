import java.io.ByteArrayOutputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Client {

    public static void main(String[] args) {

        String hostname = "localhost";
        int port = 4445;

        try {
            
            InetAddress address = InetAddress.getByName(hostname);
            DatagramSocket socket = new DatagramSocket();

            Mock m = new Mock("test", 123);
            
            byte[] buf = m.serialize();

            DatagramPacket request = new DatagramPacket(buf, buf.length, address, port);
            socket.send(request);
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
