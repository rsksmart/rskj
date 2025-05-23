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

package co.rsk.trie;

import java.util.Optional;

public interface TrieStore {
    void save(Trie trie);

    void flush();

    /**
     * @param hash the root of the {@link Trie} to retrieve
     * @return an optional containing the {@link Trie} with <code>rootHash</code> if found
     */
    Optional<Trie> retrieve(byte[] hash);
    byte[] retrieveValue(byte[] hash);

    void dispose();

    Optional<TrieDTO> retrieveDTO(byte[] hash);
    void saveDTO(TrieDTO trieDTO);
}
