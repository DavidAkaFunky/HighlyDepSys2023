package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.Map;

public class SignedBlock {

    // Block
    private Block block;
    // Signatures (attestation)
    private Map<String, String> signatures;

}