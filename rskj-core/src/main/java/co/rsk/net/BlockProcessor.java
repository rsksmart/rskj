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
    BlockProcessResult processBlock(MessageChannel sender, Block block);

    void processGetBlock(MessageChannel sender, byte[] hash);

    BlockNodeInformation getNodeInformation();

    Blockchain getBlockchain();

    long getLastKnownBlockNumber();

    void processNewBlockHashesMessage(MessageChannel sender, NewBlockHashesMessage message);

    void processBlockHeaders(MessageChannel sender, List<BlockHeader> blockHeaders);

    boolean hasBlock(byte[] hash);

    boolean hasBlockInProcessorStore(byte[] hash);

    boolean hasBlockInSomeBlockchain(byte[] hash);

    boolean hasBetterBlockToSync();

    boolean isAdvancedBlock(long number);

    // New messages for RSK's sync protocol

    void processBlockRequest(MessageChannel sender, long requestId, byte[] hash);

    void processBlockHeadersRequest(MessageChannel sender, long requestId, byte[] hash, int count);

    void processBodyRequest(MessageChannel sender, long requestId, byte[] hash);

    void processBlockHashRequest(MessageChannel sender, long requestId, long height);

    void processSkeletonRequest(MessageChannel sender, long requestId, long startNumber);

    boolean canBeIgnoredForUnclesRewards(long blockNumber);
}
