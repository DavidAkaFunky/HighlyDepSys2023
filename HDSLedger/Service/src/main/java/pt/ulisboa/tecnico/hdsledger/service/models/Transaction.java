package pt.ulisboa.tecnico.hdsledger.service.models;

import java.math.BigDecimal;
import java.security.PublicKey;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;

public class Transaction {

    // Transaction identifier
    private int id;
    // 
    private LedgerRequest request;
    // Paid fee
    private BigDecimal fee;
    // Public key
    private Account blockProducer;
    // Signature of the transaction (Non-Repudiation)
    private String signature;

    
}
