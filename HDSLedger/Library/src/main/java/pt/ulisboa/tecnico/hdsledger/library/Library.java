package pt.ulisboa.tecnico.hdsledger.library;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfigBuilder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;

public class Library {

    private static final Logger LOGGER = Logger.getLogger(Library.class.getName());
    private static final String CONFIG = "../Service/src/main/resources/config.json";
    private final NodeConfig server;

    public Library() {
        // Get leader from config file
        Optional<NodeConfig> server = Arrays.stream(new NodeConfigBuilder().fromFile(CONFIG))
                .filter(NodeConfig::isLeader).findFirst();
        if (server.isEmpty())
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        this.server = server.get();
    }

    /*
     * Append a value to the blockchain
     * This method is intentionally blocking
     * 
     * @param value the value to be appended
     */
    public LedgerMessage append(String value) throws LedgerException {

        // Create message to send to blockchain service
        LedgerMessage request = new LedgerMessage();
        request.setType(LedgerMessage.LedgerMessageType.APPEND);
        request.setArg(value);

        for (;;) {
            try {

                // Create socket to send and receive message
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(1000);
                InetAddress address = InetAddress.getByName(server.getHostname());
                int port = server.getClientPort();

                // Create UDP packet
                String json = new Gson().toJson(request);
                DatagramPacket packet = new DatagramPacket(json.getBytes(), json.getBytes().length, address, port);

                // Send packet
                socket.send(packet);

                // Receive response
                DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
                socket.receive(response);
                byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());

                socket.close();

                // TODO check this return
                // TODO add a NONCE to this message to be appended to the blockchain
                return new Gson().fromJson(new String(buffer), LedgerMessage.class);

            } catch (SocketTimeoutException e) {
                // do nothing, loop
            } catch (IOException e) {
                throw new LedgerException(ErrorMessage.CannotOpenSocket);
            }
        }
    }

    public LedgerMessage read() throws LedgerException {

        // Create message to send to blockchain service
        LedgerMessage request = new LedgerMessage();
        request.setType(LedgerMessage.LedgerMessageType.READ);

        for (;;) {
            try {

                // Create socket to send and receive message
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(1000);
                InetAddress address = InetAddress.getByName(server.getHostname());
                int port = server.getClientPort();

                // Create UDP packet
                String json = new Gson().toJson(request);
                DatagramPacket packet = new DatagramPacket(json.getBytes(), json.getBytes().length, address, port);

                // Send packet
                socket.send(packet);

                // Receive response
                var response = new DatagramPacket(new byte[1024], 1024);
                socket.receive(response);
                byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());

                socket.close();
                return new Gson().fromJson(new String(buffer), LedgerMessage.class); // TODO send message
                                                                                     // and return real
                                                                                     // response
            } catch (SocketTimeoutException e) {
                // do nothing, loop
            } catch (IOException e) {
                throw new LedgerException(ErrorMessage.CannotOpenSocket);
            }
        }
    }
}