package pt.ulisboa.tecnico.meic.sirs;

import java.io.IOException;
import java.util.Base64;

/**
 * Decode a file with Base64
 */
public class Base64Decode {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("This program decodes a file with base64.");
            System.out.println("Usage: Base64Decode <inputFile> <outputFile>");
            return;
        }

        FileMixer.mix(args[0], args[1], new ByteArrayMixer() {
            @Override
            public byte[] mix(byte[] byteArray1, byte[] byteArray2) {
                return Base64.getDecoder().decode(byteArray1);
            }
        });
    }
}
