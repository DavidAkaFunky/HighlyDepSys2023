package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;

class SimplexLink {

    private NodeConfig sourceNodeConfig;

    private NodeConfig destinationNodeConfig;

    private int sequenceNumber = 0;
    private int lastAckedSeq = 0;

    public NodeConfig getSourceNodeConfig() {
        return sourceNodeConfig;
    }

    public void setSourceNodeConfig(NodeConfig sourceNodeConfig) {
        this.sourceNodeConfig = sourceNodeConfig;
    }

    public NodeConfig getDestinationNodeConfig() {
        return destinationNodeConfig;
    }

    public void setDestinationNodeConfig(NodeConfig destinationNodeConfig) {
        this.destinationNodeConfig = destinationNodeConfig;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public int getLastAckedSeq() {
        return lastAckedSeq;
    }

    private int unsafeSeqInc() {
        sequenceNumber++;
        return sequenceNumber;
    }

    private int unsafeAckInc() {
        lastAckedSeq++;
        return lastAckedSeq;
    }

    public int stampMessage() {
        synchronized (this) {
            return unsafeSeqInc();
        }
    }

    public int tryUpdateSeq(int seq) {
        synchronized (this) {
            return seq == (sequenceNumber + 1) ? unsafeSeqInc() : sequenceNumber;
        }
    }
    
    public int tryUpdateAck(int ack) {
        synchronized (this) {
            return ack == (lastAckedSeq + 1) ? unsafeAckInc() : lastAckedSeq;
        }
    }
}

public class SimplexLinkBuilder {
    private final SimplexLink instance = new SimplexLink();

    public SimplexLinkBuilder setSourceNodeConfig(NodeConfig nodeConfig) {
        instance.setSourceNodeConfig(nodeConfig);
        return this;
    }

    public SimplexLinkBuilder setDestinationNodeConfig(NodeConfig nodeConfig) {
        instance.setDestinationNodeConfig(nodeConfig);
        return this;
    }

    public SimplexLink build() {
        return instance;
    }

}
