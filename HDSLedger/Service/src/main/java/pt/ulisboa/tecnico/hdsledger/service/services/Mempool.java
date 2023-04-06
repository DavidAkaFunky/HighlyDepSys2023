package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

public class Mempool {

    private final Queue<LedgerRequest> pool = new LinkedList<>();

    private final int blocksize;

    public Mempool(int blocksize) {
        this.blocksize = blocksize;
    }

    private Optional<Block> checkTransactionThreshold() {
        synchronized (this.pool) {
            if (this.pool.size() < this.blocksize) return Optional.empty();
            var block = new Block();

            for(int i = 0; i < this.blocksize; i++)
                block.addRequest(this.pool.poll());
           return Optional.of(block);
        }
    }

    public Optional<Block> add(LedgerRequest request) {
        synchronized (this.pool) {
            this.pool.add(request);
        }
        return checkTransactionThreshold();
    }
}
