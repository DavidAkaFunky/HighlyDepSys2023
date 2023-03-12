package pt.ulisboa.tecnico.hdsledger.utilities;

public class NodeConfig {
    private boolean isLeader = false;
    private String hostname = "localhost";
    private String id = "id";
    private int port = 0;

    private int clientPort = 0;

    public boolean isLeader() {
        return isLeader;
    }

    public int getPort() {
        return port;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    protected void setPort(int port) {
        this.port = port;
    }

    public String getHostname() {
        return hostname;
    }

    protected void setHostname(String hostname) {
        this.hostname = hostname;
    }

    protected void setLeader(boolean leader) {
        this.isLeader = leader;
    }

    public int getClientPort() {
        return clientPort;
    }

    protected void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }
}
