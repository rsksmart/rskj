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

package co.rsk.rpc.modules.trace;

import co.rsk.config.VmConfig;
import co.rsk.core.bc.BlockExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.ethereum.vm.trace.Serializers;
import org.ethereum.vm.trace.SummarizedProgramTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

public class TraceModuleImpl implements TraceModule {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    private final BlockExecutor blockExecutor;

    public TraceModuleImpl(
            BlockStore blockStore,
            ReceiptStore receiptStore,
            BlockExecutor blockExecutor) {
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.blockExecutor = blockExecutor;
    }

    @Override
    public JsonNode traceTransaction(String transactionHash) throws Exception {
        logger.trace("trace_transaction({})", transactionHash);

        byte[] hash = stringHexToByteArray(transactionHash);
        TransactionInfo txInfo = receiptStore.getInMainChain(hash, blockStore);

        if (txInfo == null) {
            logger.trace("No transaction info for {}", transactionHash);
            return null;
        }

        Block block = blockStore.getBlockByHash(txInfo.getBlockHash());
        Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());
        Transaction tx = block.getTransactionsList().get(txInfo.getIndex());
        txInfo.setTransaction(tx);

        ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor();
        blockExecutor.traceBlock(programTraceProcessor, VmConfig.LIGHT_TRACE, block, parent.getHeader(), false, false);

        SummarizedProgramTrace programTrace = (SummarizedProgramTrace)programTraceProcessor.getProgramTrace(tx.getHash());

        if (programTrace == null) {
            return null;
        }

        List<TransactionTrace> traces = TraceTransformer.toTraces(programTrace, txInfo, block.getNumber());
        ObjectMapper mapper = Serializers.createMapper(true);
        return mapper.valueToTree(traces);
    }
}
