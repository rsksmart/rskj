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

import co.rsk.trie.TrieStore;
import org.ethereum.db.BlockStore;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class BlockChainFlusherTest {
    @Test
    public void flushesAfterNInvocations() {
        TrieStore trieStore = mock(TrieStore.class);
        BlockStore blockStore = mock(BlockStore.class);
        BlockChainFlusher flusher = new BlockChainFlusher(true, 7, trieStore, blockStore);

        for (int i = 0; i < 6; i++) {
            flusher.flush();
        }

        verify(trieStore, never()).flush();
        verify(blockStore, never()).flush();

        flusher.flush();

        verify(trieStore).flush();
        verify(blockStore).flush();
    }
}