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

package co.rsk.core.bc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.EthereumListener;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BlockExecutor has methods to execute block with its transactions.
 * There are two main use cases:
 * - execute and validate the block final state
 * - execute and complete the block final state
 * <p>
 * Created by ajlopez on 29/07/2016.
 */
public class BlockExecutor {
    private static final Logger logger = LoggerFactory.getLogger("blockexecutor");

    private final RskSystemProperties config;
    private final Repository repository;
    private final ReceiptStore receiptStore;
    private final BlockStore blockStore;
    private final EthereumListener listener;

    private final ProgramInvokeFactory programInvokeFactory = new ProgramInvokeFactoryImpl();

    public BlockExecutor(
        RskSystemProperties config,
        Repository repository,
        ReceiptStore receiptStore,
        BlockStore blockStore,
        EthereumListener listener) {

        this.config = config;
        this.repository = repository;
        this.receiptStore = receiptStore;
        this.blockStore = blockStore;
        this.listener = listener;
    }

    /**
     * Execute and complete a block.
     *
     * @param block        A block to execute and complete
     * @param parent       The parent of the block.
     */
    public void executeAndFill(Block block, Block parent) {
        BlockResult result = execute(block, parent.getStateRoot(), true);
        fill(block, result);
    }

    public void executeAndFillAll(Block block, Block parent) {
        BlockResult result = executeAll(block, parent.getStateRoot());
        fill(block, result);
    }

    public void executeAndFillReal(Block block, Block parent) {
        BlockResult result = execute(block, parent.getStateRoot(), false, false);
        if (result != BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT) {
            fill(block, result);
        }
    }

    private void fill(Block block, BlockResult result) {
        block.setTransactionsList(result.getExecutedTransactions());
        BlockHeader header = block.getHeader();
        header.setTransactionsRoot(Block.getTxTrie(block.getTransactionsList()).getHash().getBytes());
        header.setReceiptsRoot(result.getReceiptsRoot());
        header.setGasUsed(result.getGasUsed());
        header.setPaidFees(result.getPaidFees());
        block.setStateRoot(result.getStateRoot());

        header.setLogsBloom(result.getLogsBloom());

        block.flushRLP();
    }

    /**
     * Execute and validate the final state of a block.
     *
     * @param block        A block to execute and complete
     * @param parent       The parent of the block.
     * @return true if the block final state is equalBytes to the calculated final state.
     */
    public boolean executeAndValidate(Block block, Block parent) {
        BlockResult result = execute(block, parent.getStateRoot(), false);

        return this.validate(block, result);
    }

    /**
     * Validate the final state of a block.
     *
     * @param block        A block to validate
     * @param result       A block result (state root, receipts root, etc...)
     * @return true if the block final state is equalBytes to the calculated final state.
     */
    public boolean validate(Block block, BlockResult result) {
        if (result == BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT) {
            logger.error("Block's execution was interrupted because of an invalid transaction: {} {}.", block.getNumber(), block.getShortHash());
            return false;
        }

        if (!Arrays.equals(result.getStateRoot(), block.getStateRoot()))  {
            logger.error("Block's given State Root doesn't match: {} {} {} != {}", block.getNumber(), block.getShortHash(), Hex.toHexString(block.getStateRoot()), Hex.toHexString(result.getStateRoot()));
            return false;
        }

        if (!Arrays.equals(result.getReceiptsRoot(), block.getReceiptsRoot())) {
            logger.error("Block's given Receipt Hash doesn't match: {} {} != {}", block.getNumber(), block.getShortHash(), Hex.toHexString(result.getReceiptsRoot()));
            return false;
        }

        byte[] resultLogsBloom = result.getLogsBloom();
        byte[] blockLogsBloom = block.getLogBloom();

        if (!Arrays.equals(resultLogsBloom, blockLogsBloom)) {
            String resultLogsBloomString = Hex.toHexString(resultLogsBloom);
            String blockLogsBloomString = Hex.toHexString(blockLogsBloom);

            logger.error("Block's given logBloom Hash doesn't match: {} != {} Block {} {}", resultLogsBloomString, blockLogsBloomString, block.getNumber(), block.getShortHash());
            return false;
        }

        if (result.getGasUsed() != block.getGasUsed()) {
            logger.error("Block's given gasUsed doesn't match: {} != {} Block {} {}", block.getGasUsed(), result.getGasUsed(), block.getNumber(), block.getShortHash());
            return false;
        }

        Coin paidFees = result.getPaidFees();
        Coin feesPaidToMiner = block.getFeesPaidToMiner();

        if (!paidFees.equals(feesPaidToMiner))  {
            logger.error("Block's given paidFees doesn't match: {} != {} Block {} {}", feesPaidToMiner, paidFees, block.getNumber(), block.getShortHash());
            return false;
        }

        List<Transaction> executedTransactions = result.getExecutedTransactions();
        List<Transaction> transactionsList = block.getTransactionsList();

        if (!executedTransactions.equals(transactionsList))  {
            logger.error("Block's given txs doesn't match: {} != {} Block {} {}", transactionsList, executedTransactions, block.getNumber(), block.getShortHash());
            return false;
        }

        return true;
    }

