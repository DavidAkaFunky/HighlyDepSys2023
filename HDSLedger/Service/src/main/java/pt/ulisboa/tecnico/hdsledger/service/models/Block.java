package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.tecnico.hdsledger.communication.LedgerRequest;

public class Block {

    // Consensus instance 
    private int consensusInstance;
    // List of (ordered) transactions
    private List<LedgerRequest> requests = new ArrayList<>();
    // Hash of the previous block
    // private String previousHash;
    
    public Block() { }

    public void addRequest(LedgerRequest request) {
        requests.add(request);
    }

    public List<LedgerRequest> getRequests() {
        return requests;
    }

    public void setRequests(List<LedgerRequest> requests) {
        this.requests = requests;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }

	public String getHash() {
		return consensusInstance + requests.toString();
	}

    @Override
    public String toString() {
        return "Block [consensusInstance=" + consensusInstance + ", requests=" + requests + "]";
    }

    @Override
    public boolean equals(Object o){
        if (o == this) {
            return true;
        }
        if (!(o instanceof Block)) {
            return false;
        }
        Block block = (Block) o;
        return block.getConsensusInstance() == this.consensusInstance && block.getRequests().equals(this.requests);
    }
}
