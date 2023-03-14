package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class Client {

    private final static String clientsConfigPath = "../Client/src/main/resources/client_config.json";
    private final static String nodesConfigPath = "../Service/src/main/resources/server_config.json";

    private static void welcomeText(String clientId) {
        System.out.println("Helcome to the HDS Ledger Client!");
        System.out.println("Your client ID is: " + clientId);
        System.out.println("Type 'write <value>' to write a value to the blockchain.");
        System.out.println("Type 'read' to read the blockchain.");
        System.out.println("Type 'exit' to exit the program.");
    }

    public static void main(String[] args) {

        // Command line arguments
        final String clientId = args[0];

        // Get all the client config
        ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);

        ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
        
        // Get the client config
        Optional<ProcessConfig> clientConfig = Arrays.stream(clientConfigs).filter(c -> c.getId().equals(clientId)).findFirst();
        if (clientConfig.isEmpty()){
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        }
        ProcessConfig config = clientConfig.get();
        
        // Allow the client to connect to the server's correct port
        for (ProcessConfig nodeConfig : nodeConfigs) {
            nodeConfig.setPort(nodeConfig.getClientPort());
        }

        // Library to interact with the blockchain
        final Library library = new Library(config, nodeConfigs);
        library.listen();

        welcomeText(clientId);
        
        final Scanner scanner = new Scanner(System.in);

        String line = "";
        while (true) {
            line = scanner.nextLine();

            // Empty command
            if ((line = line.trim()).length() == 0) {
                continue;
            }

            // TODO: What if string to be happended has spaces?
            String[] tokens = line.split(" ");

            switch (tokens[0]) {
                case "write" -> {
                    if (tokens.length == 2) {
                        System.out.println("Writing " + tokens[1] + " to blockchain...");
                        List<String> blockchainValues = library.append(tokens[1]);
                        library.printNewBlockchainValues(blockchainValues);
                        library.printBlockchain();
                    } else {
                        System.err.println("Wrong number of arguments (1 required).");
                    }
                }
                case "read" -> {
                    System.out.println("Reading blockchain...");
                    library.read();
                    library.printBlockchain();
                }
                case "exit" -> {
                    System.out.println("Exiting...");
                    scanner.close();
                    System.exit(0);
                }
                default -> {
                    System.out.println("Unrecognized command:" + line);
                }
            }
        }
    }
}
