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

import co.rsk.net.*;
import co.rsk.net.messages.NewBlockHashesMessage;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.ByteArrayWrapper;

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


    @Override
    public BlockProcessResult processBlock(MessageSender sender, Block block) {
        Map<ByteArrayWrapper, ImportResult> connectionsResult = new HashMap<>();
        this.blocks.add(block);
        connectionsResult.put(new ByteArrayWrapper(block.getHash()), ImportResult.IMPORTED_BEST);
        return new BlockProcessResult(false, connectionsResult);
    }

    @Override
    public void processGetBlock(MessageSender sender, byte[] hash) {

    }

    @Override
    public void processGetBlockHeaders(MessageSender sender, long blockNumber, byte[] hash, int maxHeaders, int skip, boolean reverse) {

    }

    @Override
    public void processGetBlockHeaders(MessageSender sender, byte[] hash) {

    }

    @Override
    public BlockNodeInformation getNodeInformation() {
        return null;
    }

    @Override
    public void processStatus(MessageSender sender, Status status) {

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
    public void processNewBlockHashesMessage(MessageSender sender, NewBlockHashesMessage message) {

    }

    @Override
    public void processBlockHeaders(MessageSender sender, List<BlockHeader> blockHeaders) {

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
    public boolean isSyncingBlocks() { return false; }

    @Override
    public boolean hasBetterBlockToSync() { return false; }

    @Override
    public void sendStatusToAll() { }

    @Override
    public void acceptAnyBlock() { }
}
