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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 19/08/2016.
 */
public class BlockUtils {
    private static final long MAX_BLOCK_PROCESS_TIME_NANOSECONDS = 60_000_000_000L;

    private BlockUtils() { }

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
     * Calculate the gas limit of a sublist, depending on the sublist type (sequential and parallel), from the block
     * gas limit. The distribution can be performed one of two ways:
     *
     * 1. The block gas limit is divided equally among all sublists. If the division was not even (results in a decimal
     * number) then the extra gas limit gets added to the sequential sublist.
     *
     * 2. The sequential sublist gets assigned a fixed value, determined by minSequentialSetGasLimit and additional
     * gas limit is calculated by subtracting minSequentialSetGasLimit form block gas limit, the result is then divided
     * by the amount of transaction execution threads. If the division for the parallel sublists was not even (results
     * in a decimal number) then the extra gas limit gets added to the sequential sublist.
     *
     *
     * @param block                         the block to get the gas limit from
     * @param forSequentialTxSet            a boolean the indicates if the gas limit beign calculated is for a sequential
     *                                      sublist or a paralle one.
     * @param minSequentialSetGasLimit      The minimum gas limit value the sequential sublist can have, configured by
     *                                      network in {@link Constants}.
     *
     * @return set of ancestors block hashes
     */
    public static long getSublistGasLimit(Block block, boolean forSequentialTxSet, long minSequentialSetGasLimit) {
        long blockGasLimit = GasCost.toGas(block.getGasLimit());
        int transactionExecutionThreadCount = Constants.getTransactionExecutionThreads();

        /*
        This if determines which distribution approach will be performed. If the result of multiplying the minSequentialSetGasLimit
        by transactionExecutionThreadCount + 1 (where transactionExecutionThreadCount is the parallel sublist count and
        the + 1 represents the sequential sublist) is less than the block gas limit then the equal division approach is performed,
        otherwise the second approach, where the parallel sublists get less gas limit than the sequential sublist, is executed.
         */
        if((transactionExecutionThreadCount + 1) * minSequentialSetGasLimit <= blockGasLimit) {
            long parallelTxSetGasLimit = blockGasLimit / (transactionExecutionThreadCount + 1);

            if (forSequentialTxSet) {
                /*
                Subtract the total parallel sublist gas limit (parallelTxSetGasLimit) from the block gas limit to get
                the sequential sublist gas limit + the possible extra gas limit and return it.
                 */
                return blockGasLimit - (transactionExecutionThreadCount * parallelTxSetGasLimit);
            }

            return parallelTxSetGasLimit;
        } else {
            // Check if the block gas limit is less than the sequential gas limit.
            if (blockGasLimit <= minSequentialSetGasLimit) {
                /*
                If this method execution is for a sequential sublist then return the total block gas limit. This will
                skip the parallel sublist gas limit calculation since there will not be any gas limit left.
                 */
                if (forSequentialTxSet) {
                    return blockGasLimit;
                }

                // If this method execution is NOT for a sequential sublist then return 0.
                return 0;
            }

            long additionalGasLimit = (blockGasLimit - minSequentialSetGasLimit);
            long parallelTxSetGasLimit = additionalGasLimit / (transactionExecutionThreadCount);

            /*
            If this method execution is for a sequential sublist then calculate the possible extra gas limit by subtracting
            the total parallel sublist gas limit (parallelTxSetGasLimit) from additionalGasLimit and add the result to
            minSequentialSetGasLimit
             */
            if (forSequentialTxSet) {
                long extraGasLimit = additionalGasLimit - (parallelTxSetGasLimit * transactionExecutionThreadCount);
                return minSequentialSetGasLimit + extraGasLimit;
            }
            return parallelTxSetGasLimit;
        }
    }
}
