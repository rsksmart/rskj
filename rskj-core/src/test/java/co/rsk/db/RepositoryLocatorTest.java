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
import org.ethereum.TestUtils;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RepositoryLocatorTest {
    @Test
    public void getsSnapshotFromTranslatedStateRoot() {
        BlockHeader header = mock(BlockHeader.class);
        Keccak256 stateRoot = TestUtils.randomHash();
        StateRootHandler stateRootHandler = mock(StateRootHandler.class);
        when(stateRootHandler.translate(header)).thenReturn(stateRoot);

        Trie underlyingTrie = mock(Trie.class);
        when(underlyingTrie.getHash()).thenReturn(TestUtils.randomHash());
        Trie trie = mock(Trie.class);
        when(trie.getSnapshotTo(stateRoot)).thenReturn(underlyingTrie);
        Repository repository = mock(Repository.class);
        when(repository.getTrie()).thenReturn(trie);

        RepositoryLocator repositoryLocator = new RepositoryLocator(repository, stateRootHandler);
        RepositoryReader actualRepository = repositoryLocator.snapshotAt(header);
        assertEquals(underlyingTrie.getHash(), new Keccak256(actualRepository.getRoot()));
    }
}
