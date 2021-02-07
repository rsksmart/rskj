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
package co.rsk.cli.tools;

import co.rsk.RskContext;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;

import java.io.PrintStream;
import java.util.Optional;

/**
 * The entry point for rewind blocks state CLI tool
 * This is an experimental/unsupported tool
 */
public class RewindBlocks {
    public static void main(String[] args) {
        execute(args, new RskContext(args).getBlockStore());
    }

    public static void execute(String[] args, BlockStore blockStore) {
        long blockNumber = Long.parseLong(args[0]);

        if (blockStore.getMaxNumber() > blockNumber) {
            blockStore.rewind(blockNumber);
        }
    }
}
