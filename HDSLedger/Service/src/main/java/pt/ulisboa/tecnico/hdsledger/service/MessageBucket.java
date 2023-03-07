package pt.ulisboa.tecnico.hdsledger.service;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

public class MessageBucket {

    private final int quorumSize;
    private final Map<Integer, Map<Integer, List<Message>>> bucket = new ConcurrentHashMap<>();

    public MessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        // TODO verify
        quorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
    }

    public boolean quorum(int instance, int round, Predicate<Message> pred) {
        bucket.putIfAbsent(instance, new ConcurrentHashMap<>());
        bucket.get(instance).putIfAbsent(round, new ArrayList<>());
        return bucket.get(instance).get(round).stream().filter(m -> pred.test(m)).count() >= quorumSize;
    }

}