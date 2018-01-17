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

import co.rsk.crypto.Sha3Hash;
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

    public static boolean blockInSomeBlockChain(Sha3Hash blockHash, long blockNumber, Blockchain blockChain) {
        final List<BlockInformation> blocks = blockChain.getBlocksInformationByNumber(blockNumber);

        return blocks.stream().anyMatch(bi -> blockHash.equals(bi.getHash()));
    }

    public static Set<Sha3Hash> unknownDirectAncestorsHashes(Block block, Blockchain blockChain, BlockStore store) {
        Set<Sha3Hash> hashes = new HashSet<>();

        hashes.add(block.getParentHash());

        return unknownAncestorsHashes(hashes, blockChain, store, false);
    }

    public static Set<Sha3Hash> unknownAncestorsHashes(Sha3Hash blockHash, Blockchain blockChain, BlockStore store) {
        Set<Sha3Hash> hashes = new HashSet<>();
        hashes.add(blockHash);

        return unknownAncestorsHashes(hashes, blockChain, store, true);
    }

    public static Set<Sha3Hash> unknownAncestorsHashes(Set<Sha3Hash> hashesToProcess, Blockchain blockChain, BlockStore store, boolean withUncles) {
        Set<Sha3Hash> unknown = new HashSet<>();
        Set<Sha3Hash> hashes = hashesToProcess;

        while (!hashes.isEmpty()) {
            hashes = getNextHashes(hashes, unknown, blockChain, store, withUncles);
        }

        return unknown;
    }

    private static Set<Sha3Hash> getNextHashes(Set<Sha3Hash> previousHashes, Set<Sha3Hash> unknown, Blockchain blockChain, BlockStore store, boolean withUncles) {
        Set<Sha3Hash> nextHashes = new HashSet<>();
        for (Sha3Hash hash : previousHashes) {
            if (unknown.contains(hash)) {
                continue;
            }

            Block block = blockChain.getBlockByHash(hash);
            if (block == null) {
                block = store.getBlockByHash(hash);
            }

            if (block == null) {
                unknown.add(hash);
                continue;
            }

            if (!block.isGenesis() && !blockInSomeBlockChain(block, blockChain)) {
                nextHashes.add(block.getParentHash());

                if (withUncles) {
                    for (BlockHeader uncleHeader : block.getUncleList()) {
                        nextHashes.add(uncleHeader.getHash());
                    }
                }
            }
        }
        return nextHashes;
    }

    public static void addBlockToList(List<Block> blocks, Block block) {
        byte[] hash = block.getHash().getBytes();

        for (Block b : blocks) {
            if (Arrays.equals(b.getHash().getBytes(), hash)) {
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
