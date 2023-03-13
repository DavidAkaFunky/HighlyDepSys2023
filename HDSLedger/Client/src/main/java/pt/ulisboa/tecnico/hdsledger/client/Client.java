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

    public static void main(String[] args) {

        final String clientId = args[0];

        final Scanner scanner = new Scanner(System.in);

        ClientConfig[] configs = new ClientConfigBuilder().fromFile(configPath);

        Optional<ClientConfig> clientConfig = Arrays.stream(configs).filter(c -> c.getId().equals(clientId)).findFirst();

        if (clientConfig.isEmpty())
            throw new LedgerException(ErrorMessage.ConfigFileFormat);

        ClientConfig config = clientConfig.get();

        final Library library = new Library(config);

        while (true) {
            System.out.printf("%n> ");
            String line = scanner.nextLine();

            // Empty command
            if (line.trim().length() == 0) {
                System.out.println();
                continue;
            }

            String[] tokens = line.split(" ");

            switch (tokens[0]) {
                case "write" -> {
                    if (tokens.length == 2) {
                        System.out.println("Writing " + tokens[1] + " to blockchain...");
                        List<String> blockchainValues = library.append(tokens[1]);
                        library.printNewBlockchainValues(blockchainValues);
                    } else {
                        System.err.println("Wrong number of arguments (1 required).");
                    }
                }
                case "read" -> {
                    System.out.println("Reading blockchain...");
                    //library.read();
                    library.printBlockchain();
                }
                case "exit" -> {
                    System.out.println("Exiting...");
                    scanner.close();
                    System.exit(0);
                }
                default -> {
                    System.err.println("Unrecognized command:" + line);
                }
            }
        }
    }
}
