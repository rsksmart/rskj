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
package co.rsk.core.bc;

import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.trie.MultiTrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

public class GarbageCollectorTest {
    private CompositeEthereumListener emitter;
    private MultiTrieStore multiTrieStore;
    private BlockStore blockStore;
    private GarbageCollector collector;

    private EthereumListener listener;
    private RepositoryLocator repositoryLocator;

    @BeforeEach
    public void setUp() {
        this.emitter = mock(CompositeEthereumListener.class);
        this.multiTrieStore = mock(MultiTrieStore.class);
        this.blockStore = mock(BlockStore.class);
        this.repositoryLocator = mock(RepositoryLocator.class);
        this.collector = new GarbageCollector(emitter, 7, 3, multiTrieStore, blockStore, repositoryLocator);
        this.collector.start();

        ArgumentCaptor<EthereumListener> argument = ArgumentCaptor.forClass(EthereumListener.class);
        verify(emitter, times(1)).addListener(argument.capture());
        this.listener = argument.getValue();
    }

    @AfterEach
    public void tearDown() {
        verify(emitter, times(0)).removeListener(listener);
        collector.stop();
        verify(emitter, times(1)).removeListener(listener);
    }

    @Test
    public void collectsOnBlocksPerEpochModulo() {
        for (int i = 100; i < 105; i++) {
            Block block = block(i);
            listener.onBestBlock(block, null);
        }

        verify(multiTrieStore, never()).collect(any());

        byte[] stateRoot = new byte[] {0x42, 0x43, 0x02};
        withSnapshotStateRootAtBlockNumber(85, stateRoot);

        Block block = block(105);
        listener.onBestBlock(block, null);
        verify(multiTrieStore).collect(stateRoot);
    }

    @Test
    public void collectsOnBlocksPerEpochModuloAndMinimumOfStatesToKeep() {
        for (int i = 0; i < 21; i++) {
            Block block = block(i);
            listener.onBestBlock(block, null);
        }

        verify(multiTrieStore, never()).collect(any());

        byte[] stateRoot = new byte[] {0x42, 0x43, 0x02};
        withSnapshotStateRootAtBlockNumber(1, stateRoot);

        Block block = block(21);
        listener.onBestBlock(block, null);
        verify(multiTrieStore).collect(stateRoot);
    }

    private void withSnapshotStateRootAtBlockNumber(int i, byte[] stateRoot) {
        Block block = block(i);
        when(blockStore.getChainBlockByNumber(i)).thenReturn(block);
        RepositorySnapshot snapshot = mock(RepositorySnapshot.class);
        when(repositoryLocator.snapshotAt(block.getHeader())).thenReturn(snapshot);
        when(snapshot.getRoot()).thenReturn(stateRoot);
    }

    private Block block(long number) {
        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(number);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(blockHeader);
        return block;
    }
}
