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

package co.rsk.core.bc;

import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MiningMainchainViewImpl implements MiningMainchainView {
    private static final Logger logger = LoggerFactory.getLogger("miningmainchainview");

    private final Object internalBlockStoreReadWriteLock = new Object();

    private final int height;

    private BlockStore blockStore;

    @GuardedBy("internalBlockStoreReadWriteLock")
    private Map<Keccak256, BlockHeader> blocksByHash;

    @GuardedBy("internalBlockStoreReadWriteLock")
    private Map<Long, List<Keccak256>> blockHashesByNumber;

    @GuardedBy("internalBlockStoreReadWriteLock")
    private List<BlockHeader> mainchain;

    public MiningMainchainViewImpl(BlockStore blockStore,
                                   int height) {
        this.height = height;
        this.blockStore = blockStore;
        this.blocksByHash = new HashMap<>();
        this.blockHashesByNumber = new HashMap<>();

        BlockHeader currentBest = blockStore.getBestBlock().getHeader();
        addHeaderToMaps(currentBest);
        buildMainchainFromList(Arrays.asList(currentBest));
    }

    public void addBest(BlockHeader bestHeader) {
        synchronized (internalBlockStoreReadWriteLock) {
            addHeaderToMaps(bestHeader);

            // try to avoid recalculating the whole chain if the new header's parent already exists in the chain
            OptionalInt parentIndex = findParentIndex(bestHeader);
            if (parentIndex.isPresent()) {
                addBestAndRebuildFromParent(bestHeader, parentIndex.getAsInt());
            } else {
                buildMainchainFromList(Arrays.asList(bestHeader));
            }

            deleteEntriesOutOfBoundaries(bestHeader.getNumber());
        }
    }

    @Override
    public List<BlockHeader> get() {
        synchronized (internalBlockStoreReadWriteLock) {
            return Collections.unmodifiableList(mainchain);
        }
    }

    /**
     * Given a new best header and the index of its parent rebuild the mainchain using the index as the pivot point
     * by discarding all headers subsequent to the it, setting the new header as the tip and refilling with as many
     * are needed to complete the desired height
     *
     * @param bestHeader The best header to be on top of the chain
     * @param parentIndex List index of the best header's parent
     */
    private void addBestAndRebuildFromParent(BlockHeader bestHeader, int parentIndex) {
        List<BlockHeader> commonAncestorChain = mainchain.stream()
                .skip(parentIndex)
                .collect(Collectors.toList());

        List<BlockHeader> newMainchain = Stream.concat(
                Arrays.asList(bestHeader).stream(),
                commonAncestorChain.stream())
                .collect(Collectors.toList());

        buildMainchainFromList(newMainchain);
    }

    /**
     * Given a source list take it as the new mainchain and refill it with as many block headers are needed or trim it
     * to reach the desired depth/height
     *
     * @param sourceList
     */
    private void buildMainchainFromList(List<BlockHeader> sourceList) {
        int sourceSize = sourceList.size();

        if (sourceSize == 0) {
            return;
        }

        if (height < sourceSize) {
            mainchain = sourceList.stream()
                    .limit(height)
                    .collect(Collectors.toList());

            return;
        }

        BlockHeader lastHeader = sourceList.get(sourceSize - 1);

        List<BlockHeader> missingHeaders = retrieveAncestorsForHeader(lastHeader, height - sourceSize);

        for (BlockHeader header : missingHeaders) {
            if(!blocksByHash.containsKey(header.getHash())) {
                addHeaderToMaps(header);
            }
        }

        mainchain = Stream.concat(sourceList.stream(), missingHeaders.stream())
                .collect(Collectors.toList());
    }

    /**
     * Given a start block header and a chain length, retrieve a List of said length consisting of
     * the start header's ancestors
     *
     * The returned list DOES NOT include the start header
     *
     * @param header The block header to look the ancestors for
     * @param chainLength The max length of the returned ancestor chain
     */
    private List<BlockHeader> retrieveAncestorsForHeader(BlockHeader header, int chainLength) {
        List<BlockHeader> missingHeaders = new ArrayList<>(chainLength);

        BlockHeader currentHeader = header;
        for(int i = 0; i < chainLength; i++) {

            // genesis has no parent
            if(currentHeader.isGenesis()) {
                break;
            }

            Block nextBlock = blockStore.getBlockByHash(currentHeader.getParentHash().getBytes());
            if (nextBlock == null) {
                logger.error("Missing parent for block {}, number {}", currentHeader.getPrintableHash(), currentHeader.getNumber());
                break;
            }
            currentHeader = nextBlock.getHeader();

            missingHeaders.add(currentHeader);
        }

        return missingHeaders;
    }

    private void addHeaderToMaps(BlockHeader header) {
        blocksByHash.put(header.getHash(), header);
        addToBlockHashesByNumberMap(header);
    }

    private void addToBlockHashesByNumberMap(BlockHeader headerToAdd) {
        long blockNumber = headerToAdd.getNumber();
        if (blockHashesByNumber.containsKey(blockNumber)) {
            blockHashesByNumber.get(blockNumber).add(headerToAdd.getHash());
        } else {
            blockHashesByNumber.put(headerToAdd.getNumber(), new ArrayList<>(Collections.singletonList(headerToAdd.getHash())));
        }
    }

    private void deleteEntriesOutOfBoundaries(long bestBlockNumber) {
        long blocksHeightToDelete = bestBlockNumber - height;
        if(blocksHeightToDelete >= 0 && blockHashesByNumber.containsKey(blocksHeightToDelete)) {
            blockHashesByNumber.get(blocksHeightToDelete).forEach(blockHashToDelete -> blocksByHash.remove(blockHashToDelete));
            blockHashesByNumber.remove(blocksHeightToDelete);
        }
    }

    private OptionalInt findParentIndex(BlockHeader header) {
        for (int i = 0; i < mainchain.size(); i++) {
            BlockHeader chainHeader = mainchain.get(i);
            if (chainHeader.isParentOf(header)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }
}
