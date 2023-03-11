package pt.ulisboa.tecnico.hdsledger.library;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.LedgerException;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeConfigBuilder;

public class Library {

    private static final Logger LOGGER = Logger.getLogger(Library.class.getName());
    private static final String CONFIG = "src/main/resources/config.txt";
    private NodeConfig[] nodes;
    private PerfectLink link;
    private final String id = "4";
    private int messageId = 1;
    

    public Library(){
        // TODO: Distinguish by server/client
        NodeConfig[] nodes = new NodeConfigBuilder().fromFile(CONFIG);
        // TODO: change this
        Optional<NodeConfig> config = Arrays.stream(nodes).filter(nodeConfig -> nodeConfig.getId().equals(id)).findAny();

        if (config.isEmpty()) throw new LedgerException(ErrorMessage.ConfigFileFormat);

        var nodeConfig = config.get();
        link = new PerfectLink(nodeConfig, nodes);
    }

    public List<String> write(String value) {
        List<String> args = new ArrayList<>();
        args.add(value);
        Message message = new Message(id, messageId++, Type.START, args);
        link.broadcast(message);
        return listen();
    }

    public List<String> read(){
        Message message = new Message(id, messageId++, Type.START);
        link.broadcast(message);
        return listen();
    }

    private List<String> listen() {
        while (true) {
            try {
                Message message = link.receive();
                switch (message.getType()) {
                    case DECIDE -> {
                        LOGGER.log(Level.INFO, "{0} - Received DECIDE message from {1}",
                                new Object[]{id, message.getSenderId()});
                        return message.getArgs(); // Content of the blockchain
                    }

                    case ACK -> {
                        LOGGER.log(Level.INFO, "{0} - Received ACK message from {1}",
                                new Object[]{id, message.getSenderId()});
                        // ignore
                    }
    
                    case IGNORE -> {
                        LOGGER.log(Level.INFO, "{0} - Received IGNORE message from {1}",
                                new Object[]{id, message.getSenderId()});
                        // ignore
                    }
    
                    default -> {
                        LOGGER.log(Level.INFO, "{0} - Received unknown message from {1}",
                                new Object[]{id, message.getSenderId()});
                        // ignore
                    }
                }
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }
        }
    }
}