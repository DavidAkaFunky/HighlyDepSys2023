package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestBalance.ConsistencyMode;
import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.Scanner;

public class Client {

    private static String clientsConfigPath = "src/main/resources/";
    private static String nodesConfigPath = "../Service/src/main/resources/";

    private static void welcomeText(String clientId, ProcessConfig.ByzantineBehavior behavior) {
        System.out.println("Welcome to the HDS Ledger Client!");
        System.out.println("Your client ID is: " + clientId);
        System.out.println("Your Byzantine Behavior is: " + behavior);
        System.out.println("Type 'create' to create an account.");
        System.out.println(
                "Type 'transfer <destination_id> <amount>' to transfer a given amount from one account to another.");
        System.out.println("Type 'balance <account_id> <strong|weak>' to read the current account balance.");
        System.out.println("Type 'exit' to exit the program.\n");
    }

    public static void main(String[] args) {

        // Command line arguments
        final String clientId = args[0];
        nodesConfigPath += args[1];
        clientsConfigPath += args[2];
        boolean showDebugLogs = false;
        if (args.length == 4) {
            showDebugLogs = args[3].equals("-debug");
        }

        // Get all the configs
        ProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFile(clientsConfigPath);
        ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);

        // Get the client config
        Optional<ProcessConfig> clientConfig = Arrays.stream(clientConfigs).filter(c -> c.getId().equals(clientId))
                .findFirst();
        if (clientConfig.isEmpty()) {
            throw new LedgerException(ErrorMessage.ConfigFileFormat);
        }
        ProcessConfig config = clientConfig.get();

        // Allow the client to connect to the server's correct port
        for (ProcessConfig nodeConfig : nodeConfigs) {
            nodeConfig.setPort(nodeConfig.getClientPort());
        }

        // Library to interact with the blockchain
        final Library library = new Library(config, nodeConfigs, clientConfigs, showDebugLogs);
        library.listen();

        // Initial text
        welcomeText(clientId, config.getByzantineBehavior());

        final Scanner scanner = new Scanner(System.in);

        String line = "";
        String prompt = String.format("[%s @ HDSLedger]$ ", clientId);
        while (true) {

            System.out.flush();
            System.out.println();
            System.out.print(prompt);
            line = scanner.nextLine();

            // Empty command
            if ((line = line.trim()).length() == 0) {
                continue;
            }

            String[] tokens = line.split(" ");

            switch (tokens[0]) {
                case "create" -> {
                    if (tokens.length != 1) {
                        System.out.println("Invalid number of arguments for create command.");
                        continue;
                    }
                    System.out.println("Creating account...");
                    library.create();
                }
                case "transfer" -> {
                    if (tokens.length != 3) {
                        System.out.println("Invalid number of arguments for transfer command.");
                        continue;
                    }
                    String destinationId = tokens[1];
                    BigDecimal amount = new BigDecimal(tokens[2]);
                    System.out.println("Transferring " + amount + " from " + clientId + " to " + destinationId + "...");
                    library.transfer(clientId, destinationId, amount);
                }
                case "balance" -> {
                    if (tokens.length != 3) {
                        System.out.println("Invalid number of arguments for read command.");
                        continue;
                    }
                    String consistencyMode = tokens[2];
                    if (!consistencyMode.equals("strong") && !consistencyMode.equals("weak")) {
                        System.out.println("Invalid consistency mode. Use 'strong' or 'weak'.");
                        continue;
                    }
                    String accountId = tokens[1];
                    System.out.println("Reading blockchain with " + consistencyMode + " mode...");
                    ConsistencyMode mode = consistencyMode.equals("strong") ? ConsistencyMode.STRONG
                            : ConsistencyMode.WEAK;
                    library.balance(accountId, mode);
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
