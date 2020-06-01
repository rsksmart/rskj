/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.net.light;

import co.rsk.net.BlockSyncService;
import co.rsk.net.Peer;
import co.rsk.net.messages.BlockReceiptsResponseMessage;
import co.rsk.net.messages.Message;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.TransactionInfo;
import co.rsk.net.messages.TransactionIndexResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;


/**
 * Created by Julian Len and Sebastian Sicardi on 20/10/19.
 */
public class LightProcessor {
    private static final Logger logger = LoggerFactory.getLogger("lightprocessor");
    // keep tabs on which nodes know which blocks.
    private final BlockSyncService blockSyncService;
    private final Blockchain blockchain;

    public LightProcessor(@Nonnull final Blockchain blockchain,
                          @Nonnull final BlockSyncService blockSyncService) {
        this.blockSyncService = blockSyncService;
        this.blockchain = blockchain;
    }

    /**
     * processBlockReceiptsRequest sends the requested block receipts if it is available.
     *
     * @param sender the sender of the BlockReceipts message.
     * @param requestId the id of the request
     * @param blockHash   the requested block hash.
     */
    public void processBlockReceiptsRequest(Peer sender, long requestId, byte[] blockHash) {
        logger.trace("Processing block receipts request {} block {} from {}", requestId, Hex.toHexString(blockHash), sender.getPeerNodeID());
        final Block block = blockSyncService.getBlockFromStoreOrBlockchain(blockHash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        List<TransactionReceipt> receipts = new LinkedList<>();

        for (Transaction tx :  block.getTransactionsList()) {
            TransactionInfo txInfo = blockchain.getTransactionInfo(tx.getHash().getBytes());
            receipts.add(txInfo.getReceipt());
        }

        Message responseMessage = new BlockReceiptsResponseMessage(requestId, receipts);
        sender.sendMessage(responseMessage);
    }

    public void processBlockReceiptsResponse(Peer sender, BlockReceiptsResponseMessage message) {
        throw new UnsupportedOperationException();
    }

    public void processTransactionIndexRequest(Peer sender, long id, byte[] hash) {
        logger.debug("transactionID request Message Received");

        TransactionInfo txinfo = blockchain.getTransactionInfo(hash);

        if (txinfo == null) {
            // Don't waste time sending an empty response.
            return;
        }

        byte[] blockHash = txinfo.getBlockHash();
        long blockNumber = blockchain.getBlockByHash(blockHash).getNumber();
        long txIndex = txinfo.getIndex();

        TransactionIndexResponseMessage response = new TransactionIndexResponseMessage(id, blockNumber, blockHash, txIndex);
        sender.sendMessage(response);
    }

    public void processTransactionIndexResponseMessage(Peer sender, TransactionIndexResponseMessage message) {
        logger.debug("transactionIndex response Message Received");
        logger.debug("ID: " + message.getId());
        logger.debug("BlockHash: " + Hex.toHexString(message.getBlockHash()));
        logger.debug("Blocknumber: " + message.getBlockNumber());
        logger.debug("TxIndex: " + message.getTransactionIndex());
        throw new UnsupportedOperationException();
    }
}
