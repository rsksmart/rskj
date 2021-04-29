package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.rpc.JsonRpcSerializer;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PendingTransactionsNotificationEmitter {

    private final JsonRpcSerializer jsonRpcSerializer;

    private final Map<SubscriptionId, Channel> subscriptions = new ConcurrentHashMap<>();

    public PendingTransactionsNotificationEmitter(Ethereum ethereum, JsonRpcSerializer jsonRpcSerializer) {
        this.jsonRpcSerializer = jsonRpcSerializer;
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onPendingTransactionsReceived(List<Transaction> transactions) {
                emit();
            }
        });
    }

    private void emit() {
        subscriptions.forEach((SubscriptionId id, Channel channel) -> {
            //TODO: HARDCODED FOR TDD
            String msg = "serialized";
            channel.writeAndFlush(new TextWebSocketFrame(msg));
        });
    }

    public void subscribe(SubscriptionId subscriptionId, Channel channel) {
        subscriptions.put(subscriptionId, channel);
    }
}
