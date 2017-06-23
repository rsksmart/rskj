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

package co.rsk.net;

import co.rsk.net.messages.NewBlockHashesMessage;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;

import java.util.List;

/**
 * Created by ajlopez on 5/11/2016.
 */
public interface BlockProcessor {
    BlockProcessResult processBlock(MessageSender sender, Block block);

    void processStatus(MessageSender sender, Status status);

    void processGetBlock(MessageSender sender, byte[] hash);

    void processGetBlockHeaders(MessageSender sender, long blockNumber, byte[] hash, int maxHeaders, int skip, boolean reverse);

    void processGetBlockHeaders(MessageSender sender, byte[] hash);

    BlockNodeInformation getNodeInformation();

    Blockchain getBlockchain();

    long getLastKnownBlockNumber();

    void processNewBlockHashesMessage(MessageSender sender, NewBlockHashesMessage message);

    void processBlockHeaders(MessageSender sender, List<BlockHeader> blockHeaders);

    boolean hasBlock(byte[] hash);

    boolean hasBlockInProcessorStore(byte[] hash);

    boolean hasBlockInSomeBlockchain(byte[] hash);

    boolean isSyncingBlocks();

    boolean hasBetterBlockToSync();

    void sendStatusToAll();

    void acceptAnyBlock();
}
