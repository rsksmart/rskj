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

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.net.*;
import co.rsk.net.messages.NewBlockHashesMessage;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class SimpleBlockProcessor implements BlockProcessor {
    public long lastKnownBlockNumber = 0;
    private List<Block> blocks = new ArrayList<>();
    private List<Block> siblings = new ArrayList<>();
    private long requestId;
    private byte[] hash;
    private int count;
    private long blockGap = 1000000;
    private Blockchain blockchain;

    public SimpleBlockProcessor(Blockchain blockchain){
        this.blockchain = blockchain;
    }

    public SimpleBlockProcessor(){
    }

    @Override
    public BlockProcessResult processBlock(Block block, MessageChannel sender) {
        Map<Keccak256, ImportResult> connectionsResult = new HashMap<>();
        this.blocks.add(block);
        connectionsResult.put(block.getHash(), ImportResult.IMPORTED_BEST);
        return new BlockProcessResult(false, connectionsResult, block.getShortHash(), Duration.ZERO);
    }

    @Override
    public void processGetBlock(MessageChannel sender, byte[] hash) {

    }

    @Override
    public boolean isAdvancedBlock(long number) {
        return number >= this.blockGap;
    }

    public void setBlockGap(long gap) {
        this.blockGap = gap;
    }

    @Override
    public void processBlockRequest(MessageChannel sender, long requestId, byte[] hash) {
        this.requestId = requestId;
        this.hash = hash;
        this.count = count;
    }

    @Override
    public void processBlockHeadersRequest(MessageChannel sender, long requestId, byte[] hash, int count) {
        this.requestId = requestId;
        this.hash = hash;
    }

    @Override
    public void processBodyRequest(MessageChannel sender, long requestId, byte[] hash) {
    }

    @Override
    public void processBlockHashRequest(MessageChannel sender, long requestId, long height) {
    }

    @Override
    public BlockNodeInformation getNodeInformation() {
        return mock(BlockNodeInformation.class);
    }

    @Override
    public Blockchain getBlockchain() {
        return blockchain;
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
    public void processSkeletonRequest(final MessageChannel sender, long requestId, final long startNumber) {

    }

    @Override
    public boolean canBeIgnoredForUnclesRewards(long blockNumber) {
        return false;
    }

    @Override
    public BlockDifficulty getTotalDifficultyFor(Block block) {
        return new BlockDifficulty(BigInteger.ONE);
    }

    @Override
    public void processSibling(MessageChannel sender, Block block) {
        this.siblings.add(block);
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

    public List<Block> getSiblings() {
        return siblings;
    }
}
