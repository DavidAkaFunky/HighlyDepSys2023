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
import pt.ulisboa.tecnico.hdsledger.communication.UpdateAccount;

public class Ledger {

    // Store accounts (public key hash -> account)
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    // Store temporary account
    // When committing its necessary to apply transactions to check if they are
    // valid meaning we need to alter account balances. This state is "merged" with
    // accounts when the consensus is decided.
    private final Map<String, Account> temporaryAccounts = new ConcurrentHashMap<>();
    // Map consensus instance -> public key hash -> account update
    private final Map<Integer, Map<String, UpdateAccount>> accountUpdates = new ConcurrentHashMap<>();
    // Map consensus instance -> public key hash -> signer Id -> account update
    // signature
    private final Map<Integer, Map<String, Map<String, String>>> accountUpdateSignatures = new ConcurrentHashMap<>();

    private final BigDecimal fee = BigDecimal.ONE;

    private Account temporaryLeaderAccount;

    public Ledger(String leaderId, String leaderPublicKeyHash) {
        this.temporaryLeaderAccount = new Account(leaderId, leaderPublicKeyHash);
        this.temporaryAccounts.put(leaderPublicKeyHash, this.temporaryLeaderAccount);
        this.accounts.put(leaderPublicKeyHash, new Account(leaderId, leaderPublicKeyHash));
    }

    public Map<String, Account> getAccounts() {
        return accounts;
    }

    public Map<String, Account> getTemporaryAccounts() {
        return temporaryAccounts;
    }

    public void addAccountUpdateSignature(int consensusInstance, String publicKeyHash, String signerId,
            String signature) {
        accountUpdateSignatures.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        accountUpdateSignatures.get(consensusInstance).putIfAbsent(publicKeyHash, new ConcurrentHashMap<>());
        accountUpdateSignatures.get(consensusInstance).get(publicKeyHash).put(signerId, signature);
    }

    public Map<String, String> getAccountUpdateSignatures(int consensusInstance, String publicKeyHash) {
        return accountUpdateSignatures.get(consensusInstance).get(publicKeyHash);
    }

    public Map<Integer, Map<String, Map<String, String>>> getAccountUpdateSignatures() {
        return accountUpdateSignatures;
    }

    public Optional<Account> createAccount(String ownerId, PublicKey publicKey, PublicKey leaderPublicKey) {
        String publicKeyHash;
        String leaderPublicKeyHash;
        try {
            publicKeyHash = RSAEncryption.digest(publicKey.toString());
            leaderPublicKeyHash = RSAEncryption.digest(leaderPublicKey.toString());
        } catch (NoSuchAlgorithmException e) {
            return Optional.empty();
        }

        // Put returns null if the key was not present
        Account acc = new Account(ownerId, publicKeyHash);
        if (temporaryAccounts.put(publicKeyHash, acc) != null) {
            return Optional.empty();
        }
        
        // Pay leader a fee
        acc.subtractBalance(this.fee);
        
        Account leaderAccount = temporaryAccounts.get(leaderPublicKeyHash);
        leaderAccount.addBalance(this.fee);
        
        return Optional.of(acc);
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

    public List<Account> transfer(
        int consensusInstance, 
        BigDecimal amount,
        PublicKey sourcePubKey,
        PublicKey destinationPubKey,
        PublicKey leaderPubKey) {

        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            return new ArrayList<>();

        String srcHash;
        String destHash;
        String leaderHash;
        try {
            srcHash = RSAEncryption.digest(sourcePubKey.toString());
            destHash = RSAEncryption.digest(destinationPubKey.toString());
            leaderHash = RSAEncryption.digest(leaderPubKey.toString());
        } catch (NoSuchAlgorithmException e) {
            return new ArrayList<>();
        }

        Account srcAccount = temporaryAccounts.get(srcHash);
        Account destAccount = temporaryAccounts.get(destHash);
        Account leaderAccount = temporaryAccounts.get(leaderHash);            
        // include in the subtract the leader fee
        if (srcAccount == null || destAccount == null || !srcAccount.subtractBalance(amount.add(this.fee))) {
            return new ArrayList<>();
        }

        destAccount.addBalance(amount);
        leaderAccount.addBalance(this.fee);
        List<Account> accounts = new ArrayList<>();
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
        Account tmpSrcAccount = temporaryAccounts.get(srcHash);
        Account tmpDestAccount = temporaryAccounts.get(destHash);

        // No need to check if accounts exist or if the balance is enough,
        // since the transfer was already successful

        tmpSrcAccount.addBalance(amount);
        tmpDestAccount.subtractBalance(amount);
    }

    public void commitTransactions(int consensusInstance) {
        this.accountUpdates.get(consensusInstance).forEach((pubKeyHash, update) -> {
            this.accounts.putIfAbsent(pubKeyHash, new Account(update.getOwnerId(), pubKeyHash));
            Account acc = this.accounts.get(pubKeyHash);
            acc.updateAccount(update, pubKeyHash);
        });
    }

    public Account getAccount(String publicKeyHash) {
        return this.accounts.get(publicKeyHash);
    }

    public Account getTemporaryAccount(String publicKeyHash) {
        return this.temporaryAccounts.get(publicKeyHash);
    }

    public void addAccountUpdate(int consensusInstance, String publicKeyHash, UpdateAccount updateAccount) {
        accountUpdates.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        accountUpdates.get(consensusInstance).put(publicKeyHash, updateAccount);
    }

    public Map<String, UpdateAccount> getAccountUpdates(int consensusInstance) {
        return accountUpdates.get(consensusInstance);
    }

    public UpdateAccount getAccountUpdate(int consensusInstance, String publicKeyHash) {
        return this.accountUpdates.get(consensusInstance).get(publicKeyHash);
    }

}