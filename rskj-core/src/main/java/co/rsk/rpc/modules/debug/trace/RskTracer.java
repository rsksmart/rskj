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
package co.rsk.rpc.modules.debug.trace;

import co.rsk.core.bc.BlockExecutor;
import co.rsk.crypto.Keccak256;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.modules.debug.TraceOptions;
import co.rsk.util.HexUtils;
import co.rsk.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class RskTracer implements DebugTracer {

    private static final Logger logger = LoggerFactory.getLogger("web3");
    private final ReceiptStore receiptStore;
    private final BlockStore blockStore;
    private final BlockExecutor blockExecutor;
    private final Web3InformationRetriever web3InformationRetriever;

    public RskTracer(
            BlockStore blockStore,
            ReceiptStore receiptStore,
            BlockExecutor blockExecutor,
            Web3InformationRetriever web3InformationRetriever) {
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.blockExecutor = blockExecutor;
        this.web3InformationRetriever = web3InformationRetriever;
    }

    @Override
    public JsonNode traceTransaction(String transactionHash, TraceOptions traceOptions) {
        logger.trace("debug_traceTransaction for txHash: {}", StringUtils.trim(transactionHash));


        byte[] hash = HexUtils.stringHexToByteArray(transactionHash);
        TransactionInfo txInfo = receiptStore.getInMainChain(hash, blockStore).orElse(null);

        if (txInfo == null) {
            logger.trace("No transaction info for txHash: {}", StringUtils.trim(transactionHash));
            return null;
        }

        Block block = blockStore.getBlockByHash(txInfo.getBlockHash());
        Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());
        Transaction tx = block.getTransactionsList().get(txInfo.getIndex());
        txInfo.setTransaction(tx);

        ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor(traceOptions);
        blockExecutor.traceBlock(programTraceProcessor, 0, block, parent.getHeader(), false, false);

        return programTraceProcessor.getProgramTraceAsJsonNode(tx.getHash());
    }

    @Override
    public JsonNode traceBlockByHash(String blockHash, TraceOptions traceOptions) {

        byte[] bHash = HexUtils.stringHexToByteArray(blockHash);
        Block block = blockStore.getBlockByHash(bHash);
        if (block == null) {
            logger.trace("No block is found for blockHash: {}", StringUtils.trim(blockHash));
            return null;
        }

        return traceBlock(block, traceOptions);
    }

    @Override
    public JsonNode traceBlockByNumber(String bnOrId, TraceOptions traceOptions){
        Block block = web3InformationRetriever.getBlock(bnOrId).orElse(null);
        if (block == null) {
            logger.trace("No block is found for bnOrId: {}", StringUtils.trim(bnOrId));
            return null;
        }

        return traceBlock(block, traceOptions);
    }

    @Override
    public TracerType getTracerType() {
        return TracerType.RSK_TRACER;
    }

    private JsonNode traceBlock(Block block, TraceOptions options) {
        Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());

        ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor(options);
        blockExecutor.traceBlock(programTraceProcessor, 0, block, parent.getHeader(), false, false);

        List<Keccak256> txHashes = block.getTransactionsList().stream()
                .map(Transaction::getHash)
                .collect(Collectors.toList());

        return programTraceProcessor.getProgramTracesAsJsonNode(txHashes);
    }
}
