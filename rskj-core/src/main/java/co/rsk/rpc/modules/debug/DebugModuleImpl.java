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

package co.rsk.rpc.modules.debug;

import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.crypto.Keccak256;
import co.rsk.net.MessageHandler;
import co.rsk.net.handler.quota.TxQuota;
import co.rsk.net.handler.quota.TxQuotaChecker;
import co.rsk.rpc.Web3InformationRetriever;
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
import java.util.Map;
import java.util.stream.Collectors;

public class DebugModuleImpl implements DebugModule {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    private final MessageHandler messageHandler;
    private final BlockExecutor blockExecutor;

    private final TxQuotaChecker txQuotaChecker;
    private final Web3InformationRetriever web3InformationRetriever;

    public DebugModuleImpl(
            BlockStore blockStore,
            ReceiptStore receiptStore,
            MessageHandler messageHandler,
            BlockExecutor blockExecutor,
            TxQuotaChecker txQuotaChecker,
            Web3InformationRetriever web3InformationRetriever) {
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.messageHandler = messageHandler;
        this.blockExecutor = blockExecutor;
        this.txQuotaChecker = txQuotaChecker;
        this.web3InformationRetriever = web3InformationRetriever;
    }

    @Override
    public String wireProtocolQueueSize() {
        long n = messageHandler.getMessageQueueSize();
        return HexUtils.toQuantityJsonHex(n);
    }

    @Override
    public JsonNode traceTransaction(String transactionHash, Map<String, String> traceOptions) {
        logger.trace("debug_traceTransaction for txHash: {}", StringUtils.trim(transactionHash));

        TraceOptions options = toTraceOptions(traceOptions);

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

        ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor(options);
        blockExecutor.traceBlock(programTraceProcessor, 0, block, parent.getHeader(), false, false);

        return programTraceProcessor.getProgramTraceAsJsonNode(tx.getHash());
    }

    @Override
    public JsonNode traceBlockByHash(String blockHash, Map<String, String> traceOptions) {
        logger.trace("debug_traceBlockByHash for blockHash: {}", StringUtils.trim(blockHash));

        TraceOptions options = toTraceOptions(traceOptions);

        byte[] bHash = HexUtils.stringHexToByteArray(blockHash);
        Block block = blockStore.getBlockByHash(bHash);
        if (block == null) {
            logger.trace("No block is found for blockHash: {}", StringUtils.trim(blockHash));
            return null;
        }

        return traceBlock(block, options);
    }

    @Override
    public JsonNode traceBlockByNumber(String bnOrId, Map<String, String> traceOptions) {
        logger.trace("debug_traceBlockByNumber for bnOrId: {}", StringUtils.trim(bnOrId));

        TraceOptions options = toTraceOptions(traceOptions);

        Block block = web3InformationRetriever.getBlock(bnOrId).orElse(null);
        if (block == null) {
            logger.trace("No block is found for bnOrId: {}", StringUtils.trim(bnOrId));
            return null;
        }

        return traceBlock(block, options);
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

    private TraceOptions toTraceOptions(Map<String, String> traceOptions) {
        TraceOptions options = new TraceOptions(traceOptions);

        if (!options.getUnsupportedOptions().isEmpty()) {
            // TODO: implement the logic that takes into account the remaining trace options.
            logger.warn("Received {} unsupported trace options", options.getUnsupportedOptions().size());
        }

        return options;
    }

    @Override
    public TxQuota accountTransactionQuota(String address) {
        logger.trace("debug_accountTransactionQuota({})", StringUtils.trim(address));
        RskAddress rskAddress = new RskAddress(address);
        return this.txQuotaChecker.getTxQuota(rskAddress);
    }
}
