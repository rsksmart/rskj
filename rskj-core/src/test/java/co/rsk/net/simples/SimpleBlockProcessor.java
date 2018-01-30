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

import co.rsk.core.commons.Keccak256;
import co.rsk.net.*;
import co.rsk.net.messages.NewBlockHashesMessage;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.ByteArrayWrapper;

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
    private Keccak256 hash;
    private int count;

    @Override
    public BlockProcessResult processBlock(MessageChannel sender, Block block) {
        Map<ByteArrayWrapper, ImportResult> connectionsResult = new HashMap<>();
        this.blocks.add(block);
        connectionsResult.put(new ByteArrayWrapper(block.getHash().getBytes()), ImportResult.IMPORTED_BEST);
        return new BlockProcessResult(false, connectionsResult, block.getShortHash(), Duration.ZERO);
    }

    @Override
    public void processGetBlock(MessageChannel sender, Keccak256 hash) {

    }

    @Override
    public void processBlockRequest(MessageChannel sender, long requestId, Keccak256 hash) {
        this.requestId = requestId;
        this.hash = hash;
    }

    @Override
    public void processBlockHeadersRequest(MessageChannel sender, long requestId, Keccak256 hash, int count) {
        this.requestId = requestId;
        this.hash = hash;
        this.count = count;
    }

    @Override
    public void processBodyRequest(MessageChannel sender, long requestId, Keccak256 hash) {
    }

    @Override
    public void processBlockHashRequest(MessageChannel sender, long requestId, long height) {
    }

    @Override
    public BlockNodeInformation getNodeInformation() {
        return null;
    }

    @Override
    public Blockchain getBlockchain() {
        return null;
    }

    public List<Block> getBlocks() {
        return this.blocks;
    }

    public long getLastKnownBlockNumber() {
        return lastKnownBlockNumber;
    }

    @Override
    public void processNewBlockHashesMessage(MessageChannel sender, NewBlockHashesMessage message) {

    }

    @Override
    public void processBlockHeaders(MessageChannel sender, List<BlockHeader> blockHeaders) {

    }

    @Override
    public void processSkeletonRequest(final MessageChannel sender, long requestId, final long startNumber) {

    }

    @Override
    public boolean hasBlock(Keccak256 hash) {
        return false;
    }

    @Override
    public boolean hasBlockInProcessorStore(Keccak256 hash) {
        return false;
    }

    @Override
    public boolean hasBlockInSomeBlockchain(Keccak256 hash) {
        return false;
    }

    @Override
    public boolean hasBetterBlockToSync() { return false; }

    public long getRequestId() { return this.requestId; }

    public Keccak256 getHash() { return this.hash; }
}
