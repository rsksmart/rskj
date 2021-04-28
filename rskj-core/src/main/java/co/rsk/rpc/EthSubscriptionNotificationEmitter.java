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

import co.rsk.rpc.modules.eth.subscribe.*;
import io.netty.channel.Channel;

/**
 * This manages subscriptions and emits events to interested clients.
 * Can only be used with the WebSockets transport.
 */
public class EthSubscriptionNotificationEmitter implements EthSubscribeParamsVisitor {
    private final BlockHeaderNotificationEmitter blockHeader;
    private final LogsNotificationEmitter logs;
    private final PendingTransactionsNotificationEmitter pendingTransactions;
    private final SyncNotificationEmitter sync;

    public EthSubscriptionNotificationEmitter(
            BlockHeaderNotificationEmitter blockHeader,
            LogsNotificationEmitter logs,
            PendingTransactionsNotificationEmitter pendingTransactions,
            SyncNotificationEmitter sync) {
        this.blockHeader = blockHeader;
        this.logs = logs;
        this.pendingTransactions = pendingTransactions;
        this.sync = sync;
    }

    @Override
    public SubscriptionId visit(EthSubscribeNewHeadsParams params, Channel channel) {
        SubscriptionId subscriptionId = new SubscriptionId();
        blockHeader.subscribe(subscriptionId, channel);
        return subscriptionId;
    }

    @Override
    public SubscriptionId visit(EthSubscribeLogsParams params, Channel channel) {
        SubscriptionId subscriptionId = new SubscriptionId();
        logs.subscribe(subscriptionId, channel, params);
        return subscriptionId;
    }

    @Override
    public SubscriptionId visit(EthSubscribePendingTransactionsParams params, Channel channel) {
        SubscriptionId subscriptionId = new SubscriptionId();
        pendingTransactions.subscribe(subscriptionId, channel);
        return subscriptionId;
    }

    @Override
    public SubscriptionId visit(EthSubscribeSyncParams params, Channel channel) {
        SubscriptionId subscriptionId = new SubscriptionId();
        sync.subscribe(subscriptionId, channel);
        return subscriptionId;
    }

    /**
     * @return whether the unsubscription succeeded.
     */
    public boolean unsubscribe(SubscriptionId subscriptionId) {
        // temporal variables avoid short-circuiting behavior
        boolean unsubscribedBlockHeader = blockHeader.unsubscribe(subscriptionId);
        boolean unsubscribedLogs = logs.unsubscribe(subscriptionId);
        boolean unsubscribePendingTransactions = pendingTransactions.unsubscribe(subscriptionId);
        boolean unsubscribeSync = sync.unsubscribe(subscriptionId);
        return unsubscribedBlockHeader || unsubscribedLogs || unsubscribePendingTransactions || unsubscribeSync;
    }

    /**
     * Clear all subscriptions for channel.
     */
    public void unsubscribe(Channel channel) {
        blockHeader.unsubscribe(channel);
        logs.unsubscribe(channel);
        pendingTransactions.unsubscribe(channel);
        sync.unsubscribe(channel);
    }
}
