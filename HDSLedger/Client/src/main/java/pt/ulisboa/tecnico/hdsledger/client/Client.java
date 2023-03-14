package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class Client {

    private final static String configPath = "../Client/src/main/resources/client_config.json";

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

        // Get all the client configs
        ClientConfig[] configs = new ClientConfigBuilder().fromFile(configPath);
        
        // Get the client config
        Optional<ClientConfig> clientConfig = Arrays.stream(configs).filter(c -> c.getId().equals(clientId)).findFirst();
        if (clientConfig.isEmpty()){
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        }
        ClientConfig config = clientConfig.get();
        
        // Library to interact with the blockchain
        final Library library = new Library(config);

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
                    List<String> blockchainValues = library.read();
                    library.printNewBlockchainValues(blockchainValues);
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
