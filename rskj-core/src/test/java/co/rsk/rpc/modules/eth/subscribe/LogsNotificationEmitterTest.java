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
package co.rsk.rpc.modules.eth.subscribe;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.ethereum.vm.LogInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.core.JsonProcessingException;

import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockFork;
import co.rsk.core.bc.BlockchainBranchComparator;
import co.rsk.jsonrpc.JsonRpcMessage;
import co.rsk.rpc.JsonRpcSerializer;
import co.rsk.util.HexUtils;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class LogsNotificationEmitterTest {
    private LogsNotificationEmitter emitter;
    private EthereumListener listener;
    private JsonRpcSerializer serializer;
    private ReceiptStore receiptStore;
    private BlockchainBranchComparator comparator;

    @Before
    public void setUp() {
        Ethereum ethereum = mock(Ethereum.class);
        serializer = mock(JsonRpcSerializer.class);
        receiptStore = mock(ReceiptStore.class);
        comparator = mock(BlockchainBranchComparator.class);
        emitter = new LogsNotificationEmitter(ethereum, serializer, receiptStore, comparator);

        ArgumentCaptor<EthereumListener> listenerCaptor = ArgumentCaptor.forClass(EthereumListener.class);
        verify(ethereum).addListener(listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @Test
    public void onBestBlockEventTriggersMessageToChannel() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        EthSubscribeLogsParams params = mock(EthSubscribeLogsParams.class);
        emitter.subscribe(subscriptionId, channel, params);
        when(serializer.serializeMessage(any()))
                .thenReturn("serialized");

        listener.onBestBlock(testBlock(logInfo()), null);

        verify(channel).write(new TextWebSocketFrame("serialized"));
        verify(channel).flush();
    }

    @Test
    public void onBestBlockEventTriggersOneMessageToChannelPerLogInfoAndSubscription() throws JsonProcessingException {
        SubscriptionId subscriptionId1 = mock(SubscriptionId.class);
        SubscriptionId subscriptionId2 = mock(SubscriptionId.class);
        Channel channel1 = mock(Channel.class);
        Channel channel2 = mock(Channel.class);
        EthSubscribeLogsParams params = mock(EthSubscribeLogsParams.class);
        emitter.subscribe(subscriptionId1, channel1, params);
        emitter.subscribe(subscriptionId2, channel2, params);
        when(serializer.serializeMessage(any()))
                .thenReturn("serializedLog1")
                .thenReturn("serializedLog2")
                .thenReturn("serializedLog1")
                .thenReturn("serializedLog2");

        listener.onBestBlock(testBlock(logInfo(), logInfo()), null);

        verify(channel1).write(new TextWebSocketFrame("serializedLog1"));
        verify(channel1).write(new TextWebSocketFrame("serializedLog2"));
        verify(channel1).flush();
        verify(channel2).write(new TextWebSocketFrame("serializedLog1"));
        verify(channel2).write(new TextWebSocketFrame("serializedLog2"));
        verify(channel2).flush();
    }

    @Test
    public void filterEmittedLog() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        EthSubscribeLogsParams params = mock(EthSubscribeLogsParams.class);
        RskAddress logSender = TestUtils.randomAddress();
        when(params.getAddresses()).thenReturn(new RskAddress[] { logSender });
        emitter.subscribe(subscriptionId, channel, params);

        byte[] log1Data = {0x1};
        byte[] log2Data = {0x2};
        Block block1 = testBlock(logInfo(logSender, log1Data));
        Block block2 = testBlock(logInfo(log2Data));

        listener.onBestBlock(block1, null);
        verifyLogsData(log1Data);

        BlockFork blockFork = mock(BlockFork.class);
        when(blockFork.getNewBlocks()).thenReturn(Collections.singletonList(block2));
        when(comparator.calculateFork(block1, block2)).thenReturn(blockFork);

        clearInvocations(channel);
        listener.onBestBlock(block2, null);
        verify(channel, never()).write(any(ByteBufHolder.class));
    }

    @Test
    public void emitsNewAndRemovedLogs() throws JsonProcessingException {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        EthSubscribeLogsParams params = mock(EthSubscribeLogsParams.class);
        emitter.subscribe(subscriptionId, channel, params);

        byte[] log1Data = {0x1};
        byte[] log2Data = {0x2};
        Block block1 = testBlock(logInfo(log1Data));
        Block block2 = testBlock(logInfo(log2Data));

        listener.onBestBlock(block1, null);
        verifyLogsData(log1Data);
        verifyLogsRemovedStatus(false);

        // configure comparator to return a result for a branch that changed the tip from block1 to block2.
        // e.g block1 with number N is connected as best, then block2 with the same number replaces block1 as best.
        BlockFork blockFork = mock(BlockFork.class);
        when(blockFork.getOldBlocks()).thenReturn(Collections.singletonList(block1));
        when(blockFork.getNewBlocks()).thenReturn(Collections.singletonList(block2));
        when(comparator.calculateFork(block1, block2))
                .thenReturn(blockFork);

        listener.onBestBlock(block2, null);
        verifyLogsData(log1Data, log1Data, log2Data);
        verifyLogsRemovedStatus(false, true, false);
    }

    @Test
    public void unsubscribeSucceedsForExistingSubscriptionId() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        EthSubscribeLogsParams params = mock(EthSubscribeLogsParams.class);
        emitter.subscribe(subscriptionId, channel, params);

        assertThat(emitter.unsubscribe(new SubscriptionId()), is(false));
        assertThat(emitter.unsubscribe(subscriptionId), is(true));
    }

    @Test
    public void unsubscribeChannelThenNothingIsEmitted() {
        SubscriptionId subscriptionId = mock(SubscriptionId.class);
        Channel channel = mock(Channel.class);
        EthSubscribeLogsParams params = mock(EthSubscribeLogsParams.class);
        emitter.subscribe(subscriptionId, channel, params);

        emitter.unsubscribe(channel);

        listener.onBestBlock(testBlock(logInfo()), null);
        verifyNoMoreInteractions(channel);
    }

    private void verifyLogsData(byte[]... results) throws JsonProcessingException {
        verifyLogs((ln, d) -> assertThat(ln.getData(), is(HexUtils.toJsonHex(d))), results);
    }

    private void verifyLogsRemovedStatus(Boolean... results) throws JsonProcessingException {
        verifyLogs((ln, r) -> assertThat(ln.getRemoved(), is(r)), results);
    }

    private <T> void verifyLogs(BiConsumer<LogsNotification, T> checker, T... results)
            throws JsonProcessingException {
        ArgumentCaptor<JsonRpcMessage> captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
        verify(serializer, times(results.length)).serializeMessage(captor.capture());
        assertThat(captor.getAllValues(), hasSize(results.length));
        for (int i = 0; i < results.length; i++) {
            EthSubscriptionNotification<LogsNotification> subscriptionNotification = (EthSubscriptionNotification<LogsNotification>) captor.getAllValues().get(i);
            LogsNotification logsNotification = subscriptionNotification.getParams().getResult();
            checker.accept(logsNotification, results[i]);
        }
    }

    private Block testBlock(LogInfo... logInfos) {
        Transaction transaction = transaction();
        Block block = block(transaction);
        withTransactionInfo(block, transaction, logInfos);
        return block;
    }

    private Block block(Transaction transaction) {
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(TestUtils.randomHash());
        when(block.getTransactionsList()).thenReturn(Collections.singletonList(transaction));
        return block;
    }

    private Transaction transaction() {
        Transaction tx = mock(Transaction.class);
        when(tx.getHash()).thenReturn(TestUtils.randomHash());
        return tx;
    }

    private LogInfo logInfo(byte... data) {
        return logInfo(TestUtils.randomAddress(), data);
    }

    private LogInfo logInfo(final RskAddress logSource, byte... data) {
        LogInfo logInfo = mock(LogInfo.class);
        when(logInfo.getAddress()).thenReturn(logSource.getBytes());
        when(logInfo.getData()).thenReturn(data);
        return logInfo;
    }

    private void withTransactionInfo(Block block, Transaction transaction, LogInfo... logInfos) {
        TransactionInfo transactionInfo = mock(TransactionInfo.class);
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(transactionInfo.getReceipt()).thenReturn(receipt);
        when(receipt.getLogInfoList()).thenReturn(Arrays.asList(logInfos));
        when(receiptStore.get(transaction.getHash().getBytes(), block.getHash().getBytes()))
                .thenReturn(Optional.of(transactionInfo));
    }
}