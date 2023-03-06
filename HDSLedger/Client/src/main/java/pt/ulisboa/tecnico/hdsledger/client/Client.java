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

            switch (line) {
                case "exit" -> {
                    System.out.println("Exiting...");
                    scanner.close();
                    System.exit(0);
                }
                default -> {
                    System.out.println(line);
                    break;
                }    
            }
        }
    }
}
