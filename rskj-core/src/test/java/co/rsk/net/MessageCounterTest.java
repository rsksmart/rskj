package co.rsk.net;

import org.junit.Assert;
import org.junit.Test;

import co.rsk.net.simples.SimplePeer;


public class MessageCounterTest {

	
	@Test
	public void decrement_toBelowOne_thenRemoveKey() {
	
		SimplePeer sender = new SimplePeer(new NodeID(new byte[] {1}));
		
		MessageCounter counter = new MessageCounter();
		
		counter.increment(sender);
		counter.decrement(sender);
		
		Assert.assertFalse(counter.hasCounter(sender));
		
	}
	
}
