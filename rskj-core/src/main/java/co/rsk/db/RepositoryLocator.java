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
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.util.RLP;

import java.util.Optional;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public class RepositoryLocator {
    // all zeroed, default hash for empty nodes
    private static final Keccak256 EMPTY_HASH = new Keccak256(Keccak256Helper.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));

    private final TrieStore trieStore;
    private final StateRootHandler stateRootHandler;

    public RepositoryLocator(TrieStore store, StateRootHandler stateRootHandler) {
        this.trieStore = store;
        this.stateRootHandler = stateRootHandler;
    }

    /**
     * Similar to getRepositoryAt but retrieves an optional instead of throwing an exception
     * @return an optional {@link RepositorySnapshot}
     */
    public Optional<RepositorySnapshot> findSnapshotAt(BlockHeader header) {
        Keccak256 stateRoot = stateRootHandler.translate(header);

        if (EMPTY_HASH.equals(stateRoot)) {
            return Optional.of(new TopRepository(new Trie(this.trieStore), this.trieStore));
        }

        Optional<Trie> trie = trieStore.retrieve(stateRoot.getBytes());

        return trie.map(t -> new TopRepository(t, this.trieStore));
    }

    /**
     * Retrieves a repository with the state at a particular header
     * @param header the header to retrieve the state from
     * @return a read-only {@link RepositorySnapshot}
     * @throws IllegalArgumentException if the state is not found.
     */
    public TopRepository getRepositoryAt(BlockHeader header) {
        Keccak256 stateRoot = stateRootHandler.translate(header);

        if (EMPTY_HASH.equals(stateRoot)) {
            return new TopRepository(new Trie(this.trieStore), this.trieStore);
        }

        Optional<Trie> trie = trieStore.retrieve(stateRoot.getBytes());

        return trie.map(t -> new TopRepository(t, this.trieStore))
                .orElseThrow(() -> trieNotFoundException(header));
    }

    private IllegalArgumentException trieNotFoundException(BlockHeader header) {
        return new IllegalArgumentException(String.format(
                "The trie with root %s is missing in this store", header.getHash()
        ));
    }
}
