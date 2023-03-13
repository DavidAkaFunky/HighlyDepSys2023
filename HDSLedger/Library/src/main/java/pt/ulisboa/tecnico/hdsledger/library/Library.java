package pt.ulisboa.tecnico.hdsledger.library;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.communication.SignedMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.stream.Collectors;

public class Library {

    private static final CustomLogger LOGGER = new CustomLogger(Library.class.getName());
    private static final String CONFIG = "../Service/src/main/resources/server_config.json";

    // Config details of node leader
    private final NodeConfig leader;
    // Known blockchain
    private final List<String> blockchain = new ArrayList<>();
    // Client identifier
    private final ClientConfig config;
    // Client socket
    private final DatagramSocket socket;
    // Message sequence number
    private int clientSeq = 1;

    public Library(ClientConfig clientConfig) throws LedgerException {
        try {
            this.socket = new DatagramSocket();
        } catch (SocketException e) {
            throw new LedgerException(ErrorMessage.CannotOpenSocket);
        }
        this.config = clientConfig;
        // Get leader from config file
        Optional<NodeConfig> leader = Arrays.stream(new NodeConfigBuilder().fromFile(CONFIG))
                .filter(NodeConfig::isLeader).findFirst();
        if (leader.isEmpty())
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        this.leader = leader.get();
    }

    public List<String> getBlockchain() {
        return blockchain;
    }

    /*
     * Print the known blockchain content
     */
    public void printBlockchain() {
        System.out.println("Known blockchain content: " + getBlockchain().values());
    }

    /*
     * Print the new blockchain content
     *
     * @param blockchainValues the new blockchain content
     */
    public void printNewBlockchainValues(List<String> blockchainValues) {
        System.out.println("New blockchain content: " + blockchainValues);
    }

    /*
     * Append a value to the blockchain
     * This method is intentionally blocking
     *
     * @param value the value to be appended
     */
    public List<String> append(String value) throws LedgerException {

        // Create message to send to blockchain service
        LedgerRequest request = new LedgerRequest(LedgerRequest.LedgerRequestType.APPEND, this.config.getId(),
                this.clientSeq++, value, this.blockchain.size());

        try {
            Thread sendThread = new Thread(() -> {
                for (; ; ) {
                    try {
                        InetAddress address = InetAddress.getByName(leader.getHostname());

                        int port = leader.getClientPort();

                        // Create UDP packet
                        String jsonString = new Gson().toJson(request);
                        Optional<String> signature;
                        try {
                            signature = Optional.of(RSAEncryption.sign(jsonString, config.getPrivateKeyPath()));
                        } catch (FileNotFoundException e) {
                            throw new LedgerException(ErrorMessage.ConfigFileNotFound);
                        } catch (Exception e) {
                            // sorry DM
                            throw new RuntimeException();
                        }

                        SignedMessage message = new SignedMessage(jsonString, signature.get());
                        byte[] serializedMessage = new Gson().toJson(message).getBytes();
                        DatagramPacket packet = new DatagramPacket(serializedMessage, serializedMessage.length, address, port);
                        // Send packet

                        socket.send(packet);
                        Thread.sleep(1000);
                    } catch (IOException e) {
                        throw new LedgerException(ErrorMessage.SocketSendingError);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            sendThread.start();

            // Receive response
            DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
            socket.receive(response);
            sendThread.interrupt();

            // Deserialize response
            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            SignedMessage responseData = new Gson().fromJson(new String(buffer), SignedMessage.class);
            if (RSAEncryption.verifySignature(responseData.getMessage(), responseData.getSignature(), leader.getPublicKeyPath())) {
                throw new LedgerException(ErrorMessage.SignatureDoesntMatch);
            }

            LedgerResponse ledgerResponse = new Gson().fromJson(responseData.getMessage(), LedgerResponse.class);

            // Add new values to the blockchain
            List<String> blockchainValues = ledgerResponse.getValues();

            blockchain.addAll(ledgerResponse.getValues().stream().toList());

            return blockchainValues;

        } catch (IOException e) {
            throw new LedgerException(ErrorMessage.CannotOpenSocket);
        }
    }

    public List<String> read() throws LedgerException {

        // Create message to send to blockchain service
        LedgerRequest request = new LedgerRequest(LedgerRequest.LedgerRequestType.READ, this.config, this.clientSeq++,
                "",
                this.blockchain.size());

        try {
            Thread sendThread = new Thread(() -> {
                for (; ; ) {
                    try {
                        InetAddress address = InetAddress.getByName(leader.getHostname());

                        int port = leader.getClientPort();

                        // Create UDP packet
                        String jsonString = new Gson().toJson(request);
                        String signature = RSAEncryption.sign(jsonString, pathToKey);
                        SignedMessage message = new SignedMessage(jsonString, signature);
                        DatagramPacket packet = new DatagramPacket(jsonBytes, jsonBytes.length, address, port);

                        // Send packet

                        socket.send(packet);
                        Thread.sleep(1000);
                    } catch (IOException e) {
                        throw new LedgerException(ErrorMessage.SocketSendingError);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            sendThread.start();

            // Receive response
            DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
            socket.receive(response);
            sendThread.interrupt();

            // Deserialize response
            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            LedgerResponse responseData = new Gson().fromJson(new String(buffer), LedgerResponse.class);

            // TODO: Only request values from N+1 instance
            // because we already store the values from N instance
            // Add new values to the blockchain
            List<String> blockchainValues = responseData.getValues();
            int currrentBlockchainSize = blockchain.size();
            for (int i = currrentBlockchainSize + 1; i <= blockchainValues.size(); i++) {
                blockchain.put(i, blockchainValues.get(i - 1));
            }

            return blockchainValues;
        } catch (IOException e) {
            throw new LedgerException(ErrorMessage.CannotOpenSocket);
        }
    }
}