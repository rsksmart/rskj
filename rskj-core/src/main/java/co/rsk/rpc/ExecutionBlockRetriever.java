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

import co.rsk.core.Coin;
import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.mine.BlockToMineBuilder;
import co.rsk.mine.MinerServer;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.util.Utils;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

/**
 * Encapsulates the logic to retrieve or create an execution block
 * for Web3 calls.
 */
public class ExecutionBlockRetriever {
    private static final String LATEST_ID = "latest";
    private static final String PENDING_ID = "pending";

    private final MiningMainchainView miningMainchainView;
    private final Blockchain blockchain;
    private final MinerServer minerServer;
    private final BlockToMineBuilder builder;

    @Nullable
    private Block cachedBlock;
    @Nullable
    private BlockResult cachedResult;

    public ExecutionBlockRetriever(MiningMainchainView miningMainchainView,
                                   Blockchain blockchain,
                                   MinerServer minerServer,
                                   BlockToMineBuilder builder) {
        this.miningMainchainView = miningMainchainView;
        this.blockchain = blockchain;
        this.minerServer = minerServer;
        this.builder = builder;
    }

    public BlockResult retrieveExecutionBlock(String bnOrId) {
        if (LATEST_ID.equals(bnOrId)) {
            return newBlockResult(blockchain.getBestBlock());
        }

        if (PENDING_ID.equals(bnOrId)) {
            Optional<Block> latestBlock = minerServer.getLatestBlock();
            if (latestBlock.isPresent()) {
                return newBlockResult(latestBlock.get());
            }

            Block bestBlock = blockchain.getBestBlock();
            if (cachedBlock == null || !bestBlock.isParentOf(cachedBlock)) {

                // If the miner server is not running there is no one to update the mining mainchain view,
                // thus breaking eth_call with 'pending' parameter
                //
                // This is just a provisional fix not intended to remain in the long run
                if (!minerServer.isRunning()) {
                    miningMainchainView.addBest(bestBlock.getHeader());
                }

                List<BlockHeader> mainchainHeaders = miningMainchainView.get();
                cachedResult = builder.build(mainchainHeaders, null);
            }

            return cachedResult;
        }

        // Is the block specifier either a hexadecimal or decimal number?
        Optional<Long> executionBlockNumber = Optional.empty();

        if (Utils.isHexadecimalString(bnOrId)) {
            executionBlockNumber = Optional.of(Utils.hexadecimalStringToLong(bnOrId));
        } else if (Utils.isDecimalString(bnOrId)) {
            executionBlockNumber = Optional.of(Utils.decimalStringToLong(bnOrId));
        }

        if (executionBlockNumber.isPresent()) {
            Block executionBlock = blockchain.getBlockByNumber(executionBlockNumber.get());
            if (executionBlock == null) {
                throw invalidParamError(String.format("Invalid block number %d", executionBlockNumber.get()));
            }
            return newBlockResult(executionBlock);
        }

        // If we got here, the specifier given is unsupported
        throw invalidParamError(String.format(
                "Unsupported block specifier '%s'. Can only be either 'latest', " +
                        "'pending' or a specific block number (either hex - prepending '0x' or decimal).",
                bnOrId));
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

                // If the miner server is not running there is no one to update the mining mainchain view,
                // thus breaking eth_call with 'pending' parameter
                //
                // This is just a provisional fix not intended to remain in the long run
                if (!minerServer.isRunning()) {
                    miningMainchainView.addBest(bestBlock.getHeader());
                }

                List<BlockHeader> mainchainHeaders = miningMainchainView.get();
                cachedBlock = builder.build(mainchainHeaders, null).getBlock();
            }

            return cachedBlock;
        }

        // Is the block specifier either a hexadecimal or decimal number?
        Optional<Long> executionBlockNumber = Optional.empty();

        if (Utils.isHexadecimalString(bnOrId)) {
            executionBlockNumber = Optional.of(Utils.hexadecimalStringToLong(bnOrId));
        } else if (Utils.isDecimalString(bnOrId)) {
            executionBlockNumber = Optional.of(Utils.decimalStringToLong(bnOrId));
        }

        if (executionBlockNumber.isPresent()) {
            Block executionBlock = blockchain.getBlockByNumber(executionBlockNumber.get());
            if (executionBlock == null) {
                throw invalidParamError(String.format("Invalid block number %d", executionBlockNumber.get()));
            }
            return executionBlock;
        }

        // If we got here, the specifier given is unsupported
        throw invalidParamError(String.format(
                "Unsupported block specifier '%s'. Can only be either 'latest', " +
                "'pending' or a specific block number (either hex - prepending '0x' or decimal).",
                bnOrId));
    }

    private static BlockResult newBlockResult(Block block) {
        return new BlockResult(
                block,
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                Coin.ZERO,
                null
        );
    }
}
