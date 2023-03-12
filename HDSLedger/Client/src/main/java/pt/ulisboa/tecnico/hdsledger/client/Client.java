package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.library.Library;
import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.util.Base64;
import java.util.Scanner;

public class Client {

    public static void main(String[] args) {

        final Scanner scanner = new Scanner(System.in);
        final Library library = new Library();

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
                case "test" -> {
                    try {
                        String testString = "HelloWorld!";
                        String digestBase64 = RSAEncryption.sign(testString, "../PKI/priv.key");
                        System.out.println("Digest encrypted: " + digestBase64);
                        // create message from testString and digestEncrypted
                        String message = testString + " " + digestBase64 ;
                        // send message to server
                        // ...
                        // receive message from server
                        // split message into testString and digestEncrypted
                        String[] messageTokens = message.split(" ");
                        String receivedTestString = messageTokens[0];
                        String receivedDigestEncrypted = messageTokens[1];
                        System.out.println("Received digest: " + receivedDigestEncrypted);
                        // decrypt digestEncrypted
                        boolean valid = RSAEncryption.verifySignature(receivedDigestEncrypted, "../PKI/pub.key");
                        if (valid) {
                            System.out.println("Valid signature!");
                        } else {
                            System.out.println("Invalid signature!");
                        }


                    } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("Error: " + e.getMessage());
                    }
                }
                case "write" -> {
                    if (tokens.length == 2){
                        System.out.println("Writing " + tokens[1] + " to blockchain...");
                        library.append(tokens[1]);
                    } else {
                        System.err.println("Wrong number of arguments (1 required).");
                    }
                }
                case "read" -> {
                    System.out.println("Reading blockchain...");

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
