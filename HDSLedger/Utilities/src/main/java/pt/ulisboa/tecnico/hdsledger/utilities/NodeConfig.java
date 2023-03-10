package pt.ulisboa.tecnico.hdsledger.utilities;

public class NodeConfig {
    private boolean _isLeader = false;
    private String hostname = "localhost";

    private String id = "id";
    private int port = 0;

    public boolean isLeader() {
        return _isLeader;
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
        _isLeader = leader;
    }
}
