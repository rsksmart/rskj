/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.net.eth.LightClientHandler;
import co.rsk.net.light.*;
import co.rsk.validators.ProofOfWorkRule;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.server.Channel;
import org.ethereum.vm.DataWord;

import static org.mockito.Mockito.*;

public class LightClientTestUtils {
    private final MessageQueue messageQueue;
    private final Blockchain blockchain;
    private final BlockStore blockStore;
    private final LightSyncProcessor lightSyncProcessor;
    private final SystemProperties config;
    private final RepositoryLocator repositoryLocator;
    private final LightProcessor lightProcessor;
    private final LightMessageHandler lightMessageHandler;
    private final LightClientHandler.Factory factory;
    private final RepositorySnapshot repositorySnapshot;

    public LightClientTestUtils() {
        messageQueue = mock(MessageQueue.class);
        blockchain = mock(Blockchain.class);
        blockStore = mock(BlockStore.class);
        config = mock(SystemProperties.class);
        repositoryLocator = mock(RepositoryLocator.class);
        repositorySnapshot = mock(RepositorySnapshot.class);
        Genesis genesis = mock(Genesis.class);
        lightProcessor = new LightProcessor(blockchain, blockStore, repositoryLocator);
        ProofOfWorkRule proofOfWorkRule = mock(ProofOfWorkRule.class);
        LightPeersInformation lightPeersInformation = mock(LightPeersInformation.class);
        lightSyncProcessor = new LightSyncProcessor(config, genesis, blockStore, blockchain, proofOfWorkRule, lightPeersInformation);
        lightMessageHandler = new LightMessageHandler(lightProcessor, lightSyncProcessor);
        factory = (lightPeer) -> new LightClientHandler(lightPeer, lightSyncProcessor, lightMessageHandler);
    }

    private void includeBlockInBlockchain(Block block) {
        when(blockchain.getBlockByNumber(block.getNumber())).thenReturn(block);
        when(blockchain.getBlockByHash(block.getHash().getBytes())).thenReturn(block);
    }

    public MessageQueue getMessageQueue() {
        return messageQueue;
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public BlockStore getBlockStore() {
        return blockStore;
    }

    public LightSyncProcessor getLightSyncProcessor() {
        return lightSyncProcessor;
    }

    public SystemProperties getConfig() {
        return config;
    }

    public RepositoryLocator getRepositoryLocator() {
        return repositoryLocator;
    }

    public LightProcessor getLightProcessor() {
        return lightProcessor;
    }

    /**
     * Its created as a spy so we can check if the message was sent
     **/
    public LightPeer createPeer() {
        return spy(new LightPeer(mock(Channel.class), messageQueue));
    }

    public LightClientHandler generateLightClientHandler(LightPeer lightPeer) {
        return factory.newInstance(lightPeer);
    }

    public ChannelHandlerContext hookLightLCHandlerToCtx(LightClientHandler lightClientHandler) {
        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(lightClientHandler);
        return ch.pipeline().firstContext();
    }

    /**
     * Creates an Account in a given block, with all its data
     **/
    public void includeAccount(Keccak256 blockHash, byte[] addressBytes, AccountState accountState,
                               Keccak256 codeHash, byte[] storageRoot) {
        Block block = mock(Block.class);
        RskAddress address = new RskAddress(DataWord.valueOf(addressBytes));

        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(block.getHash()).thenReturn(blockHash);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(repositorySnapshot);
        when(repositorySnapshot.getAccountState(address)).thenReturn(accountState);

        when(repositorySnapshot.getCodeHash(address)).thenReturn(codeHash);
        when(repositorySnapshot.getRoot()).thenReturn(storageRoot);
    }
}
