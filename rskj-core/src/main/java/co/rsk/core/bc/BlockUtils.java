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

import co.rsk.crypto.Keccak256;
import co.rsk.net.NetBlockStore;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockInformation;
import org.ethereum.vm.GasCost;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 19/08/2016.
 */
public class BlockUtils {
    private static final long MAX_BLOCK_PROCESS_TIME_NANOSECONDS = 60_000_000_000L;
    public static final int SEQUENTIAL_THREAD_COUNT = 1;

    private BlockUtils() {
    }

    public static boolean tooMuchProcessTime(long nanoseconds) {
        return nanoseconds > MAX_BLOCK_PROCESS_TIME_NANOSECONDS;
    }

    public static boolean blockInSomeBlockChain(Block block, Blockchain blockChain) {
        return blockInSomeBlockChain(block.getHash(), block.getNumber(), blockChain);
    }

    private static boolean blockInSomeBlockChain(Keccak256 blockHash, long blockNumber, Blockchain blockChain) {
        final List<BlockInformation> blockInformations = blockChain.getBlocksInformationByNumber(blockNumber);
        return blockInformations.stream().anyMatch(bi -> Arrays.equals(blockHash.getBytes(), bi.getHash()));
    }

    public static Set<Keccak256> unknownDirectAncestorsHashes(Block block, Blockchain blockChain, NetBlockStore store) {
        Set<Keccak256> hashes = Collections.singleton(block.getParentHash());
        return unknownAncestorsHashes(hashes, blockChain, store, false);
    }

    public static Set<Keccak256> unknownAncestorsHashes(Keccak256 blockHash, Blockchain blockChain, NetBlockStore store) {
        Set<Keccak256> hashes = Collections.singleton(blockHash);
        return unknownAncestorsHashes(hashes, blockChain, store, true);
    }

    public static Set<Keccak256> unknownAncestorsHashes(Set<Keccak256> hashesToProcess, Blockchain blockChain, NetBlockStore store, boolean withUncles) {
        Set<Keccak256> unknown = new HashSet<>();
        Set<Keccak256> hashes = hashesToProcess;

        while (!hashes.isEmpty()) {
            hashes = getNextHashes(hashes, unknown, blockChain, store, withUncles);
        }

        return unknown;
    }

    private static Set<Keccak256> getNextHashes(Set<Keccak256> previousHashes, Set<Keccak256> unknown, Blockchain blockChain, NetBlockStore store, boolean withUncles) {
        Set<Keccak256> nextHashes = new HashSet<>();
        for (Keccak256 hash : previousHashes) {
            if (unknown.contains(hash)) {
                continue;
            }

            Block block = blockChain.getBlockByHash(hash.getBytes());
            if (block == null) {
                block = store.getBlockByHash(hash.getBytes());
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
        for (Block b : blocks) {
            if (b.getHash().equals(block.getHash())) {
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

    /**
     * Returns the gas limit assigned to the sequential sublist or to each parallel sublist for a given block.
     *
     * The block gas limit {@code B} is split across {@code T = P + 1} sublists, where {@code P} is the number of
     * parallel execution threads and the extra {@code +1} represents the sequential sublist.
     *
     * Allocation rules:
     * CASE I:(when {@code B/T >= M}) : The block gas limit is big enough to give the minimum M to each sublist.
     *  Each parallel sublist gets an equal share of the block gas limit {@code (B / T)}, with any remainder ({@code B % T}) added to the sequential sublist.
     * CASE II:(when {@code B <= M}: The block gas limit is less than or equal to the minimum required for the sequential list.
     *  Each parallel sublist gets {@code 0} (Parallel execution is completely disabled) and the sequential sublist gets the entire block gas limit {@code B} (entire block).
     * Case III:(when {@code M < B < T * M}: The block gas limit is bigger than the minimum sequential guarantee, but not big enough to give that minimum to every sublist.
     *  Each parallel sublist gets an equal share of the gas remaining {@code (B - M)} after reserving M for sequential and the sequential sublist gets the minimum {@code M}
     *  plus any remainder left after allocating to parallel sublists.
     *
     * WARNING: Assumes B, P, T, and M are strictly positive (> 0).
     *
     * @param block the block providing the total gas limit {@code B}
     * @param isSequentialList {@code true} to return the sequential sublist limit, {@code false} to return the per-parallel-sublist limit
     * @param minSequentialListGasLimit the minimum gas limit reserved for the sequential sublist ({@code M})
     * @return the gas limit for the requested sublist (sequential or per parallel sublist)
     */
    public static long getSublistGasLimit(Block block, boolean isSequentialList, long minSequentialListGasLimit) {
        long B = GasCost.toGas(block.getGasLimit());
        int P = Constants.getTransactionExecutionThreads();
        int T = P + SEQUENTIAL_THREAD_COUNT;
        long M = minSequentialListGasLimit;

        if ( B/T >= M) { // This is the same as (T * M <= B) but avoids long multiplication that can overflow.
            long parallel = B / T; // Does not include the reminder, which is added to the sequential sublist.
            return isSequentialList ? B - (P * parallel) :  parallel;
        }
        if (B <= M) {
            return isSequentialList ? B : 0;
        }
        long parallelListGasLimit = (B - M) / P; // Does not include the reminder, which is added to the sequential sublist.
        long sequentialListGasLimit = B - (parallelListGasLimit * P); // Includes the remainder after dividing the remaining gas across parallel sublists.
        return isSequentialList ? sequentialListGasLimit : parallelListGasLimit;
    }
}
