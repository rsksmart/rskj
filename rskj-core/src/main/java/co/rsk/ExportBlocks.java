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
package co.rsk;

import co.rsk.core.BlockDifficulty;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;

import java.util.Optional;

/**
 * The entrypoint for export state CLI util
 */
public class ExportBlocks {
    public static void main(String[] args) {
        RskContext ctx = new RskContext(args);
        BlockStore blockStore = ctx.getBlockStore();

        long fromBlockNumber = Long.parseLong(args[0]);
        long toBlockNumber = Long.parseLong(args[1]);

        for (long n = fromBlockNumber; n <= toBlockNumber; n++) {
            Block block = blockStore.getChainBlockByNumber(n);
            BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(block.getHash().getBytes());

            System.out.println(
                block.getNumber() + "," +
                Hex.toHexString(block.getHash().getBytes()) + "," +
                Hex.toHexString(totalDifficulty.getBytes()) + "," +
                Hex.toHexString(block.getEncoded())
            );
        }
    }
}
