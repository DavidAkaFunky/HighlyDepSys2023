package blockchain;

import utils.Serialization;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

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

            Data mock = Serialization.unserialize(mockByteArr, Data.class);
            System.out.println(mock);

            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
