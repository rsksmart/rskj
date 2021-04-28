package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.rpc.JsonRpcSerializer;
import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PendingTransactionsNotificationEmitter {
    private static final Logger logger = LoggerFactory.getLogger(PendingTransactionsNotificationEmitter.class);

    private final JsonRpcSerializer jsonRpcSerializer;

    private final Map<SubscriptionId, Channel> subscriptions = new ConcurrentHashMap<>();

    public PendingTransactionsNotificationEmitter(Ethereum ethereum, JsonRpcSerializer jsonRpcSerializer) {
        this.jsonRpcSerializer = jsonRpcSerializer;
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onPendingTransactionsReceived(List<Transaction> transactions) {
                emitPendingTransactions(transactions);
            }
        });
    }

    public void subscribe(SubscriptionId subscriptionId, Channel channel) {
        subscriptions.put(subscriptionId, channel);
    }

    public boolean unsubscribe(SubscriptionId subscriptionId) {
        return subscriptions.remove(subscriptionId) != null;
    }

    public void unsubscribe(Channel channel) {
        subscriptions.values().removeIf(channel::equals);
    }

    private void emitPendingTransactions(List<Transaction> transactions) {
        if (subscriptions.isEmpty()) {
            return;
        }
        subscriptions.forEach((SubscriptionId id, Channel channel) -> {
            transactions.forEach(tx -> {
                EthSubscriptionNotification request = getNotification(id, tx);
                try {
                    String msg = jsonRpcSerializer.serializeMessage(request);
                    channel.write(new TextWebSocketFrame(msg));
                } catch (IOException e) {
                    logger.error("Couldn't serialize new pending transactions result for notification", e);
                }
            });
            channel.flush();
        });
    }

    @VisibleForTesting
    EthSubscriptionNotification getNotification(SubscriptionId id, Transaction transaction) {
        return new EthSubscriptionNotification(
                new EthSubscriptionParams(id, transaction.getHash().toJsonString()));
    }
}
