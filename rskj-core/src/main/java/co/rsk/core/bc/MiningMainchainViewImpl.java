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
import org.ethereum.core.Blockchain;

import javax.annotation.concurrent.GuardedBy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MiningMainchainViewImpl implements MiningMainchainView {
    private final Object internalBlockStoreReadWriteLock = new Object();

    private final int height;

    private Blockchain blockchain;

    @GuardedBy("internalBlockStoreReadWriteLock")
    private Map<Keccak256, BlockHeader> blocksByHash;

    @GuardedBy("internalBlockStoreReadWriteLock")
    private Map<Long, List<BlockHeader>> blocksByNumber;

    @GuardedBy("internalBlockStoreReadWriteLock")
    private List<BlockHeader> mainchain;

    public MiningMainchainViewImpl(Blockchain blockchain,
                                   int height) {
        this.height = height;
        this.blockchain = blockchain;
        this.blocksByHash = new HashMap<>();
        this.blocksByNumber = new HashMap<>();
        fillInternalStore(blockchain.getBestBlock().getHeader());
    }

    @Override
    public void addBest(Block bestBlock) {
        addBest(bestBlock.getHeader());
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

    @Override
    public Block getBestBlock() {
        return blockchain.getBestBlock();
    }

    @Override
    public Block getBlockByNumber(long number) {
        return blockchain.getBlockByNumber(number);
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

            currentHeader = blockchain.getBlockByHash(currentHeader.getParentHash().getBytes()).getHeader();
        }

        mainchain = newHeaderChain;
    }

    private void addHeaderToMaps(BlockHeader header) {
        blocksByHash.put(header.getHash(), header);
        addToBlockByNumberMap(header);
    }

    private void addToBlockByNumberMap(BlockHeader headerToAdd) {
        long currentBlockNumber = headerToAdd.getNumber();
        if (blocksByNumber.containsKey(currentBlockNumber)) {
            blocksByNumber.get(currentBlockNumber).add(headerToAdd);
        } else {
            blocksByNumber.put(headerToAdd.getNumber(), new ArrayList<>(Collections.singletonList(headerToAdd)));
        }
    }

    private void deleteEntriesOutOfBoundaries(long bestBlockNumber) {
        long blocksHeightToDelete = bestBlockNumber - height;
        if(blocksHeightToDelete >= 0 && blocksByNumber.containsKey(blocksHeightToDelete)) {
            blocksByNumber.get(blocksHeightToDelete).forEach(blockToDelete -> blocksByHash.remove(blockToDelete.getHash()));
            blocksByNumber.remove(blocksHeightToDelete);
        }
    }
}
