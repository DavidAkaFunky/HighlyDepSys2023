package pt.ulisboa.tecnico.hdsledger.service;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.text.MessageFormat;

import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class MessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());

    private final int quorumSize;
    private final Map<Integer, Map<Integer, List<String>>> bucket = new ConcurrentHashMap<>();

    public MessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        quorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
    }

    /*
     * Add a message to the bucket
     * 
     * @param consensusInstance
     * 
     * @param round
     * 
     * @param value
     */
    public void addMessage(int consensusInstance, int round, String value) {
        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ArrayList<>());
        bucket.get(consensusInstance).get(round).add(value);
    }

    /*
     * A quorum of valid messages is a set of floor[(n+f)/2] + 1 messages that agree
     * on the same consensus instance, round and value.
     * 
     * @param instance
     * 
     * @param round
     */
    public Optional<String> hasValidQuorum(String nodeId, int instance, int round) {

        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<String, Integer>();
        bucket.get(instance).get(round).forEach((String value) -> {
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        int size = frequency.size();

        if (size > 1)
            LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - ALERT: There are {1} different messages, there might be an intrusion!!!!!!", 
                            nodeId, size));

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

}