/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
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
                EthSubscriptionNotification<String> request = getNotification(id, tx);
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
    EthSubscriptionNotification<String> getNotification(SubscriptionId id, Transaction transaction) {
        return new EthSubscriptionNotification<>(
                new EthSubscriptionParams<>(id, transaction.getHash().toJsonString()));
    }
}
