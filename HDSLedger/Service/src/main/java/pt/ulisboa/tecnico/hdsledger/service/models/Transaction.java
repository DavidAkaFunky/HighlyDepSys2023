package pt.ulisboa.tecnico.hdsledger.service.models;

import java.math.BigDecimal;

public class Transaction {

    // TODO: Should transaction be generic for any operation
    // read/write in the blockchain?
    // Meaning, creating accounts, moving money and reading
    // balance should all be transaction ?

    // Transaction identifier
    private int id;
    // Account that is sending the money
    private Account source;
    // Account that is receiving the money
    private Account destination;
    // Amount of money to be sent
    private BigDecimal amount;
    // Payed fee
    private BigDecimal fee;
    // Signature of the transaction (Non-Repudiation)
    private String signature;

}
