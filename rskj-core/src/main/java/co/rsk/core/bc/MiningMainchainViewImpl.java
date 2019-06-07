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
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;

import javax.annotation.concurrent.GuardedBy;
import java.util.*;

public class MiningMainchainViewImpl implements MiningMainchainView {
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
        fillInternalStore(blockStore.getBestBlock().getHeader());
    }

    public void addBest(BlockHeader bestHeader) {
        synchronized (internalBlockStoreReadWriteLock) {
            addHeaderToMaps(bestHeader);

            fillInternalStore(bestHeader);
            deleteEntriesOutOfBoundaries(bestHeader.getNumber());
        }
    }

    @Override
    public List<BlockHeader> get() {
        synchronized (internalBlockStoreReadWriteLock) {
            return Collections.unmodifiableList(mainchain);
        }
    }

    private void fillInternalStore(BlockHeader bestHeader) {
        List<BlockHeader> newHeaderChain = new ArrayList<>(height);
        BlockHeader currentHeader = bestHeader;
        for(int i = 0; i < height; i++) {
            newHeaderChain.add(currentHeader);

            if(!blocksByHash.containsKey(currentHeader.getHash())) {
                addHeaderToMaps(currentHeader);
            }

            if(currentHeader.isGenesis()) {
                break;
            }

            currentHeader = blockStore.getBlockByHash(currentHeader.getParentHash().getBytes()).getHeader();
        }

        mainchain = newHeaderChain;
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
}
