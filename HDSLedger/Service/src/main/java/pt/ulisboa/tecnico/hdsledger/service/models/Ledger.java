package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.utilities.RSAEncryption;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestCreate;
import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequestTransfer;

public class Ledger {

    // Store accounts (public key hash -> account)
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    // Store temporary account
    // When committing its necessary to apply transactions to check if they are
    // valid meaning we need to alter account balances. This state is "merged" with
    // accounts when the consensus is decided.
    private final Map<String, Account> temporaryAccounts = new ConcurrentHashMap<>();

    public Ledger() {
    }

    public Optional<Account> createAccount(int consensusInstance, LedgerRequestCreate request) {
        PublicKey publicKey = request.getAccountPubKey();
        String publicKeyHash;
        try {
            publicKeyHash = RSAEncryption.digest(publicKey.toString());
        } catch (NoSuchAlgorithmException e) {
            return Optional.empty();
        }
        // Put returns null if the key was not present
        Account acc = new Account(consensusInstance, publicKeyHash);
        if (temporaryAccounts.put(publicKeyHash, acc) == null){
            return Optional.of(acc);
        }
        return Optional.empty();
    }

    public void revertCreateAccount(LedgerRequestCreate request) {
        PublicKey publicKey = request.getAccountPubKey();
        String publicKeyHash;
        try {
            publicKeyHash = RSAEncryption.digest(publicKey.toString());
        } catch (NoSuchAlgorithmException e) {
            return;
        }
        // Put returns null if the key was not present
        temporaryAccounts.remove(publicKeyHash);
    }

    public List<Account> transfer(int consensusInstance, LedgerRequestTransfer request) {
        BigDecimal amount = request.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            return new ArrayList<Account>();

        String srcHash;
        String destHash;
        try {
            srcHash = RSAEncryption.digest(request.getSourcePubKey().toString());
            destHash = RSAEncryption.digest(request.getDestinationPubKey().toString());
        } catch (NoSuchAlgorithmException e) {
            return new ArrayList<Account>();
        }
        Account srcAccount = temporaryAccounts.get(srcHash);
        Account destAccount = temporaryAccounts.get(destHash);
        if (srcAccount == null || destAccount == null || !srcAccount.subtractBalance(consensusInstance, amount)) {
            return new ArrayList<Account>();
        }
        destAccount.addBalance(consensusInstance, amount);
        List<Account> accounts = new ArrayList<Account>();
        accounts.add(srcAccount);
        accounts.add(destAccount);
        return accounts;
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
        Account tmpSrcAccount = temporaryAccounts.get(srcHash);
        Account destAccount = accounts.get(destHash);
        Account tmpDestAccount = temporaryAccounts.get(destHash);

        // No need to check if accounts exist or if the balance is enough,
        // since the transfer was already successful

        tmpSrcAccount.addBalance(srcAccount.getMostRecentConsensusInstance(), amount);
        tmpDestAccount.subtractBalance(destAccount.getMostRecentConsensusInstance(), amount);
    }

    public void commitTransactions() {
        temporaryAccounts.forEach((pubKeyHash, tmpAcc) -> {
            accounts.putIfAbsent(pubKeyHash, new Account(tmpAcc.getMostRecentConsensusInstance(), pubKeyHash));
            Account acc = accounts.get(pubKeyHash);
            acc.setBalance(tmpAcc.getBalance());
        });
    }

    public Account getAccount(String publicKeyHash) {
        return accounts.get(publicKeyHash);
    }

}