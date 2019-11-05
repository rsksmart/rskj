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

package co.rsk.net.simples;

import co.rsk.crypto.Keccak256;
import co.rsk.net.BlockNodeInformation;
import co.rsk.net.BlockProcessResult;
import co.rsk.net.BlockProcessor;
import co.rsk.net.Peer;
import co.rsk.net.messages.NewBlockHashesMessage;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.ImportResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class SimpleBlockProcessor implements BlockProcessor {
    public long lastKnownBlockNumber = 0;
    private List<Block> blocks = new ArrayList<Block>();
    private long requestId;
    private byte[] hash;
    private int count;
    private long blockGap = 1000000;

    @Override
    public BlockProcessResult processBlock(Peer sender, Block block) {
        Map<Keccak256, ImportResult> connectionsResult = new HashMap<>();
        this.blocks.add(block);
        connectionsResult.put(block.getHash(), ImportResult.IMPORTED_BEST);
        return new BlockProcessResult(false, connectionsResult, block.getShortHash(), Duration.ZERO);
    }

    @Override
    public void processGetBlock(Peer sender, byte[] hash) {

    }

    @Override
    public boolean isAdvancedBlock(long number) {
        return number >= this.blockGap;
    }

    public void setBlockGap(long gap) {
        this.blockGap = gap;
    }

    @Override
    public void processBlockRequest(Peer sender, long requestId, byte[] hash) {
        this.requestId = requestId;
        this.hash = hash;
        this.count = count;
    }

    @Override
    public void processBlockHeadersRequest(Peer sender, long requestId, byte[] hash, int count) {
        this.requestId = requestId;
        this.hash = hash;
    }

    @Override
    public void processBodyRequest(Peer sender, long requestId, byte[] hash) {
    }

    @Override
    public void processBlockHashRequest(Peer sender, long requestId, long height) {
    }

    @Override
    public BlockNodeInformation getNodeInformation() {
        return null;
    }

    public List<Block> getBlocks() {
        return this.blocks;
    }

    public long getLastKnownBlockNumber() {
        return lastKnownBlockNumber;
    }

    @Override
    public void processNewBlockHashesMessage(Peer sender, NewBlockHashesMessage message) {

    }

    @Override
    public void processBlockHeaders(Peer sender, List<BlockHeader> blockHeaders) {

    }

    @Override
    public void processSkeletonRequest(final Peer sender, long requestId, final long startNumber) {

    }

    @Override
    public boolean canBeIgnoredForUnclesRewards(long blockNumber) {
        return false;
    }

    @Override
    public boolean hasBlock(byte[] hash) {
        return false;
    }

    @Override
    public boolean hasBlockInProcessorStore(byte[] hash) {
        return false;
    }

    @Override
    public boolean hasBlockInSomeBlockchain(byte[] hash) {
        return false;
    }

    @Override
    public boolean hasBetterBlockToSync() { return false; }

    public long getRequestId() { return this.requestId; }

    public byte[] getHash() { return this.hash; }
}
