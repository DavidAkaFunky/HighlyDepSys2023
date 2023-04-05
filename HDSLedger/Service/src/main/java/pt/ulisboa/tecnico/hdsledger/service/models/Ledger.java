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

    // Map consensus instance -> public key hash -> signer Id -> account update signature
    private final Map<Integer, Map<String, Map<String, String>>> accountUpdateSignatures = new ConcurrentHashMap<>();

    public Ledger() {
    }

    public void addAccountUpdateSignature(int consensusInstance, String publicKeyHash, String signerId, String signature) {
        accountUpdateSignatures.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        accountUpdateSignatures.get(consensusInstance).putIfAbsent(publicKeyHash, new ConcurrentHashMap<>());
        accountUpdateSignatures.get(consensusInstance).get(publicKeyHash).put(signerId, signature);
    }

    public Map<String, String> getAccountUpdateSignatures(int consensusInstance, String ownerId) {
        return accountUpdateSignatures.get(consensusInstance).get(ownerId);
    }

    public Optional<Account> createAccount(String ownerId, LedgerRequestCreate request) {
        PublicKey publicKey = request.getAccountPubKey();
        String publicKeyHash;
        try {
            publicKeyHash = RSAEncryption.digest(publicKey.toString());
        } catch (NoSuchAlgorithmException e) {
            return Optional.empty();
        }
        // Put returns null if the key was not present
        Account acc = new Account(ownerId, publicKeyHash);
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
            return new ArrayList<>();

        String srcHash;
        String destHash;
        try {
            srcHash = RSAEncryption.digest(request.getSourcePubKey().toString());
            destHash = RSAEncryption.digest(request.getDestinationPubKey().toString());
        } catch (NoSuchAlgorithmException e) {
            return new ArrayList<>();
        }

        Account srcAccount = temporaryAccounts.get(srcHash);
        Account destAccount = temporaryAccounts.get(destHash);
        if (srcAccount == null || destAccount == null || !srcAccount.subtractBalance(amount)) {
            return new ArrayList<>();
        }

        destAccount.addBalance(amount);
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
        temporaryAccounts.forEach((pubKeyHash, tmpAcc) -> {
            UpdateAccount update = accountUpdates.get(consensusInstance).get(pubKeyHash);
            accounts.putIfAbsent(pubKeyHash, new Account(update.getOwnerId(), pubKeyHash));
            Account acc = accounts.get(pubKeyHash);
            acc.updateAccount(update, pubKeyHash);
        });
    }

    public Account getAccount(String publicKeyHash) {
        return accounts.get(publicKeyHash);
    }

    public Account getTemporaryAccount(String publicKeyHash) {
        return temporaryAccounts.get(publicKeyHash);
    }

    public void addAccountUpdate(int consensusInstance, String publicKeyHash, UpdateAccount updateAccount) {
        accountUpdates.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        accountUpdates.get(consensusInstance).put(publicKeyHash, updateAccount);
    }

    public Map<String, UpdateAccount> getAccountUpdates(int consensusInstance) {
        return accountUpdates.get(consensusInstance);
    }

}