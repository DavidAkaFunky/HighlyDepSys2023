package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.library.Library;
import java.util.Scanner;

public class Client {

    public static void main(String[] args){

        final Scanner scanner = new Scanner(System.in);
        final Library library = new Library();

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
