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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.rsk.config.VmConfig;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.util.HexUtils;

public class TraceModuleImpl implements TraceModule {

    private static final String EARLIEST_BLOCK = "earliest";
    private static final String LATEST_BLOCK = "latest";
    private static final String PENDING_BLOCK = "pending";
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private static final ObjectMapper OBJECT_MAPPER = Serializers.createMapper(true);

    private final Blockchain blockchain;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    private final BlockExecutor blockExecutor;
    private final ExecutionBlockRetriever executionBlockRetriever;

    public TraceModuleImpl(
            Blockchain blockchain,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            BlockExecutor blockExecutor,
            ExecutionBlockRetriever executionBlockRetriever) {
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.blockExecutor = blockExecutor;
        this.executionBlockRetriever = executionBlockRetriever;
    }

    @Override
    public JsonNode traceTransaction(String transactionHash) throws Exception {
        logger.trace("trace_transaction({})", transactionHash);

        byte[] hash = HexUtils.stringHexToByteArray(transactionHash);
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

        Optional<List<TransactionTrace>> blockTraces = buildBlockTraces(block);

        return !blockTraces.isPresent() ? null : OBJECT_MAPPER.valueToTree(blockTraces.get());
    }

    @Override
    public JsonNode traceFilter(TraceFilterRequest traceFilterRequest) throws Exception {
        List<List<TransactionTrace>> blockTracesGroup = new ArrayList<>();

        Block fromBlock = getBlockByTagOrNumber(traceFilterRequest.getFromBlock(), traceFilterRequest.getFromBlockNumber());
        Block block = getBlockByTagOrNumber(traceFilterRequest.getToBlock(), traceFilterRequest.getToBlockNumber());

        block = block == null ? blockchain.getBestBlock() : block;

        while (fromBlock != null && block != null && block.getNumber() >= fromBlock.getNumber()) {
            Optional<List<TransactionTrace>> builtTraces = buildBlockTraces(block, traceFilterRequest);

            if (builtTraces.isPresent()) {
                blockTracesGroup.add(builtTraces.get());
            }

            block = this.blockchain.getBlockByHash(block.getParentHash().getBytes());
        }

        Collections.reverse(blockTracesGroup);

        Stream<TransactionTrace> txTraceStream = blockTracesGroup.stream().flatMap(Collection::stream);

        if (traceFilterRequest.getAfter() != null) {
            txTraceStream = txTraceStream.skip(traceFilterRequest.getAfter());
        }

        if (traceFilterRequest.getCount() != null) {
            txTraceStream = txTraceStream.limit(traceFilterRequest.getCount());
        }

        List<TransactionTrace> traces = txTraceStream.collect(Collectors.toList());

        return OBJECT_MAPPER.valueToTree(traces);
    }

    @Override
    public JsonNode traceGet(String transactionHash, List<String> positions) throws Exception {
        TraceGetRequest request = new TraceGetRequest(transactionHash, positions);

        TransactionInfo txInfo = this.receiptStore.getInMainChain(request.getTransactionHashAsByteArray(), this.blockStore).orElse(null);

        if (txInfo == null) {
            logger.trace("No transaction info for {}", transactionHash);
            return null;
        }

        Block block = this.blockchain.getBlockByHash(txInfo.getBlockHash());

        List<TransactionTrace> traces = buildBlockTraces(block);

        TransactionTrace transactionTrace = null;
        if(traces != null) {
            transactionTrace = traces.get(request.getTracePositionsAsListOfIntegers().get(0));
        }
        
        return OBJECT_MAPPER.valueToTree(transactionTrace);
    }

    private Block getByJsonArgument(String arg) {
        if (arg.length() < 20) {
            return this.getByJsonBlockId(arg);
        } else {
            return this.getByJsonBlockHash(arg);
        }
    }

    private Block getByJsonBlockHash(String arg) {
        byte[] hash = HexUtils.stringHexToByteArray(arg);

        return this.blockchain.getBlockByHash(hash);
    }

