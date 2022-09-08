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

import co.rsk.db.StateRootsStore;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.trie.TrieStore;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

public class BlockChainFlusherTest {
    private CompositeEthereumListener emitter;
    private TrieStore trieStore;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private BlocksBloomStore blocksBloomStore;
    private StateRootsStore stateRootsStore;
    private BlockChainFlusher flusher;

    private EthereumListener listener;

    @BeforeEach
    public void setUp() {
        this.emitter = mock(CompositeEthereumListener.class);
        this.trieStore = mock(TrieStore.class);
        this.blockStore = mock(BlockStore.class);
        this.receiptStore = mock(ReceiptStore.class);
        this.blocksBloomStore = mock(BlocksBloomStore.class);
        this.stateRootsStore = mock(StateRootsStore.class);
        this.flusher = new BlockChainFlusher(7, emitter, trieStore, blockStore, receiptStore, blocksBloomStore, stateRootsStore);
        this.flusher.start();

        ArgumentCaptor<EthereumListener> argument = ArgumentCaptor.forClass(EthereumListener.class);
        verify(emitter, times(1)).addListener(argument.capture());
        this.listener = argument.getValue();
    }

    @Test
    public void flusherStarted_WhenStopped_ShouldUnsubscribeAndFlush() {
        flusher.stop();

        verify(emitter, times(1)).removeListener(listener);

        verify(trieStore).flush();
        verify(blockStore).flush();
        verify(receiptStore).flush();
        verify(blocksBloomStore).flush();
        verify(stateRootsStore).flush();
    }

    @Test
    public void flusherStarted_WhenReceive7Blocks_ShouldFlushStores() {
        for (int i = 0; i < 6; i++) {
            listener.onBestBlock(null, null);
        }

        verify(trieStore, never()).flush();
        verify(blockStore, never()).flush();
        verify(receiptStore, never()).flush();
        verify(blocksBloomStore, never()).flush();
        verify(stateRootsStore, never()).flush();

        listener.onBestBlock(null, null);

        verify(trieStore).flush();
        verify(blockStore).flush();
        verify(receiptStore).flush();
        verify(blocksBloomStore).flush();
        verify(stateRootsStore).flush();
    }
}
