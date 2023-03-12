package pt.ulisboa.tecnico.hdsledger.library;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class Library {

    private static final CustomLogger LOGGER = new CustomLogger(Library.class.getName());
    private static final String CONFIG = "../Service/src/main/resources/config.json";
    private final NodeConfig leader;
    private final Map<Integer, String> blockchain = new ConcurrentHashMap<>();
    private int clientSeq = 1;

    public Library() {
        // Get leader from config file
        Optional<NodeConfig> leader = Arrays.stream(new NodeConfigBuilder().fromFile(CONFIG))
                .filter(NodeConfig::isLeader).findFirst();
        if (leader.isEmpty())
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        this.leader = leader.get();
    }

    public Map<Integer, String> getBlockchain() {
        return blockchain;
    }

    public void printBlockchain() {
        System.out.print("Blockchain content");
        getBlockchain().values().forEach((x) -> System.out.print(" -> " + x));
        System.out.println();
    }

    public void printNewBlockchainValues(List<String> blockchainValues) {
        System.out.print("New blockchain values");
        blockchainValues.forEach((x) -> System.out.print(" -> " + x));
        System.out.println();
    }

    /*
     * Append a value to the blockchain
     * This method is intentionally blocking
     * 
     * @param value the value to be appended
     */
    public List<String> append(String value) throws LedgerException {

        // Create message to send to blockchain service
        LedgerRequest request = new LedgerRequest(LedgerRequest.LedgerRequestType.APPEND, clientSeq++, value, blockchain.size());

        for (;;) {
            try {

                // Create socket to send and receive message
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(1000);
                InetAddress address = InetAddress.getByName(leader.getHostname());
                int port = leader.getClientPort();

                // Create UDP packet
                byte[] jsonBytes = new Gson().toJson(request).getBytes();
                DatagramPacket packet = new DatagramPacket(jsonBytes, jsonBytes.length, address, port);

                // Send packet
                socket.send(packet);

                // Receive response
                DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
                socket.receive(response);
                socket.close();

                byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
                LedgerResponse responseData = new Gson().fromJson(new String(buffer), LedgerResponse.class);
                List<String> blockchainValues = responseData.getValues();
                int currrentBlockchainSize = blockchain.size();
                for (String blockchainValue : blockchainValues) {
                    blockchain.put(++currrentBlockchainSize, blockchainValue);
                }

                return blockchainValues;

            } catch (SocketTimeoutException e) {
                // do nothing, loop
            } catch (IOException e) {
                throw new LedgerException(ErrorMessage.CannotOpenSocket);
            }
        }
    }

    public List<String> read() throws LedgerException {

        // Create message to send to blockchain service
        LedgerRequest request = new LedgerRequest(LedgerRequest.LedgerRequestType.READ, clientSeq++, "", blockchain.size());

        for (;;) {
            try {

                // Create socket to send and receive message
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(1000);
                InetAddress address = InetAddress.getByName(leader.getHostname());
                int port = leader.getClientPort();

                // Create UDP packet
                byte[] jsonBytes = new Gson().toJson(request).getBytes();
                DatagramPacket packet = new DatagramPacket(jsonBytes, jsonBytes.length, address, port);

                // Send packet
                socket.send(packet);

                // Receive response
                DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
                socket.receive(response);
                socket.close();

                byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
                LedgerResponse responseData = new Gson().fromJson(new String(buffer), LedgerResponse.class);
                List<String> blockchainValues = responseData.getValues();
                int currrentBlockchainSize = blockchain.size();

                // Only add new values to the blockchain
                for (int i = currrentBlockchainSize + 1; i <= blockchainValues.size(); i++) {
                    blockchain.put(i, blockchainValues.get(i - 1));
                }

                return blockchainValues;

            } catch (SocketTimeoutException e) {
                // do nothing, loop
            } catch (IOException e) {
                throw new LedgerException(ErrorMessage.CannotOpenSocket);
            }
        }
    }
}