    private Block getByJsonBlockId(String id) {
        if (EARLIEST_BLOCK.equalsIgnoreCase(id)) {
            return this.blockchain.getBlockByNumber(0);
        } else if (LATEST_BLOCK.equalsIgnoreCase(id)) {
            return this.blockchain.getBestBlock();
        } else if (PENDING_BLOCK.equalsIgnoreCase(id)) {
            throw RskJsonRpcRequestException.unimplemented("The method don't support 'pending' as a parameter yet");
        } else {
            try {
                long blockNumber = HexUtils.stringHexToBigInteger(id).longValue();
                return this.blockchain.getBlockByNumber(blockNumber);
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                logger.warn("Exception in getBlockByNumber", e);
                throw RskJsonRpcRequestException.invalidParamError("invalid blocknumber " + id);
            }
        }
    }

    private Optional<List<TransactionTrace>> buildBlockTraces(Block block) {
        return buildBlockTraces(block, null);
    }

    private List<co.rsk.rpc.modules.trace.TransactionTrace> prepareTxTraces(Transaction tx, ProgramTraceProcessor programTraceProcessor, long blockNumber) {
        TransactionInfo txInfo = receiptStore.getInMainChain(tx.getHash().getBytes(), this.blockStore).orElse(null);
        Objects.requireNonNull(txInfo);
        txInfo.setTransaction(tx);

        SummarizedProgramTrace programTrace = (SummarizedProgramTrace) programTraceProcessor.getProgramTrace(tx.getHash());

        if (programTrace == null) return null;

        List<co.rsk.rpc.modules.trace.TransactionTrace> traces = TraceTransformer.toTraces(programTrace, txInfo, blockNumber);

        return traces;
    }

    private Optional<List<TransactionTrace>> buildBlockTraces(Block block, TraceFilterRequest traceFilterRequest) {
        List<TransactionTrace> blockTraces = new ArrayList<>();

        if (block != null && block.getNumber() != 0) {
            List<Transaction> txList = block.getTransactionsList();

            ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor();
            Block parent = this.blockchain.getBlockByHash(block.getParentHash().getBytes());
            this.blockExecutor.traceBlock(programTraceProcessor, VmConfig.LIGHT_TRACE, block, parent.getHeader(), false, false);

            if (traceFilterRequest != null) {
                Stream<Transaction> txStream = block.getTransactionsList().stream();

                if (traceFilterRequest.getFromAddress() != null && !traceFilterRequest.getFromAddress().isEmpty()) {
                    List<RskAddress> addresses = traceFilterRequest.getFromAddressAsRskAddresses();
                    txStream = txStream.filter(tx -> tx.getSender().getBytes().length > 0 && addresses.contains(tx.getSender()));
                }

                if (traceFilterRequest.getToAddress() != null && !traceFilterRequest.getToAddress().isEmpty()) {
                    List<RskAddress> addresses = traceFilterRequest.getToAddressAsRskAddresses();
                    txStream = txStream.filter(tx -> tx.getReceiveAddress().getBytes().length > 0 && addresses.contains(tx.getReceiveAddress()));
                }

                txList = txStream.collect(Collectors.toList());
            }

            for (Transaction tx : txList) {
                List<co.rsk.rpc.modules.trace.TransactionTrace> traces = prepareTxTraces(tx, programTraceProcessor, block.getNumber());
                if (traces == null) {
                    blockTraces.clear();
                    return Optional.empty();
                }

                blockTraces.addAll(traces);
            }
        }

        return Optional.of(blockTraces);
    }

    private Block getBlockByTagOrNumber(String strBlock, BigInteger biBlock) {
        if (strBlock.equalsIgnoreCase(LATEST_BLOCK)) {
            return this.blockchain.getBestBlock();
        } else if (strBlock.equalsIgnoreCase(EARLIEST_BLOCK)) {
            return this.blockchain.getBlockByNumber(0);
        } else if (strBlock.equalsIgnoreCase(PENDING_BLOCK)) {
            return this.executionBlockRetriever.retrieveExecutionBlock(PENDING_BLOCK).getBlock();
        } else {
            long toBlockNumber = biBlock.longValue();
            return this.blockchain.getBlockByNumber(toBlockNumber);
        }
    }
}
