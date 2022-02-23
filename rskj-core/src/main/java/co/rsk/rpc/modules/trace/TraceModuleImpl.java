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
import co.rsk.util.HexUtils;
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

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        SummarizedProgramTrace programTrace = (SummarizedProgramTrace) programTraceProcessor.getProgramTrace(tx.getHash());

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

        List<TransactionTrace> blockTraces = buildBlockTraces(block);

        return blockTraces == null ? null : OBJECT_MAPPER.valueToTree(blockTraces);
    }

    @Override
    public JsonNode traceFilter(TraceFilterRequest traceFilterRequest) throws Exception {
        List<TransactionTrace> blockTraces = new ArrayList<>();

        long fromBlockNumber = traceFilterRequest.getFromBlockNumber().longValue();
        Block block;

        if (traceFilterRequest.getToBlock().equalsIgnoreCase("latest")) {
            block = this.blockchain.getBestBlock();
        } else {
            long toBlockNumber = traceFilterRequest.getToBlockNumber().longValue();
            block = this.blockchain.getBlockByNumber(toBlockNumber);
        }

        while (block != null && block.getNumber() >= fromBlockNumber) {
            List<TransactionTrace> builtTraces = buildBlockTraces(block, traceFilterRequest);
            blockTraces.addAll(builtTraces == null ? new ArrayList<>() : builtTraces);

            block = this.blockchain.getBlockByHash(block.getParentHash().getBytes());
        }

        Collections.reverse(blockTraces);

        Stream<TransactionTrace> txTraceStream = blockTraces.stream();

        if (traceFilterRequest.getAfter() != null) {
            txTraceStream = txTraceStream.skip(traceFilterRequest.getAfter());
        }

        if (traceFilterRequest.getCount() != null) {
            txTraceStream = txTraceStream.limit(traceFilterRequest.getCount());
        }

        List<TransactionTrace> traces = txTraceStream.collect(Collectors.toList());

        return OBJECT_MAPPER.valueToTree(traces);
    }

    private Block getByJsonArgument(String arg) {
        if (arg.length() < 20) {
            return this.getByJsonBlockId(arg);
        } else {
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

    private List<TransactionTrace> buildBlockTraces(Block block) {
        return buildBlockTraces(block, null);
    }

    private List<TransactionTrace> buildBlockTraces(Block block, TraceFilterRequest traceFilterRequest) {
        List<TransactionTrace> blockTraces = new ArrayList<>();

        if (block != null && block.getNumber() != 0) {
            List<Transaction> txList = block.getTransactionsList();

            ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor();
            Block parent = this.blockchain.getBlockByHash(block.getParentHash().getBytes());
            this.blockExecutor.traceBlock(programTraceProcessor, VmConfig.LIGHT_TRACE, block, parent.getHeader(), false, false);

            if (traceFilterRequest != null) {
                Stream<Transaction> txStream = block.getTransactionsList().stream();

                if (traceFilterRequest.getFromAddress() != null && !traceFilterRequest.getFromAddress().isEmpty()) {
                    List<BigInteger> addresses = traceFilterRequest.getFromAddressAsBigIntegers();
                    txStream = txStream.filter(tx -> tx.getSender().getBytes().length > 0 && addresses.contains(HexUtils.forceParseStringHexToBigInteger(tx.getSender().toHexString())));
                }

                if (traceFilterRequest.getToAddress() != null && !traceFilterRequest.getToAddress().isEmpty()) {
                    List<BigInteger> addresses = traceFilterRequest.getToAddressAsBigIntegers();
                    txStream = txStream.filter(tx -> tx.getReceiveAddress().getBytes().length > 0 && addresses.contains(HexUtils.forceParseStringHexToBigInteger(tx.getReceiveAddress().toHexString())));
                }

                txList = txStream.collect(Collectors.toList());
            }

            for (Transaction tx : txList) {
                TransactionInfo txInfo = receiptStore.getInMainChain(tx.getHash().getBytes(), this.blockStore).orElse(null);
                Objects.requireNonNull(txInfo);
                txInfo.setTransaction(tx);

                SummarizedProgramTrace programTrace = (SummarizedProgramTrace) programTraceProcessor.getProgramTrace(tx.getHash());

                if (programTrace == null) {
                    blockTraces.clear();
                    return null;
                }

                List<co.rsk.rpc.modules.trace.TransactionTrace> traces = TraceTransformer.toTraces(programTrace, txInfo, block.getNumber());

                blockTraces.addAll(traces);
            }
        }

        return blockTraces;
    }
}
