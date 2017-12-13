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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 19/08/2016.
 */
public class BlockUtils {

    private BlockUtils() { }

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

    public static Set<ByteArrayWrapper> unknownAncestorsHashes(byte[] blockHash, Blockchain blockChain, BlockStore store) {
        Set<ByteArrayWrapper> hashes = new HashSet<>();
        hashes.add(new ByteArrayWrapper(blockHash));

        return unknownAncestorsHashes(hashes, blockChain, store, true);
    }

    public static Set<ByteArrayWrapper> unknownAncestorsHashes(Set<ByteArrayWrapper> hashesToProcess, Blockchain blockChain, BlockStore store, boolean withUncles) {
        Set<ByteArrayWrapper> unknown = new HashSet<>();
        Set<ByteArrayWrapper> hashes = hashesToProcess;

        while (!hashes.isEmpty()) {
            hashes = getNextHashes(hashes, unknown, blockChain, store, withUncles);
        }

        return unknown;
    }

    private static Set<ByteArrayWrapper> getNextHashes(Set<ByteArrayWrapper> previousHashes, Set<ByteArrayWrapper> unknown, Blockchain blockChain, BlockStore store, boolean withUncles) {
        Set<ByteArrayWrapper> nextHashes = new HashSet<>();
        for (ByteArrayWrapper hash : previousHashes) {
            if (unknown.contains(hash)) {
                continue;
            }

            Block block = blockChain.getBlockByHash(hash.getData());
            if (block == null) {
                block = store.getBlockByHash(hash.getData());
            }

            if (block == null) {
                unknown.add(hash);
                continue;
            }

            if (!block.isGenesis() && !blockInSomeBlockChain(block, blockChain)) {
                nextHashes.add(new ByteArrayWrapper(block.getParentHash()));

                if (withUncles) {
                    for (BlockHeader uncleHeader : block.getUncleList()) {
                        nextHashes.add(new ByteArrayWrapper(uncleHeader.getHash()));
                    }
                }
            }
        }
        return nextHashes;
    }

    public static void addBlockToList(List<Block> blocks, Block block) {
        byte[] hash = block.getHash();

        for (Block b : blocks) {
            if (Arrays.equals(b.getHash(), hash)) {
                return;
            }
        }

        blocks.add(block);
    }

    public static List<Block> sortBlocksByNumber(List<Block> blocks) {
        return blocks.stream()
                .sorted(Comparator.comparingLong(Block::getNumber))
                .collect(Collectors.toList());
    }

}
