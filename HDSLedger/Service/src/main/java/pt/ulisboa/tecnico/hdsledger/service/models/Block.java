package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.List;

public class Block {

    // Consensus instance 
    // private int consensusInstance;
    // Hash of the previous block
    // private String previousHash;
    // List of (ordered) transactions
    private List<Transaction> transactions = new ArrayList<>();

    public Block() { }
    
    public void appendBlock(Transaction transaction) {
        transactions.add(transaction);
    }
}
