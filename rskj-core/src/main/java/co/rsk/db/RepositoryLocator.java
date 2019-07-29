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
import co.rsk.trie.MutableTrie;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.ethereum.db.MutableRepository;

public class RepositoryLocator {
    private final Repository repository;
    private final StateRootHandler stateRootHandler;

    public RepositoryLocator(
            Repository repository,
            StateRootHandler stateRootHandler) {
        this.repository = repository;
        this.stateRootHandler = stateRootHandler;
    }

    public RepositorySnapshot snapshotAt(BlockHeader header) {
        return new MutableRepository(mutableTrieSnapshotAt(header));
    }

    public Repository startTrackingAt(BlockHeader header) {
        return new MutableRepository(new MutableTrieCache(mutableTrieSnapshotAt(header)));
    }

    private MutableTrie mutableTrieSnapshotAt(BlockHeader header) {
        Keccak256 stateRoot = stateRootHandler.translate(header);
        return new MutableTrieImpl(repository.getTrie().getSnapshotTo(stateRoot));
    }
}
