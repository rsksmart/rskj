package co.rsk.net.priority;

import co.rsk.net.MessageTask;
import co.rsk.net.Peer;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.net.message.Message;
import org.ethereum.net.server.Channel;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AvgTimeBetweenMessagesCalculator implements PriorityCalculator {

    private MaxSizeHashMap<Peer, MessageStat> avg;
    private static double ALPHA = 0.10;

    private Lock lock;

    public AvgTimeBetweenMessagesCalculator(CompositeEthereumListener listener) {

        lock = new ReentrantLock();
        avg = new MaxSizeHashMap<>(200, true);

        listener.addListener(new EthereumListenerAdapter() {
            @Override
            public void onRecvMessage(Channel peer, Message message) {
                lock.lock();
                try {
                    if (!avg.containsKey(peer)) {
                        avg.put(peer, new MessageStat(System.currentTimeMillis(),
                                60_000.0));
                        return;
                    }

                    MessageStat stat = avg.get(peer);
                    long currentTime = System.currentTimeMillis();
                    long newDelta = currentTime - stat.lastMessage;
                    double oldAvg = stat.avg;
                    avg.put(peer, new MessageStat(currentTime, oldAvg*(1-ALPHA) + ALPHA*newDelta));
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
            if (!avg.containsKey(m.getSender())) {
                return 60_000.0;
            }
            return avg.get(m.getSender()).avg;
        } finally {
            lock.unlock();
        }
    }

    private class MessageStat {
        public long lastMessage;
        public double avg;

        public MessageStat(long lastMessage, double avg) {
            this.lastMessage = lastMessage;
            this.avg = avg;
        }
    }
}
