package pt.ulisboa.tecnico.hdsledger.service.models.consensus;

// import java.util.List;

public class Commit extends ConsensusMessage {

    private String leaderSignature;

    // private List<String> replicaSignatures;

    // public Commit(String leaderSignature, List<String> replicaSignatures) {
    //     this.leaderSignature = leaderSignature;
    //     this.replicaSignatures = replicaSignatures;
    // }

    public Commit(String leaderSignature) {
        this.leaderSignature = leaderSignature;
    }

    public String getLeaderSignature() {
        return leaderSignature;
    }

    public void setLeaderSignature(String leaderSignature) {
        this.leaderSignature = leaderSignature;
    }

    // public List<String> getReplicaSignatures() {
    //     return replicaSignatures;
    // }

    // public void setReplicaSignatures(List<String> replicaSignatures) {
    //     this.replicaSignatures = replicaSignatures;
    // }
}