    /**
     * Execute a block, from initial state, returning the final state data.
     *
     * @param block        A block to validate
     * @param stateRoot    Initial state hash
     * @return BlockResult with the final state data.
     */
    public BlockResult execute(Block block, byte[] stateRoot, boolean discardInvalidTxs) {
        return execute(block, stateRoot, discardInvalidTxs, false);
    }

    public BlockResult executeAll(Block block, byte[] stateRoot) {
        return execute(block, stateRoot, false, true);
    }

    private BlockResult execute(Block block, byte[] stateRoot, boolean discardInvalidTxs, boolean ignoreReadyToExecute) {
        logger.trace("applyBlock: block: [{}] tx.list: [{}]", block.getNumber(), block.getTransactionsList().size());

        Repository initialRepository = repository.getSnapshotTo(stateRoot);

        byte[] lastStateRootHash = initialRepository.getRoot();

        Repository track = initialRepository.startTracking();
        int i = 1;
        long totalGasUsed = 0;
        Coin totalPaidFees = Coin.ZERO;
        List<TransactionReceipt> receipts = new ArrayList<>();
        List<Transaction> executedTransactions = new ArrayList<>();

        int txindex = 0;

        for (Transaction tx : block.getTransactionsList()) {
            logger.trace("apply block: [{}] tx: [{}] ", block.getNumber(), i);

            TransactionExecutor txExecutor = new TransactionExecutor(config, tx, txindex++, block.getCoinbase(), track, blockStore, receiptStore, programInvokeFactory, block, listener, totalGasUsed);

            boolean readyToExecute = txExecutor.init();
            if (!ignoreReadyToExecute && !readyToExecute) {
                if (discardInvalidTxs) {
                    logger.warn("block: [{}] discarded tx: [{}]", block.getNumber(), tx.getHash());
                    continue;
                } else {
                    logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                                block.getNumber(), tx.getHash());
                    return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
                }
            }

            executedTransactions.add(tx);

            txExecutor.execute();
            txExecutor.go();
            txExecutor.finalization();

            logger.trace("tx executed");

            track.commit();

            logger.trace("track commit");

            long gasUsed = txExecutor.getGasUsed();
            totalGasUsed += gasUsed;
            Coin paidFees = txExecutor.getPaidFees();
            if (paidFees != null) {
                totalPaidFees = totalPaidFees.add(paidFees);
            }

            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setGasUsed(gasUsed);
            receipt.setCumulativeGas(totalGasUsed);
            lastStateRootHash = initialRepository.getRoot();
            receipt.setTxStatus(txExecutor.getReceipt().isSuccessful());
            receipt.setTransaction(tx);
            receipt.setLogInfoList(txExecutor.getVMLogs());
            receipt.setStatus(txExecutor.getReceipt().getStatus());

            logger.trace("block: [{}] executed tx: [{}] state: [{}]", block.getNumber(), tx.getHash(),
                         Hex.toHexString(lastStateRootHash));

            logger.trace("tx[{}].receipt", i);

            i++;

            receipts.add(receipt);

            logger.trace("tx done");
        }

        return new BlockResult(executedTransactions, receipts, lastStateRootHash, totalGasUsed, totalPaidFees);
    }
}
