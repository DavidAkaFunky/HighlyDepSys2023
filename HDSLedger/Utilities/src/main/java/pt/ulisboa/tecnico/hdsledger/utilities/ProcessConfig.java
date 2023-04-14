package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {

    private boolean isLeader;

    private String hostname;

    private String id;

    private int port;

    private int clientPort;

    private String publicKeyPath;

    private String privateKeyPath;

    private ByzantineBehavior byzantineBehavior = ByzantineBehavior.NONE;

    public enum ByzantineBehavior {
        NONE("NONE"),
        PASSIVE("PASSIVE"),
        DROP("DROP"),
        FAKE_LEADER("FAKE_LEADER"),
        BAD_BROADCAST("BAD_BROADCAST"),
        BAD_CONSENSUS("BAD_CONSENSUS"),

        GREEDY_CLIENT("GREEDY_CLIENT"),
        SILENT_LEADER("SILENT_LEADER"),
        LANDLORD_LEADER("LANDLORD_LEADER"),
        HANDSY_LEADER("HANDSY_LEADER"),
        DICTATOR_LEADER("DICTATOR_LEADER"),
        FAKE_WEAK("FAKE_WEAK"),
        FORCE_CONSENSUS_READ("FORCE_CONSENSUS_READ"),
        CORRUPT_LEADER("CORRUPT_LEADER");

        String behavior;

        ByzantineBehavior(String s) {
            this.behavior = s;
        }
    }

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

    public void setPort(int port) {
        this.port = port;
    }

    public String getHostname() {
        return hostname;
    }

    protected void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setLeader(boolean leader) {
        this.isLeader = leader;
    }

    public int getClientPort() {
        return clientPort;
    }

    protected void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setByzantineBehavior(ByzantineBehavior byzantineBehavior) {
        this.byzantineBehavior = byzantineBehavior;
    }

    public ByzantineBehavior getByzantineBehavior() {
        return byzantineBehavior;
    }

}
