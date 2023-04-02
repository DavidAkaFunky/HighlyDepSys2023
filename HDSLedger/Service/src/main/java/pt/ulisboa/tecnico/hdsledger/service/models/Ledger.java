package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestCreate;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestTransfer;

public class Ledger {

    // Store accounts (public key hash -> account)
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public Ledger() { }

    public boolean createAccount(LedgerRequestCreate request) {
        PublicKey publicKey = request.getAccountPubKey();
        String publicKeyHash;
        try {
            publicKeyHash = RSAEncryption.digest(publicKey.toString());
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
        // Put returns null if the key was not present
        return accounts.put(publicKeyHash, new Account(publicKeyHash)) == null;
    }

    public boolean transfer(LedgerRequestTransfer request) {
        BigDecimal amount = request.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;

        String srcHash;
        String destHash;
        try {
            srcHash = RSAEncryption.digest(request.getSourcePubKey().toString());
            destHash = RSAEncryption.digest(request.getDestinationPubKey().toString());
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
        Account srcAccount = accounts.get(srcHash);
        Account destAccount = accounts.get(destHash);
        if (srcAccount == null || destAccount == null || !srcAccount.subtractBalance(amount)) {
            return false;
        }
        destAccount.addBalance(amount);
        return true;
    }

    public void revertTransfer(LedgerRequestTransfer request) {
        String srcHash;
        String destHash;
        try {
            srcHash = RSAEncryption.digest(request.getSourcePubKey().toString());
            destHash = RSAEncryption.digest(request.getDestinationPubKey().toString());
        } catch (NoSuchAlgorithmException e) {
            return;
        }
        BigDecimal amount = request.getAmount();
        Account srcAccount = accounts.get(srcHash);
        Account destAccount = accounts.get(destHash);

        // No need to check if accounts exist or if the balance is enough,
        // since the transfer was already successful
        
        srcAccount.addBalance(amount);
        destAccount.subtractBalance(amount);
    }

    public Account getAccount(String publicKeyHash) {
        return accounts.get(publicKeyHash);
    }

}