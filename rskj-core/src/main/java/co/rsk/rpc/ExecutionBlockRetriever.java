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

import co.rsk.config.InternalService;
import co.rsk.core.Coin;
import co.rsk.core.bc.BlockResult;
import co.rsk.mine.BlockToMineBuilder;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.Utils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

/**
 * Encapsulates the logic to retrieve or create an execution block
 * for Web3 calls.
 */
public class ExecutionBlockRetriever implements InternalService {
    private static final String LATEST_ID = "latest";
    private static final String PENDING_ID = "pending";

    private final Object pendingBlockLock = new Object();
    private final Blockchain blockchain;
    private final BlockToMineBuilder builder;
    private final CompositeEthereumListener emitter;
    private final EthereumListener listener = new CachedResultCleaner();

    private volatile BlockResult cachedResult;

    public ExecutionBlockRetriever(Blockchain blockchain, BlockToMineBuilder builder, CompositeEthereumListener emitter) {
        this.blockchain = blockchain;
        this.builder = builder;
        this.emitter = emitter;
    }

    public BlockResult retrieveExecutionBlock(String bnOrId) {
        if (LATEST_ID.equals(bnOrId)) {
            return newBlockResult(blockchain.getBestBlock());
        }

        if (PENDING_ID.equals(bnOrId)) {
            Block bestBlock = blockchain.getBestBlock();
            BlockResult result = cachedResult;
            // optimistic check without the lock
            if (result != null && result.getBlock().getParentHash().equals(bestBlock.getHash())) {
                return result;
            }

            synchronized (pendingBlockLock) {
                // build a new pending block, but before that just in case check if one hasn't been built while being locked
                bestBlock = blockchain.getBestBlock();
                result = cachedResult;
                if (result != null && result.getBlock().getParentHash().equals(bestBlock.getHash())) {
                    return result;
                }

                result = builder.buildPending(bestBlock.getHeader());
                cachedResult = result;

                return result;
            }
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

    @VisibleForTesting
    BlockResult getCachedResult() {
        return cachedResult;
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

    @Override
    public void start() {
        emitter.addListener(listener);
    }

    @Override
    public void stop() {
        emitter.removeListener(listener);
    }

    private class CachedResultCleaner extends EthereumListenerAdapter {
        @Override
        public void onPendingTransactionsReceived(List<Transaction> transactions) {
            cleanCachedResult();
        }

        @Override
        public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
            cleanCachedResult();
        }

        private void cleanCachedResult() {
            cachedResult = null;
        }
    }
}
