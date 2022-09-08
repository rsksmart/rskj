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

package co.rsk.db;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RepositoryLocatorTest {

    private StateRootHandler stateRootHandler;
    private TrieStore trieStore;
    private RepositoryLocator target;

    @BeforeEach
    public void setup() {
        stateRootHandler = mock(StateRootHandler.class);
        trieStore = mock(TrieStore.class);
        target = new RepositoryLocator(trieStore, stateRootHandler);
    }

    @Test
    public void getsSnapshotFromTranslatedStateRoot() {
        BlockHeader header = mock(BlockHeader.class);
        Keccak256 stateRoot = TestUtils.randomHash();
        when(stateRootHandler.translate(header)).thenReturn(stateRoot);

        Trie underlyingTrie = mock(Trie.class);
        when(underlyingTrie.getHash()).thenReturn(TestUtils.randomHash());
        when(trieStore.retrieve(stateRoot.getBytes())).thenReturn(Optional.of(underlyingTrie));

        RepositorySnapshot actualRepository = target.snapshotAt(header);
        assertEquals(underlyingTrie.getHash(), new Keccak256(actualRepository.getRoot()));
    }

    @Test
    public void findSnapshotAt_notFound() {
        BlockHeader header = mock(BlockHeader.class);
        Keccak256 stateRoot = TestUtils.randomHash();
        when(stateRootHandler.translate(header)).thenReturn(stateRoot);

        Trie underlyingTrie = mock(Trie.class);
        when(underlyingTrie.getHash()).thenReturn(TestUtils.randomHash());
        when(trieStore.retrieve(stateRoot.getBytes())).thenReturn(Optional.empty());

        Optional<RepositorySnapshot> result = target.findSnapshotAt(header);

        assertFalse(result.isPresent());
    }

    @Test
    public void findSnapshotAt_found() {
        BlockHeader header = mock(BlockHeader.class);
        Keccak256 stateRoot = TestUtils.randomHash();
        when(stateRootHandler.translate(header)).thenReturn(stateRoot);

        Trie underlyingTrie = mock(Trie.class);
        when(underlyingTrie.getHash()).thenReturn(TestUtils.randomHash());
        when(trieStore.retrieve(stateRoot.getBytes())).thenReturn(Optional.of(mock(Trie.class)));

        Optional<RepositorySnapshot> result = target.findSnapshotAt(header);
        assertTrue(result.isPresent());
    }
}
