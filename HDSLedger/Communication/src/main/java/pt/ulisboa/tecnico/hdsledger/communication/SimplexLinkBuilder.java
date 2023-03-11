package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;

class Pair<U,V> {
    private final U first;
    private final V second;

    Pair(U first, V second) {
        this.first = first;
        this.second = second;
    }

    public U getFirst() {
        return first;
    }

    public V getSecond() {
        return second;
    }
}
class SimplexLink {

    private NodeConfig sourceNodeConfig;

    private NodeConfig destinationNodeConfig;

    private int sequenceNumber = 0;


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

    private int unsafeInc() {
        sequenceNumber++;
        return sequenceNumber;
    }

    public int stampMessage() {
        synchronized (this) {
            return unsafeInc();
        }
    }

    public int tryUpdateSeq(int seq) {
        synchronized (this) {
            return seq == (sequenceNumber + 1) ? unsafeInc() : sequenceNumber;
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
