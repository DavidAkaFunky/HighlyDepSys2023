package pt.ulisboa.tecnico.hdsledger.ledger;

import java.security.PublicKey;

public class Ledger {
    
    // Store blocks (consensus instance -> block)
    private final Map<Integer, Block> blockchain = new ConcurrentHashMap<>();

    // Store accounts (public key hash -> account)
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public Ledger() { }

    public void addBlock(Block block) {
        blockchain.put(block.getConsensusInstance(), block);
    }

    public Block getBlock(int consensusInstance) {
        return blockchain.get(consensusInstance);
    }

    public boolean createAccount(PublicKey publicKey) {
        String publicKeyHash = HashUtils.hash(publicKey.getEncoded());
        if (accounts.containsKey(publicKeyHash)) {
            return false;
        }
        accounts.put(publicKeyHash, new Account(publicKey));
        return true;
    }

    public Account getAccount(String publicKeyHash) {
        return accounts.get(publicKeyHash);
    }

    public boolean addBalance(String publicKeyHash, BigDecimal amount) {
        Account account = accounts.get(publicKeyHash);
        if (account == null) {
            return false;
        }
        return account.addBalance(amount);
    }

    public boolean subtractBalance(String publicKeyHash, BigDecimal amount) {
        Account account = accounts.get(publicKeyHash);
        if (account == null) {
            return false;
        }
        return account.subtractBalance(amount);
    }

}
