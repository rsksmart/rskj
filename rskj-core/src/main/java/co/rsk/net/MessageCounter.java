package co.rsk.net;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps the counter of messages per peer
 */
public class MessageCounter {

    private Map<NodeID, AtomicInteger> messagesPerNode = new ConcurrentHashMap<>();

    private static final AtomicInteger ZERO = new AtomicInteger(0);
			
    
    public int getValue(Peer sender) {
    	return Optional.ofNullable(messagesPerNode.get(sender.getPeerNodeID())).orElse(ZERO).intValue();
    }
    
    public void increment(Peer sender) {
    	messagesPerNode
    		.computeIfAbsent(sender.getPeerNodeID(), this::createAtomicInteger)
    		.incrementAndGet();
    }
    
    private AtomicInteger createAtomicInteger(NodeID nodeId) {
    	return new AtomicInteger();
    }
    
    public void decrement(Peer sender) {
    	
    	Optional.ofNullable(messagesPerNode.get(sender.getPeerNodeID())).ifPresent(AtomicInteger::decrementAndGet);
    	
    	// if this counter is zero or negative: remove key from map
    	Optional.ofNullable(messagesPerNode.get(sender.getPeerNodeID()))
    		.filter(counter -> counter.get() < 1)
    		.ifPresent(counter -> messagesPerNode.remove(sender.getPeerNodeID()));

    }
    
}
