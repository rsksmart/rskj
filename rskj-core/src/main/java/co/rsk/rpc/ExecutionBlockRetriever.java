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
import co.rsk.core.bc.BlockResult;
import co.rsk.mine.BlockToMineBuilder;
import co.rsk.trie.Trie;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AtomicReference<Result> cachedPendingBlockResult = new AtomicReference<>();

    public ExecutionBlockRetriever(Blockchain blockchain, BlockToMineBuilder builder, CompositeEthereumListener emitter) {
        this.blockchain = blockchain;
        this.builder = builder;
        this.emitter = emitter;
    }

    public Result retrieveExecutionBlock(String bnOrId) {
        if (LATEST_ID.equals(bnOrId)) {
            return Result.ofBlock(blockchain.getBestBlock());
        }

        if (PENDING_ID.equals(bnOrId)) {
            return getPendingBlockResult();
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
            return Result.ofBlock(executionBlock);
        }

        // If we got here, the specifier given is unsupported
        throw invalidParamError(String.format(
                "Unsupported block specifier '%s'. Can only be either 'latest', " +
                        "'pending' or a specific block number (either hex - prepending '0x' or decimal).",
                bnOrId));
    }

    @Nonnull
    private Result getPendingBlockResult() {
        Block bestBlock = blockchain.getBestBlock();

        // optimistic check without the lock
        Result result = getCachedResultFor(bestBlock);
        if (result != null) {
            return result;
        }

        synchronized (pendingBlockLock) {
            // build a new pending block, but before that just in case check if one hasn't been built while being locked
            bestBlock = blockchain.getBestBlock();
            result = getCachedResultFor(bestBlock);
            if (result != null) {
                return result;
            }

            result = Result.ofBlockResult(builder.buildPending(bestBlock.getHeader()));
            cachedPendingBlockResult.set(result);

            return result;
        }
    }

    @Nullable
    private Result getCachedResultFor(@Nonnull Block bestBlock) {
        Result result = cachedPendingBlockResult.get();
        if (result != null && result.getBlock().getParentHash().equals(bestBlock.getHash())) {
            return result;
        }
        return null;
    }

    @VisibleForTesting
    Result getCachedPendingBlockResult() {
        return cachedPendingBlockResult.get();
    }

    @Override
    public void start() {
        emitter.addListener(listener);
    }

    @Override
    public void stop() {
        emitter.removeListener(listener);
    }

    public static class Result {
        private final Block block;
        private final Trie finalState;

        public Result(@Nonnull Block block, @Nullable Trie finalState) {
            this.block = block;
            this.finalState = finalState;
        }

        static Result ofBlock(Block block) {
            return new Result(block, null);
        }

        static Result ofBlockResult(BlockResult blockResult) {
            return new Result(blockResult.getBlock(), blockResult.getFinalState());
        }

        public Block getBlock() {
            return block;
        }

        public Trie getFinalState() {
            return finalState;
        }
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
            cachedPendingBlockResult.set(null);
        }
    }
}
