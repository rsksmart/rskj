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
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.ethereum.vm.trace.Serializers;
import org.ethereum.vm.trace.SummarizedProgramTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.ethereum.rpc.TypeConverter.stringHexToBigInteger;
import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

public class TraceModuleImpl implements TraceModule {

    private static final Logger logger = LoggerFactory.getLogger("web3");

    private static final ObjectMapper OBJECT_MAPPER = Serializers.createMapper(true);

    private final Blockchain blockchain;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    private final BlockExecutor blockExecutor;

    public TraceModuleImpl(
            Blockchain blockchain,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            BlockExecutor blockExecutor) {
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.blockExecutor = blockExecutor;
    }

    @Override
    public JsonNode traceTransaction(String transactionHash) throws Exception {
        logger.trace("trace_transaction({})", transactionHash);

        byte[] hash = stringHexToByteArray(transactionHash);
        TransactionInfo txInfo = this.receiptStore.getInMainChain(hash, this.blockStore).orElse(null);

        if (txInfo == null) {
            logger.trace("No transaction info for {}", transactionHash);
            return null;
        }

        Block block = this.blockchain.getBlockByHash(txInfo.getBlockHash());
        Block parent = this.blockchain.getBlockByHash(block.getParentHash().getBytes());
        Transaction tx = block.getTransactionsList().get(txInfo.getIndex());
        txInfo.setTransaction(tx);

        ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor();
        this.blockExecutor.traceBlock(programTraceProcessor, VmConfig.LIGHT_TRACE, block, parent.getHeader(), false, false);

        SummarizedProgramTrace programTrace = (SummarizedProgramTrace)programTraceProcessor.getProgramTrace(tx.getHash());

        if (programTrace == null) {
            return null;
        }

        List<TransactionTrace> traces = TraceTransformer.toTraces(programTrace, txInfo, block.getNumber());

        return OBJECT_MAPPER.valueToTree(traces);
    }

    @Override
    public JsonNode traceBlock(String blockArgument) throws Exception {
        logger.trace("trace_block({})", blockArgument);

        Block block = this.getByJsonArgument(blockArgument);

        if (block == null) {
            logger.trace("No block for {}", blockArgument);
            return null;
        }

        Block parent = this.blockchain.getBlockByHash(block.getParentHash().getBytes());

        List<TransactionTrace> blockTraces = new ArrayList<>();

        if (block.getNumber() != 0) {
            ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor();
            this.blockExecutor.traceBlock(programTraceProcessor, VmConfig.LIGHT_TRACE, block, parent.getHeader(), false, false);

            for (Transaction tx : block.getTransactionsList()) {
                TransactionInfo txInfo = receiptStore.getInMainChain(tx.getHash().getBytes(), this.blockStore).orElse(null);
                Objects.requireNonNull(txInfo);
                txInfo.setTransaction(tx);

                SummarizedProgramTrace programTrace = (SummarizedProgramTrace) programTraceProcessor.getProgramTrace(tx.getHash());

                if (programTrace == null) {
                    return null;
                }

                List<TransactionTrace> traces = TraceTransformer.toTraces(programTrace, txInfo, block.getNumber());

                blockTraces.addAll(traces);
            }
        }

        return OBJECT_MAPPER.valueToTree(blockTraces);
    }

    private Block getByJsonArgument(String arg) {
        if (arg.length() < 20) {
            return this.getByJsonBlockId(arg);
        }
        else {
            return this.getByJsonBlockHash(arg);
        }
    }

    private Block getByJsonBlockHash(String arg) {
        byte[] hash = stringHexToByteArray(arg);

        return this.blockchain.getBlockByHash(hash);
    }

    private Block getByJsonBlockId(String id) {
        if ("earliest".equalsIgnoreCase(id)) {
            return this.blockchain.getBlockByNumber(0);
        } else if ("latest".equalsIgnoreCase(id)) {
            return this.blockchain.getBestBlock();
        } else if ("pending".equalsIgnoreCase(id)) {
            throw RskJsonRpcRequestException.unimplemented("The method don't support 'pending' as a parameter yet");
        } else {
            try {
                long blockNumber = stringHexToBigInteger(id).longValue();
                return this.blockchain.getBlockByNumber(blockNumber);
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                throw RskJsonRpcRequestException.invalidParamError("invalid blocknumber " + id);
            }
        }
    }
}
