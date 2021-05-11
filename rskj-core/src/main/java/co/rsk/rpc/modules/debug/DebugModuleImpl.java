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

import co.rsk.core.bc.BlockExecutor;
import co.rsk.net.MessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.trace.ProgramTraceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

public class DebugModuleImpl implements DebugModule {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    private final MessageHandler messageHandler;
    private final BlockExecutor blockExecutor;

    public DebugModuleImpl(
            BlockStore blockStore,
            ReceiptStore receiptStore,
            MessageHandler messageHandler,
            BlockExecutor blockExecutor) {
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.messageHandler = messageHandler;
        this.blockExecutor = blockExecutor;
    }

    @Override
    public String wireProtocolQueueSize() {
        long n = messageHandler.getMessageQueueSize();
        return TypeConverter.toQuantityJsonHex(n);
    }

    @Override
    public JsonNode traceTransaction(String transactionHash, Map<String, String> traceOptions) throws Exception {
        logger.trace("debug_traceTransaction({}, {})", transactionHash, traceOptions);

        if (traceOptions != null && !traceOptions.isEmpty()) {
            // TODO: implement the logic that takes into account trace options.
            logger.warn("Received {} trace options. For now trace options are being ignored", traceOptions);
        }

        byte[] hash = stringHexToByteArray(transactionHash);
        TransactionInfo txInfo = receiptStore.getInMainChain(hash, blockStore).orElse(null);

        if (txInfo == null) {
            logger.trace("No transaction info for {}", transactionHash);
            return null;
        }

        Block block = blockStore.getBlockByHash(txInfo.getBlockHash());
        Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());
        Transaction tx = block.getTransactionsList().get(txInfo.getIndex());
        txInfo.setTransaction(tx);

        ProgramTraceProcessor programTraceProcessor = new ProgramTraceProcessor();
        blockExecutor.traceBlock(programTraceProcessor, 0, block, parent.getHeader(), false, false);

        return programTraceProcessor.getProgramTraceAsJsonNode(tx.getHash());
    }
}
