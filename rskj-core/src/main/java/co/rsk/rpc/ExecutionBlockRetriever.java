/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.rpc;

import co.rsk.mine.BlockToMineBuilder;
import co.rsk.mine.MinerServer;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.rpc.exception.JsonRpcInvalidParamException;
import org.ethereum.rpc.exception.JsonRpcUnimplementedMethodException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Encapsulates the logic to retrieve or create an execution block
 * for Web3 calls.
 */
@Component
public class ExecutionBlockRetriever {
    private static final String LATEST_ID = "latest";
    private static final String PENDING_ID = "pending";

    private final Blockchain blockchain;
    private final MinerServer minerServer;
    private final BlockToMineBuilder builder;

    @Nullable
    private Block cachedBlock;

    @Autowired
    public ExecutionBlockRetriever(Blockchain blockchain, MinerServer minerServer, BlockToMineBuilder builder) {
        this.blockchain = blockchain;
        this.minerServer = minerServer;
        this.builder = builder;
    }

    public Block getExecutionBlock(String bnOrId) {
        if (LATEST_ID.equals(bnOrId)) {
            return blockchain.getBestBlock();
        }

        if (PENDING_ID.equals(bnOrId)) {
            Optional<Block> latestBlock = minerServer.getLatestBlock();
            if (latestBlock.isPresent()) {
                return latestBlock.get();
            }

            Block bestBlock = blockchain.getBestBlock();
            if (cachedBlock == null || !bestBlock.isParentOf(cachedBlock)) {
                cachedBlock = builder.build(bestBlock, null);
            }

            return cachedBlock;
        }

        if (bnOrId.startsWith("0x")) {
            long executionBlockNumber;
            try {
                executionBlockNumber = Long.parseLong(bnOrId.substring(2), 16);
            } catch (NumberFormatException e) {
                throw new JsonRpcInvalidParamException(String.format("Not a number: %s", bnOrId), e);
            }

            Block executionBlock = blockchain.getBlockByNumber(executionBlockNumber);
            if (executionBlock == null) {
                throw new JsonRpcInvalidParamException(String.format("Invalid block number %d", executionBlockNumber));
            }
            return executionBlock;
        }

        throw new JsonRpcUnimplementedMethodException("Method only supports 'latest' and 'pending' as parameters so far.");
    }
}
