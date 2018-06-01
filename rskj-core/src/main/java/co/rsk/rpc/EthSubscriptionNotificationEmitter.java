/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.rpc;

import co.rsk.rpc.modules.eth.subscribe.BlockHeaderNotification;
import co.rsk.rpc.modules.eth.subscribe.EthSubscriptionNotification;
import co.rsk.rpc.modules.eth.subscribe.EthSubscriptionParams;
import co.rsk.rpc.modules.eth.subscribe.SubscriptionId;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This manages subscriptions and emits events to interested clients.
 * Can only be used with the WebSockets transport.
 */
public class EthSubscriptionNotificationEmitter {
    private static final Logger LOGGER = LoggerFactory.getLogger(EthSubscriptionNotificationEmitter.class);

    private final Map<SubscriptionId, Channel> subscriptions = new ConcurrentHashMap<>();
    private final JsonRpcSerializer jsonRpcSerializer;

    public EthSubscriptionNotificationEmitter(Ethereum ethereum, JsonRpcSerializer jsonRpcSerializer) {
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(Block block, List<TransactionReceipt> receipts) {
                emit(block);
            }
        });
        this.jsonRpcSerializer = jsonRpcSerializer;
    }

    /**
     * @param channel a Netty channel to subscribe notifications to.
     * @return a subscription id which should be used as an unsubscribe parameter.
     */
    public SubscriptionId subscribe(Channel channel) {
        SubscriptionId subscriptionId = new SubscriptionId();
        subscriptions.put(subscriptionId, channel);
        return subscriptionId;
    }

    /**
     * @return whether the unsubscription succeeded.
     */
    public boolean unsubscribe(SubscriptionId subscriptionId) {
        return subscriptions.remove(subscriptionId) != null;
    }

    /**
     * Clear all subscriptions for channel.
     */
    public void unsubscribe(Channel channel) {
        subscriptions.values().removeIf(channel::equals);
    }

    private void emit(Block block) {
        BlockHeaderNotification header = new BlockHeaderNotification(block);

        subscriptions.forEach((SubscriptionId id, Channel channel) -> {
            EthSubscriptionNotification request = new EthSubscriptionNotification(
                    new EthSubscriptionParams(id, header)
            );

            try {
                String msg = jsonRpcSerializer.serializeMessage(request);
                channel.writeAndFlush(new TextWebSocketFrame(msg));
            } catch (IOException e) {
                LOGGER.error("Couldn't serialize block header result for notification", e);
            }
        });
    }
}
