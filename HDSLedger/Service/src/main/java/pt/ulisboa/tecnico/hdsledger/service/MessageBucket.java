package pt.ulisboa.tecnico.hdsledger.service;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class MessageBucket {

    private final int quorumSize;
    private final Map<Integer, Map<Integer, List<String>>> bucket = new ConcurrentHashMap<>();

    public MessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        // TODO verify
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
    public Optional<String> hasValidQuorum(int instance, int round) {

        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<String, Integer>();
        bucket.get(instance).get(round).forEach((String value) -> {
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

}