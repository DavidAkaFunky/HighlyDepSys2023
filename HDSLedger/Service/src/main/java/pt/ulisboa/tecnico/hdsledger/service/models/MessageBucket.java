package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.text.MessageFormat;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.UpdateAccount;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class MessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());
    // Quorum size
    private final int quorumSize;
    // Instance -> Round -> Sender ID -> Consensus message
    private final Map<Integer, Map<Integer, Map<String, ConsensusMessage>>> bucket = new ConcurrentHashMap<>();

    public MessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        quorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
    }

    /*
     * Add a message to the bucket
     * 
     * @param consensusInstance
     * 
     * @param message
     */
    public void addMessage(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), message);
    }

    public Optional<Block> hasValidPrepareQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<Block, Integer> frequency = new HashMap<Block, Integer>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            Block block = Block.fromJson(prepareMessage.getBlock());
            frequency.put(block, frequency.getOrDefault(block, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<Block, Integer> entry) -> {
            return entry.getValue() >= quorumSize;
        }).map((Map.Entry<Block, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    /*
     * 
     */
    public Optional<List<ConsensusMessage>> hasValidCommitQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<Collection<UpdateAccount>, List<ConsensusMessage>> messages = new HashMap<Collection<UpdateAccount>, List<ConsensusMessage>>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            CommitMessage commitMessage = message.deserializeCommitMessage();
            Collection<UpdateAccount> updates = commitMessage.getUpdateAccountSignatures().values();
            List<ConsensusMessage> msgs = messages.getOrDefault(updates, new ArrayList<>());
            msgs.add(message);
            messages.put(updates, msgs);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return messages.entrySet().stream().filter((Map.Entry<Collection<UpdateAccount>, List<ConsensusMessage>> entry) -> {
            return entry.getValue().size() >= quorumSize;
        }).map((Map.Entry<Collection<UpdateAccount>, List<ConsensusMessage>> entry) -> {
            return entry.getValue();
        }).findFirst();
    }

    public void verifyReceivedPrepareMessage(Block block, int instance, int round) {
        bucket.get(instance).get(round).values().forEach((message) -> {
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            Block receivedBlock = Block.fromJson(prepareMessage.getBlock());
            if (!receivedBlock.equals(block))
                LOGGER.log(Level.INFO, MessageFormat.format(
                                  "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "@  WARNING: DIFFERENT PREPARE VALUES RECEIVED!  @\n"
                                + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                                + "IT IS POSSIBLE THAT NODE {0} IS DOING SOMETHING NASTY!",
                        message.getSenderId()));
        });
    }

    public Map<String, ConsensusMessage> getMessages(int instance, int round) {
        return bucket.get(instance).get(round);
    }
}