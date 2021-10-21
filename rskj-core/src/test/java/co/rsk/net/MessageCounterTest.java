package co.rsk.net;

import static org.hamcrest.number.OrderingComparison.greaterThan;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

	@Test
	public void decrement() {

		SimplePeer sender = new SimplePeer(new NodeID(new byte[] {1}));
		
		MessageCounter counter = new MessageCounter();
		
		counter.increment(sender);
		counter.increment(sender);
		
		counter.decrement(sender);
		
		Assert.assertEquals(1, counter.getValue(sender));
	}

	@Test
	public void givenConcurrentCalls_then_expectCorrectCount() throws InterruptedException {

		/**
		 *  this queue will be added with a '+' when the counter is incremented
		 *  and added with a '-' when the counter is decremented
		 */
		ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(3);

		SimplePeer sender = new SimplePeer(new NodeID(new byte[] {1}));

		MessageCounter counter = new MessageCounter();

		Thread inc1 = new Thread(new Incrementer(counter, sender, executor, queue));
		Thread inc2 = new Thread(new Incrementer(counter, sender, executor, queue));
		Thread inc3 = new Thread(new Incrementer(counter, sender, executor, queue));

		inc1.start();
		inc2.start();
		inc3.start();

		inc1.join();
		inc2.join();
		inc3.join();

		executor.shutdown();

		executor.awaitTermination(30, TimeUnit.SECONDS);

		int changeCount = countConcurrency(queue);

		// assert we had more than a 100 proofs of concurrency
		Assert.assertThat(changeCount, greaterThan(100));
		
		// counter must be zero at this point
		Assert.assertEquals(0, counter.getValue(sender));
	}

	/**
	 * process the queue to count how many times we had a change in signal which means
	 * that increment and decrement are been called concurrently
	 * 
	 * ++++++++------+++++-----
	 */
	private int countConcurrency(ConcurrentLinkedQueue<String> queue) {
		int changeCount = 0;
		String lastSignal = null;
		while(!queue.isEmpty()) {
			String signal = queue.poll();
			// skip the first and last interactions
			if(lastSignal != null && signal != null && !signal.equals(lastSignal)) {
				changeCount++;
			}
			lastSignal = signal;
		}
		return changeCount;
	}
	
	private static class Incrementer implements Runnable {

		private MessageCounter counter;
		private SimplePeer sender;
		private ScheduledThreadPoolExecutor executor;
		private ConcurrentLinkedQueue<String> queue;

		public Incrementer(MessageCounter counter, SimplePeer sender, ScheduledThreadPoolExecutor executor, ConcurrentLinkedQueue<String> queue) {
			this.counter = counter;
			this.sender = sender;
			this.executor = executor;
			this.queue = queue;
		}

		@Override
		public void run() {
			
			for(int i = 0; i < 100_000; i++) {
				counter.increment(sender);
				queue.add("+");
				executor.schedule(() -> {
					counter.decrement(sender);
					queue.add("-");
				}, 5, TimeUnit.MILLISECONDS);
			}

		}

	}



}
