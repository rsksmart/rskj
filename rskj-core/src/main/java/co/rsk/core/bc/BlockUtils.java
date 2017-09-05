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

package co.rsk.core.bc;

import co.rsk.net.BlockStore;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by ajlopez on 19/08/2016.
 */
public class BlockUtils {
    private static final Logger logger = LoggerFactory.getLogger("blockprocessor");

    public static boolean blockInSomeBlockChain(Block block, Blockchain blockChain) {
        return blockInSomeBlockChain(block.getHash(), block.getNumber(), blockChain);
    }

    public static boolean blockInSomeBlockChain(byte[] blockHash, long blockNumber, Blockchain blockChain) {
        final ByteArrayWrapper key = new ByteArrayWrapper(blockHash);
        final List<BlockInformation> blocks = blockChain.getBlocksInformationByNumber(blockNumber);

        return blocks.stream().anyMatch(bi -> key.equalsToByteArray(bi.getHash()));
    }

    public static Set<ByteArrayWrapper> unknownDirectAncestorsHashes(Block block, Blockchain blockChain, BlockStore store) {
        Set<ByteArrayWrapper> hashes = new HashSet<>();

        hashes.add(new ByteArrayWrapper(block.getParentHash()));

        return unknownAncestorsHashes(hashes, blockChain, store, false);
    }

    public static Set<ByteArrayWrapper> unknownAncestorsHashes(Block block, Blockchain blockChain, BlockStore store) {
        Set<ByteArrayWrapper> hashes = new HashSet<>();

        hashes.add(new ByteArrayWrapper(block.getParentHash()));

        for (BlockHeader uncleHeader : block.getUncleList()) {
            ByteArrayWrapper uncleHash = new ByteArrayWrapper(uncleHeader.getHash());
            hashes.add(uncleHash);
        }

        return unknownAncestorsHashes(hashes, blockChain, store, true);
    }

    public static Set<ByteArrayWrapper> unknownAncestorsHashes(byte[] blockHash, Blockchain blockChain, BlockStore store) {
        Set<ByteArrayWrapper> hashes = new HashSet<>();
        hashes.add(new ByteArrayWrapper(blockHash));

        return unknownAncestorsHashes(hashes, blockChain, store, true);
    }

    public static Set<ByteArrayWrapper> unknownAncestorsHashes(Set<ByteArrayWrapper> hashesToProcess, Blockchain blockChain, BlockStore store, boolean withUncles) {
        Set<ByteArrayWrapper> unknown = new HashSet<>();
        Set<ByteArrayWrapper> toexpand = new HashSet<>();
        Set<ByteArrayWrapper> hashes = hashesToProcess;

        while (!hashes.isEmpty()) {
            for (ByteArrayWrapper hash : hashes) {
                if (unknown.contains(hash))
                    continue;

                Block block = blockChain.getBlockByHash(hash.getData());

                if (block == null)
                    block = store.getBlockByHash(hash.getData());

                if (block == null)
                    unknown.add(hash);
                else if (!block.isGenesis() && !blockInSomeBlockChain(block, blockChain)) {
                    toexpand.add(new ByteArrayWrapper(block.getParentHash()));

                    if (withUncles)
                        for (BlockHeader uncleHeader : block.getUncleList())
                            toexpand.add(new ByteArrayWrapper(uncleHeader.getHash()));
                }
            }

            hashes = toexpand;
            toexpand = new HashSet<>();
        }

        return unknown;
    }

    public static void addBlockToList(List<Block> blocks, Block block) {
        byte[] hash = block.getHash();

        for (Block b : blocks)
            if (Arrays.equals(b.getHash(), hash))
                return;

        blocks.add(block);
    }

    public static void addBlocksToList(List<Block> blocks, List<Block> newBlocks) {
        for (Block newBlock : newBlocks)
            addBlockToList(blocks, newBlock);
    }

    public static List<Block> sortBlocksByNumber(List<Block> blocks) {
        List<Block> sortedBlocks = new ArrayList<>(blocks);

        Collections.sort(sortedBlocks, new Comparator<Block>() {
            @Override
            public int compare(Block b1, Block b2) {
                return Long.compare(b1.getNumber(), b2.getNumber());
            }
        });

        return sortedBlocks;
    }

}
