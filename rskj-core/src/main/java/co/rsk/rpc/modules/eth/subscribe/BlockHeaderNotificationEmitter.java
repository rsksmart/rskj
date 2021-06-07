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

public class BlockHeaderNotificationEmitter {
    private static final Logger logger = LoggerFactory.getLogger(BlockHeaderNotificationEmitter.class);

    private final JsonRpcSerializer jsonRpcSerializer;

    private final Map<SubscriptionId, Channel> subscriptions = new ConcurrentHashMap<>();

    public BlockHeaderNotificationEmitter(Ethereum ethereum, JsonRpcSerializer jsonRpcSerializer) {
        this.jsonRpcSerializer = jsonRpcSerializer;
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(Block block, List<TransactionReceipt> receipts) {
                emitBlockHeader(block);
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

    private void emitBlockHeader(Block block) {
        if (subscriptions.isEmpty()) {
            return;
        }

        BlockHeaderNotification header = new BlockHeaderNotification(block);

        subscriptions.forEach((SubscriptionId id, Channel channel) -> {
            EthSubscriptionNotification<BlockHeaderNotification> request = new EthSubscriptionNotification<>(
                    new EthSubscriptionParams<>(id, header)
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
