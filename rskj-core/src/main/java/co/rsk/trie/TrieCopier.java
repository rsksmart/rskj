/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.ContractDetailsImpl;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Repository;

import java.util.List;

/**
 * Created by ajlopez on 09/03/2018.
 */
public class TrieCopier {
    private TrieCopier() {

    }

    public static void trieStateCopy(TrieStore source, TrieStore target, Keccak256 hash) {
        Trie trie = source.retrieve(hash.getBytes());
        trie.copyTo(target);
    }

    public static void trieStateCopy(TrieStore source, TrieStore target, Blockchain blockchain, int initialHeight) {
        long h = initialHeight;

        List<Block> blocks = blockchain.getBlocksByNumber(h);

        while (!blocks.isEmpty()) {
            for (Block block : blocks) {
                trieStateCopy(source, target, new Keccak256(block.getStateRoot()));
            }

            h++;
            blocks = blockchain.getBlocksByNumber(h);
        }
    }


}
