package blockchain;

import utils.Serialization;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class Node {

    public static void main(String[] args) {

        // 1 L localhost 3000
        int id = Integer.parseInt(args[0]);
        boolean isLeader = args[1].equals("L");
        String hostname = args[2];
        int port = Integer.parseInt(args[3]);

        try{

            InetAddress address = InetAddress.getByName(hostname);
            DatagramSocket socket = new DatagramSocket(port);

            Thread listen = new Thread(() -> {

                while(true){
                    byte[] buf = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);

                    try{
                        socket.receive(packet);
                        byte[] mockByteArr = new byte[packet.getLength()];

                        for (int i = 0; i < packet.getLength(); i++) {
                            mockByteArr[i] = buf[i];
                        }

                        Data mock = Serialization.unserialize(mockByteArr, Data.class);
                        System.out.println(mock);

                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }

            });

            listen.start();

            Thread send = new Thread(() -> {
                Data d = new Data("test", 123);
                try{
                    byte[] buf = Serialization.serialize(d);

                    DatagramPacket request = new DatagramPacket(buf, buf.length, address, 3000);
                    socket.send(request);

                } catch (Exception e) {
                    System.out.println(e);
                }

            });

            send.start();


        } catch (Exception e){
            System.out.println(e);
        }

        System.out.println("Hello World!");
        System.out.println(Arrays.stream(args).toList());

    }
}
