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

import co.rsk.core.bc.BlockFork;
import co.rsk.core.bc.BlockchainBranchComparator;
import co.rsk.rpc.JsonRpcSerializer;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.rpc.AddressesTopicsFilter;
import org.ethereum.vm.LogInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class LogsNotificationEmitter {
    private static final Logger logger = LoggerFactory.getLogger(LogsNotificationEmitter.class);

    private final JsonRpcSerializer jsonRpcSerializer;
    private final ReceiptStore receiptStore;
    private final BlockchainBranchComparator branchComparator;

    private final Map<SubscriptionId, Subscription> subscriptions = new ConcurrentHashMap<>();
    private Block lastEmitted;

    public LogsNotificationEmitter(
            Ethereum ethereum,
            JsonRpcSerializer jsonRpcSerializer,
            ReceiptStore receiptStore,
            BlockchainBranchComparator branchComparator) {
        this.jsonRpcSerializer = jsonRpcSerializer;
        this.receiptStore = receiptStore;
        this.branchComparator = branchComparator;
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
                emitLogs(block);
            }
        });
    }

    public void subscribe(SubscriptionId subscriptionId, Channel channel, EthSubscribeLogsParams params) {
        subscriptions.put(subscriptionId, new Subscription(channel, params));
    }

    public boolean unsubscribe(SubscriptionId subscriptionId) {
        return subscriptions.remove(subscriptionId) != null;
    }

    public void unsubscribe(Channel channel) {
        subscriptions.values().removeIf(s -> channel.equals(s.channel));
    }

    private void emitLogs(Block block) {
        if (subscriptions.isEmpty()) {
            return;
        }

        if (lastEmitted == null) {
            emitLogs(getLogsNotifications(block, false));
        } else {
            BlockFork blockFork = branchComparator.calculateFork(lastEmitted, block);
            for (Block oldBlock : blockFork.getOldBlocks()) {
                emitLogs(getLogsNotifications(oldBlock, true));
            }

            for (Block newBlock : blockFork.getNewBlocks()) {
                emitLogs(getLogsNotifications(newBlock, false));
            }
        }

        lastEmitted = block;
    }

    private void emitLogs(List<LogsNotification> notifications) {
        for (Map.Entry<SubscriptionId, Subscription> entry : subscriptions.entrySet()) {
            SubscriptionId id = entry.getKey();
            Channel channel = entry.getValue().channel;
            AddressesTopicsFilter filter = entry.getValue().filter;

            for (LogsNotification notification : notifications) {
                if (filter.matchesExactly(notification.getLogInfo())) {
                    EthSubscriptionNotification request = new EthSubscriptionNotification(
                            new EthSubscriptionParams(id, notification)
                    );

                    try {
                        String msg = jsonRpcSerializer.serializeMessage(request);
                        channel.write(new TextWebSocketFrame(msg));
                    } catch (IOException e) {
                        logger.error("Couldn't serialize block header result for notification", e);
                    }
                }
            }

            channel.flush();
        }
    }

    private List<LogsNotification> getLogsNotifications(Block block, boolean removed) {
        List<LogsNotification> notifications = new ArrayList<>();
        for (int transactionIndex = 0; transactionIndex < block.getTransactionsList().size(); transactionIndex++) {
            Transaction transaction = block.getTransactionsList().get(transactionIndex);
            Optional<TransactionInfo> transactionInfoOpt = receiptStore.get(transaction.getHash().getBytes(), block.getHash().getBytes());
            if (!transactionInfoOpt.isPresent()) {
                logger.error("Missing receipt for transaction {} in block {}", transaction.getHash(), block.getHash());
                continue;
            }

            TransactionInfo transactionInfo = transactionInfoOpt.get();
            TransactionReceipt receipt = transactionInfo.getReceipt();
            List<LogInfo> logInfoList = receipt.getLogInfoList();
            for (int logIndex = 0; logIndex < logInfoList.size(); logIndex++) {
                LogInfo logInfo = logInfoList.get(logIndex);
                LogsNotification notification = new LogsNotification(
                        logInfo, block, transactionIndex, transaction, logIndex, removed);
                notifications.add(notification);
            }
        }

        return notifications;
    }

    private static class Subscription {
        private final Channel channel;
        private final AddressesTopicsFilter filter;

        private Subscription(Channel channel, EthSubscribeLogsParams params) {
            this.channel = channel;
            this.filter = new AddressesTopicsFilter(params.getAddresses(), params.getTopics());
        }
    }
}
