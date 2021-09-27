/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.net.simples;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.net.*;
import co.rsk.net.messages.Message;
import co.rsk.net.sync.PeersInformation;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.*;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.ethereum.util.RskMockFactory;
import org.junit.Assert;

import java.util.concurrent.*;

import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 5/15/2016.
 */
public class SimpleAsyncNode extends SimpleNode {
    private static final TestSystemProperties config = new TestSystemProperties();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinkedBlockingQueue<Future> futures = new LinkedBlockingQueue<>(5000);
    private SyncProcessor syncProcessor;
    private SimpleChannelManager simpleChannelManager;

    public SimpleAsyncNode(MessageHandler handler, Blockchain blockchain) {
        super(handler, blockchain);
    }

    public SimpleAsyncNode(MessageHandler handler,
                           Blockchain blockchain,
                           SyncProcessor syncProcessor,
                           SimpleChannelManager simpleChannelManager) {
        super(handler, blockchain);
        this.syncProcessor = syncProcessor;
        this.simpleChannelManager = simpleChannelManager;
    }

    @Override
    public void receiveMessageFrom(SimpleNode peer, Message message) {
        Peer senderToPeer = simpleChannelManager.getMessageChannel(this, peer);
        futures.add(
                executor.submit(() -> this.getHandler().processMessage(senderToPeer, message)));
    }

    public void joinWithTimeout() {
        try {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            throw new RuntimeException("Join operation timed out. Remaining tasks: " + this.futures.size() + " .");
        }
    }

    public void waitUntilNTasksWithTimeout(int number) {
        try {
            for (int i = 0; i < number; i++) {
                Future task = this.futures.poll(10, TimeUnit.SECONDS);
                if (task == null) {
                    throw new RuntimeException("Exceeded waiting time. Expected " + (number - i) + " more tasks.");
                }
                task.get();
            }
        } catch (ExecutionException ex) {
            ex.printStackTrace();
            Assert.fail();
        } catch (InterruptedException ignored) {
        }
    }

    public void waitExactlyNTasksWithTimeout(int number) {
        waitUntilNTasksWithTimeout(number);
        int remaining = this.futures.size();
        if (remaining > 0)
            throw new RuntimeException("Too many tasks. Expected " + number + " but got " + (number + remaining));
    }

    public void clearQueue() {
        this.futures.clear();
    }

    public SyncProcessor getSyncProcessor() {
        return this.syncProcessor;
    }

    public static SimpleAsyncNode createDefaultNode(Blockchain blockChain){
        return createNode(blockChain, SyncConfiguration.DEFAULT);
    }

    public static SimpleAsyncNode createNode(Blockchain blockchain, SyncConfiguration syncConfiguration) {
        return createNode(blockchain, syncConfiguration, mock(IndexedBlockStore.class));
    }

    public static SimpleAsyncNode createNode(
            Blockchain blockchain,
            SyncConfiguration syncConfiguration,
            org.ethereum.db.BlockStore indexedBlockStore) {
        NetBlockStore blockStore = new NetBlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(config, blockStore, blockchain, nodeInformation, syncConfiguration, DummyBlockValidator.VALID_RESULT_INSTANCE);
        NodeBlockProcessor processor = new NodeBlockProcessor(blockStore, blockchain, nodeInformation, blockSyncService, syncConfiguration);
        DummyBlockValidationRule blockValidationRule = new DummyBlockValidationRule();
        PeerScoringManager peerScoringManager = RskMockFactory.getPeerScoringManager();
        SimpleChannelManager channelManager = new SimpleChannelManager();
        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
        SyncProcessor syncProcessor = new SyncProcessor(
                blockchain, indexedBlockStore, mock(ConsensusValidationMainchainView.class), blockSyncService, syncConfiguration, blockFactory,
                blockValidationRule,
                new SyncBlockValidatorRule(new BlockUnclesHashValidationRule(), new BlockRootValidationRule(config.getActivationConfig())),
                new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants()),
                new PeersInformation(channelManager, syncConfiguration, blockchain, peerScoringManager),
                mock(Genesis.class),
                mock(EthereumListener.class)
        );
        NodeMessageHandler handler = new NodeMessageHandler(config, processor, syncProcessor, channelManager, null, peerScoringManager, mock(StatusResolver.class));

        return new SimpleAsyncNode(handler, blockchain, syncProcessor, channelManager);
    }

    // TODO(mc) find out why the following two work differently

    public static SimpleAsyncNode createNodeWithBlockChainBuilder(int size) {
        final Blockchain blockchain = new BlockChainBuilder().ofSize(0);
        BlockChainBuilder.extend(blockchain, size, false, true);
        return createNode(blockchain, SyncConfiguration.IMMEDIATE_FOR_TESTING, mock(org.ethereum.db.BlockStore.class));
    }

    public static SimpleAsyncNode createNodeWithWorldBlockChain(int size, boolean withUncles, boolean mining) {
        final World world = new World();
        final Blockchain blockchain = world.getBlockChain();
        BlockChainBuilder.extend(blockchain, size, withUncles, mining);
        return createNode(blockchain, SyncConfiguration.IMMEDIATE_FOR_TESTING, world.getBlockStore());
    }
}
