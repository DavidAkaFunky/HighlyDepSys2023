package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;
import pt.ulisboa.tecnico.hdsledger.service.models.Block;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Timer;
import java.util.function.Consumer;

import com.google.gson.GsonBuilder;

public class Mempool {

    private final Queue<LedgerRequest> pool = new LinkedList<>();
    // Timer for each request
    private final Map<LedgerRequest, Timer> timers = new HashMap<>();

    private final int blocksize;

    public Mempool(int blocksize) {
        this.blocksize = blocksize;
    }

    public Map<LedgerRequest, Timer> getTimers() {
        return timers;
    }

    public void removeRequest(LedgerRequest request) {
        this.pool.remove(request);
        Timer timer = this.timers.remove(request);
        if (timer != null)
            timer.cancel();
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
            if (this.pool.size() < this.blocksize)
                return Optional.empty();
            var block = new Block();

            for (int i = 0; i < this.blocksize; i++){
                LedgerRequest req = this.pool.poll();
                block.addRequest(req);
                Timer timer = this.timers.remove(req);
                if (timer != null)
                    timer.cancel();
            }
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

    public String toString() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(this.pool);
    }
}
