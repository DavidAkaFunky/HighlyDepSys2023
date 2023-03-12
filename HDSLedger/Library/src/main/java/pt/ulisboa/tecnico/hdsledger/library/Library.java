package pt.ulisboa.tecnico.hdsledger.library;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfigBuilder;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;

public class Library {

    private static final Logger LOGGER = Logger.getLogger(Library.class.getName());
    private static final String CONFIG = "../Service/src/main/resources/config.txt";
    private final NodeConfig server;

    public Library() {
        // Get leader from config file
        Optional<NodeConfig> server = Arrays.stream(new NodeConfigBuilder().fromFile(CONFIG)).filter(NodeConfig::isLeader).findFirst();
        if (server.isEmpty()) throw new LedgerException(ErrorMessage.ConfigFileFormat);
        this.server = server.get();
    }

    public LedgerMessage append(String value) throws LedgerException {
        LedgerMessage request = new LedgerMessage();
        request.setType(LedgerMessage.LedgerMessageType.APPEND);
        request.setArg(value);
        for (;;) {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(1000);
                var address = InetAddress.getByName(server.getHostname());
                var port = server.getClientPort();
                String json = new Gson().toJson(request);
                var packet = new DatagramPacket(json.getBytes(), json.getBytes().length, address, port);
                socket.send(packet);
                var response = new DatagramPacket(new byte[1024], 1024);
                socket.receive(response);
                byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
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
        LedgerMessage request = new LedgerMessage();
        request.setType(LedgerMessage.LedgerMessageType.READ);
        for (;;) {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(1000);
                var address = InetAddress.getByName(server.getHostname());
                var port = server.getClientPort();
                String json = new Gson().toJson(request);
                var packet = new DatagramPacket(json.getBytes(), json.getBytes().length, address, port);
                socket.send(packet);
                var response = new DatagramPacket(new byte[1024], 1024);
                socket.receive(response);
                return new Gson().fromJson(new String(response.getData()), LedgerMessage.class); //TODO send message and return real response
            } catch (SocketTimeoutException e) {
                // do nothing, loop
            } catch (IOException e) {
                throw new LedgerException(ErrorMessage.CannotOpenSocket);
            }
        }
    }
}