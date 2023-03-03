package blockchain;

import utils.Serialization;
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

            Data d = new Data("test", 123);

            byte[] buf = Serialization.serialize(d);

            DatagramPacket request = new DatagramPacket(buf, buf.length, address, port);
            socket.send(request);
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
