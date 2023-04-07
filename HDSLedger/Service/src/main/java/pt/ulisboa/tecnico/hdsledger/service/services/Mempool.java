package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;

import java.net.PasswordAuthentication;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.logging.Handler;

public class Mempool {

    private final Queue<LedgerRequest> pool = new LinkedList<>();

    private final int blocksize;

    public Mempool(int blocksize) {
        this.blocksize = blocksize;
    }


    /*
     * Check if mempool has enough transactions to create a block
     * Only the leader tries to create blocks
     * Note that the leader will remove transactions from the mempool
     * but other nodes will not
     * They will be removed when the block is added to the blockchain
     */
    private Optional<Block> checkTransactionThreshold() {
        synchronized (this.pool) {
            if (this.pool.size() < this.blocksize) return Optional.empty();
            var block = new Block();

            for(int i = 0; i < this.blocksize; i++)
                block.addRequest(this.pool.poll());
           return Optional.of(block);
        }
    }

    public Queue<LedgerRequest> getInnerPool() {
        return pool;
    }

    public Optional<Block> add(LedgerRequest request) {
        synchronized (this.pool) {
            this.pool.add(request);
        }
        return checkTransactionThreshold();
    }

    public void accept(Consumer<Queue<LedgerRequest>> handler) {
        synchronized (this.pool) {
            handler.accept(this.pool);
        }
    }
}