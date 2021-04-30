package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.rpc.JsonRpcSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                emit(transactions);
            }
        });
    }

    public void subscribe(SubscriptionId subscriptionId, Channel channel) {
        subscriptions.put(subscriptionId, channel);
    }

    private void emit(List<Transaction> transactions) {
        subscriptions.forEach((SubscriptionId id, Channel channel) -> {
            transactions.forEach(t -> emit(id, channel, t.getHash().toJsonString()));
        });
    }

    private void emit(SubscriptionId id, Channel channel, String txHash) {
        EthSubscriptionParams params = new EthSubscriptionParams(id, txHash);
        EthSubscriptionNotification request = new EthSubscriptionNotification(params);
        try {
            String msg = jsonRpcSerializer.serializeMessage(request);
            channel.writeAndFlush(new TextWebSocketFrame(msg));
        } catch (JsonProcessingException e) {
            logger.error("Couldn't serialize block header result for notification", e);
        }
    }
}
