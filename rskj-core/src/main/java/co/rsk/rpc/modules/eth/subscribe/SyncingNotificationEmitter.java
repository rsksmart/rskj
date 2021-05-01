package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.rpc.JsonRpcSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SyncingNotificationEmitter {
    private static final Logger logger = LoggerFactory.getLogger(SyncingNotificationEmitter.class);

    private final JsonRpcSerializer jsonRpcSerializer;

    private final Map<SubscriptionId, Channel> subscriptions = new ConcurrentHashMap<>();

    public SyncingNotificationEmitter(Ethereum ethereum, JsonRpcSerializer jsonRpcSerializer) {
        this.jsonRpcSerializer = jsonRpcSerializer;
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onSyncing(boolean isStarted, Map<String, String> status) {
                emit(isStarted,status);
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

    private void emit(boolean isStarted,Map<String, String> status) {
        subscriptions.forEach((SubscriptionId id, Channel channel) -> {
            emit(id, channel, isStarted,status );
        });
    }

    private void emit(SubscriptionId id, Channel channel, boolean isStarted, Map<String, String> status) {

        SyncingNotification syncStatus = new SyncingNotification(isStarted,status);
        EthSubscriptionParams params = new EthSubscriptionParams(id, syncStatus);

        EthSubscriptionNotification request = new EthSubscriptionNotification(params);
        try {
            String msg = jsonRpcSerializer.serializeMessage(request);
            channel.writeAndFlush(new TextWebSocketFrame(msg));
        } catch (JsonProcessingException e) {
            logger.error("Couldn't serialize block header result for notification", e);
        }
    }
}
