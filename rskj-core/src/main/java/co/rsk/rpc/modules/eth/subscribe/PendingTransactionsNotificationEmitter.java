package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.crypto.Keccak256;
import co.rsk.rpc.JsonRpcSerializer;
import co.rsk.util.EncodeUtils;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
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
        byte[] pendingTxsEncoded = EncodeUtils.encodeTransactionList(transactions);
        String pendingTxsHashed = new Keccak256(HashUtil.keccak256(pendingTxsEncoded)).toJsonString();

        subscriptions.forEach((SubscriptionId id, Channel channel) -> {
            EthSubscriptionNotification request = new EthSubscriptionNotification(
                    new EthSubscriptionParams(id, pendingTxsHashed)
            );
            try {
                String msg = jsonRpcSerializer.serializeMessage(request);
                channel.writeAndFlush(new TextWebSocketFrame(msg));
            } catch (IOException e) {
                logger.error("Couldn't serialize block header result for notification", e);
            }
        });
    }
}
