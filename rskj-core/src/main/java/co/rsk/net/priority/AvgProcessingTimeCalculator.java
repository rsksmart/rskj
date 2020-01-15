package co.rsk.net.priority;

import co.rsk.net.MessageTask;
import co.rsk.net.Peer;
import co.rsk.net.messages.Message;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;

import java.time.Duration;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AvgProcessingTimeCalculator implements PriorityCalculator {

    private MaxSizeHashMap<Peer, Double> avg;
    private static double ALPHA = 0.10;
    private Lock lock;

    public AvgProcessingTimeCalculator(CompositeEthereumListener listener) {
        avg = new MaxSizeHashMap<>(200, true);
        lock = new ReentrantLock();
        listener.addListener(new EthereumListenerAdapter() {
            @Override
            public void onProcessedMessage(Peer peer, Message message, Duration processingTime) {
                lock.lock();
                try {
                    Double newAvg = processingTime.toNanos()/Math.pow(10, 6);
                    Double oldAvg = avg.getOrDefault(peer, newAvg);
                    avg.put(peer, oldAvg*(1-ALPHA) + newAvg*ALPHA);
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    @Override
    public Double calculate(MessageTask m) {
        lock.lock();
        try {
            return avg.getOrDefault(m.getSender(), 1000.0);
        } finally {
            lock.unlock();
        }
    }
}
