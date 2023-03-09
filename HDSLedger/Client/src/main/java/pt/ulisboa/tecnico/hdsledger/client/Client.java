package pt.ulisboa.tecnico.hdsledger.client;

import java.util.Scanner;

public class Client {

    public static void main(String[] args){

        final Scanner scanner = new Scanner(System.in);

        while(true){
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
                    if(tokens.length > 1){
                        System.out.println("Writing to blockchain...");
                        System.out.println(tokens[1]);
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
                    System.out.println("Unrecognized command.");
                    System.out.println(line);
                }    
            }
        }
    }
}
