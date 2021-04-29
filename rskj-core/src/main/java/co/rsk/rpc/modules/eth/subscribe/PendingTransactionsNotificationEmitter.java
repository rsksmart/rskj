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

    private void emit(List<Transaction> transactions) {
        subscriptions.forEach((SubscriptionId id, Channel channel) -> {
            //TODO: HARDCODED FOR TDD
            EthSubscriptionNotification request = new EthSubscriptionNotification(
                    new EthSubscriptionParams(id, new EthSubscriptionNotificationDTO() {
                    })
            );
            try {
                String msg = jsonRpcSerializer.serializeMessage(request);
                channel.writeAndFlush(new TextWebSocketFrame(msg));
            } catch (JsonProcessingException e) {
                logger.error("Couldn't serialize block header result for notification", e);
            }
        });
    }

    public void subscribe(SubscriptionId subscriptionId, Channel channel) {
        subscriptions.put(subscriptionId, channel);
    }
}
