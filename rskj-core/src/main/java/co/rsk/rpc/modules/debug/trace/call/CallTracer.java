/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.rpc.modules.debug.trace.call;

import co.rsk.config.VmConfig;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.modules.debug.TraceOptions;
import co.rsk.rpc.modules.debug.trace.DebugTracer;
import co.rsk.rpc.modules.debug.trace.TracerType;
import co.rsk.util.HexUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.ethereum.vm.trace.Serializers;
import org.ethereum.vm.trace.SummarizedProgramTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CallTracer implements DebugTracer {
    private static final Logger logger = LoggerFactory.getLogger("callTracer");
    private static final ObjectMapper OBJECT_MAPPER = Serializers.createMapper(true);

    private final Web3InformationRetriever web3InformationRetriever;
    private final BlockStore blockStore;
    private final BlockExecutor blockExecutor;
    private final ReceiptStore receiptStore;
    private final Blockchain blockchain;

    public CallTracer(BlockStore blockStore, BlockExecutor blockExecutor, Web3InformationRetriever web3InformationRetriever, ReceiptStore receiptStore, Blockchain blockchain) {
        this.blockStore = blockStore;
        this.blockExecutor = blockExecutor;
        this.web3InformationRetriever = web3InformationRetriever;
        this.receiptStore = receiptStore;
        this.blockchain = blockchain;
    }

    @Override
    public JsonNode traceTransaction(@Nonnull String transactionHash, @Nonnull TraceOptions traceOptions) throws Exception {
        logger.trace("trace_transaction({})", transactionHash);

        byte[] hash = HexUtils.stringHexToByteArray(transactionHash);
        if(hash == null) {
            logger.error("Invalid transaction hash: {}", transactionHash);
            throw new IllegalArgumentException("Invalid transaction hash: " + transactionHash);
        }

        TransactionInfo txInfo = this.receiptStore.getInMainChain(hash, this.blockStore).orElse(null);

        if (txInfo == null) {
            logger.trace("No transaction info for {}", transactionHash);
            throw new IllegalArgumentException("No transaction info for " + transactionHash);
        }

        Block block = this.blockchain.getBlockByHash(txInfo.getBlockHash());
        Block parent = this.blockchain.getBlockByHash(block.getParentHash().getBytes());
        Transaction tx = block.getTransactionsList().get(txInfo.getIndex());
        txInfo.setTransaction(tx);

        ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor();
        BlockResult blockResult = this.blockExecutor.traceBlock(programTraceProcessor, VmConfig.LIGHT_TRACE, block, parent.getHeader(), false, false);
        if (blockResult == null || blockResult.getExecutedTransactions() == null || blockResult.getExecutedTransactions().isEmpty()) {
            logger.error("Unsuccessful block execution for transaction: {}", transactionHash);
            return null;
        }
        SummarizedProgramTrace programTrace = (SummarizedProgramTrace) programTraceProcessor.getProgramTrace(tx.getHash());
        if (programTrace == null ) {
            logger.error("No program trace could be obtained for transaction: {}", transactionHash);
            return null;
        }

        TransactionInfo executedTxInfo = null;
        List<TransactionReceipt> transactionReceipts = blockResult.getTransactionReceipts();
        for(int i = 0; i < transactionReceipts.size(); i++) {
            if (transactionReceipts.get(i).getTransaction().getHash().equals(tx.getHash())) {
                executedTxInfo = new TransactionInfo(transactionReceipts.get(i), block.getHash().getBytes(), i);
                break;
            }
        }
        if(executedTxInfo == null){
            logger.error("No transaction info could be obtained for transaction: {}", transactionHash);
            return null;
        }

        TransactionTrace trace = CallTraceTransformer.toTrace(programTrace, executedTxInfo, null, traceOptions.isOnlyTopCall(), traceOptions.isWithLog());
        return OBJECT_MAPPER.valueToTree(trace.getResult());
    }

    @Override
    public JsonNode traceBlockByHash(String blockHash, TraceOptions traceOptions) {
        byte[] bHash = HexUtils.stringHexToByteArray(blockHash);
        Block block = blockStore.getBlockByHash(bHash);
        if (block == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("No block is found for blockHash: {}", StringUtils.trim(blockHash));
            }
            return null;
        }
        return traceBlock(block, traceOptions);
    }

    @Override
    public JsonNode traceBlockByNumber(@Nonnull String bnOrId, @Nonnull TraceOptions traceOptions) {
        Block block = web3InformationRetriever.getBlock(bnOrId).orElse(null);
        if (block == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("No block is found for bnOrId: {}", StringUtils.trim(bnOrId));
            }
            return null;
        }

        return traceBlock(block, traceOptions);
    }

    @Override
    public TracerType getTracerType() {
        return TracerType.CALL_TRACER;
    }

    private JsonNode traceBlock(Block block, @Nonnull TraceOptions traceOptions) {
        List<TransactionTrace> result = buildBlockTraces(block, traceOptions.isOnlyTopCall(), traceOptions.isWithLog());
        return OBJECT_MAPPER.valueToTree(result);
    }

    private List<TransactionTrace> buildBlockTraces(Block block, boolean onlyTopCall, boolean withLog) {
        List<TransactionTrace> blockTraces = new ArrayList<>();

        if (block != null && block.getNumber() != 0) {
            ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor();
            Block parent = this.blockchain.getBlockByHash(block.getParentHash().getBytes());
            BlockResult blockResult = this.blockExecutor.traceBlock(programTraceProcessor, VmConfig.LIGHT_TRACE, block, parent.getHeader(), false, false);


            int i=0;
            for (Transaction tx : blockResult.getExecutedTransactions()) {
                TransactionInfo txInfo = new TransactionInfo(blockResult.getTransactionReceipts().get(i), block.getHash().getBytes(),i);

                SummarizedProgramTrace programTrace = (SummarizedProgramTrace) programTraceProcessor.getProgramTrace(tx.getHash());

                if (programTrace == null) {
                    blockTraces.clear();
                    return Collections.emptyList();
                }

                TransactionTrace trace = CallTraceTransformer.toTrace(programTrace, txInfo, null, onlyTopCall, withLog);

                blockTraces.add(trace);
                i++;
            }
        }

        return blockTraces;
    }


}
