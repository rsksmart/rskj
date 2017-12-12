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

package co.rsk.core;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.PendingState;
import org.ethereum.db.BlockStore;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 15/04/2017.
 */
public class SnapshotManager {
    private List<Long> snapshots = new ArrayList<>();

    public int takeSnapshot(Blockchain blockchain) {
        snapshots.add(Long.valueOf(blockchain.getBestBlock().getNumber()));
        return this.snapshots.size();
    }

    public boolean resetSnapshots(Blockchain blockchain) {
        this.snapshots = new ArrayList<>();

        PendingState pendingState = blockchain.getPendingState();

        long bestNumber = blockchain.getBestBlock().getNumber();

        BlockStore store = blockchain.getBlockStore();

        Block block = store.getChainBlockByNumber(0);
        BigInteger difficulty = blockchain.getBlockStore().getTotalDifficultyForHash(block.getHash());

        blockchain.setStatus(block, difficulty);

        // To clean pending state, first process the fork
        blockchain.getPendingState().processBest(block);
        // then, clear any reverted transaction
        pendingState.clearPendingState(pendingState.getAllPendingTransactions());
        pendingState.clearWire(pendingState.getWireTransactions());

        // Remove removed blocks from store
        for (long nb = blockchain.getBestBlock().getNumber() + 1; nb <= bestNumber; nb++) {
            blockchain.removeBlocksByNumber(nb);
        }

        return true;
    }

    public boolean revertToSnapshot(Blockchain blockchain, int snapshotId) {
        if (snapshotId <= 0 || snapshotId > this.snapshots.size()) {
            return false;
        }

        long newBestBlockNumber = this.snapshots.get(snapshotId - 1).longValue();

        List<Long> newSnapshots = this.snapshots.stream().limit(snapshotId).collect(Collectors.toList());

        this.snapshots = newSnapshots;

        PendingState pendingState = blockchain.getPendingState();

        long currentBestBlockNumber = blockchain.getBestBlock().getNumber();

        if (newBestBlockNumber >= currentBestBlockNumber) {
            return true;
        }

        BlockStore store = blockchain.getBlockStore();

        Block block = store.getChainBlockByNumber(newBestBlockNumber);
        BigInteger difficulty = blockchain.getBlockStore().getTotalDifficultyForHash(block.getHash());

        blockchain.setStatus(block, difficulty);

        // To clean pending state, first process the fork
        blockchain.getPendingState().processBest(block);
        // then, clear any reverted transaction
        pendingState.clearPendingState(pendingState.getAllPendingTransactions());
        pendingState.clearWire(pendingState.getWireTransactions());

        // Remove removed blocks from store
        for (long nb = blockchain.getBestBlock().getNumber() + 1; nb <= currentBestBlockNumber; nb++) {
            blockchain.removeBlocksByNumber(nb);
        }

        return true;
    }

    @VisibleForTesting
    public List<Long> getSnapshots() {
        return this.snapshots;
    }
}
