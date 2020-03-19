/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.net.Peer;
import co.rsk.db.RepositoryLocator;
import co.rsk.net.light.message.BlockReceiptsMessage;
import co.rsk.net.light.message.CodeMessage;
import co.rsk.net.light.message.TestMessage;
import org.ethereum.net.message.Message;
import co.rsk.net.light.message.TransactionIndexMessage;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.LinkedList;
import java.util.List;


/**
 * Created by Julian Len and Sebastian Sicardi on 21/10/19.
 */
public class LightProcessor {
    private static final Logger logger = LoggerFactory.getLogger("lightprocessor");
    // keep tabs on which nodes know which blocks.
    private final BlockStore blockStore;
    private final RepositoryLocator repositoryLocator;
    private final Blockchain blockchain;

    public LightProcessor(@Nonnull final Blockchain blockchain,
                          @Nonnull final BlockStore blockStore,
                          @Nonnull final RepositoryLocator repositoryLocator) {
        this.blockchain = blockchain;
        this.blockStore = blockStore;
        this.repositoryLocator = repositoryLocator;
    }
    /**
     * processBlockReceiptsRequest sends the requested block receipts if it is available.
     * @param requestId the id of the request
     * @param blockHash   the requested block hash.
     * @param msgQueue the queue for send messages
     */
    public void processGetBlockReceiptsMessage(long requestId, byte[] blockHash, MessageQueue msgQueue) {
        logger.trace("Processing block receipts request {} block {}", requestId, Hex.toHexString(blockHash).substring(0,10));
        final Block block = blockStore.getBlockByHash(blockHash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        List<TransactionReceipt> receipts = new LinkedList<>();

        for (Transaction tx :  block.getTransactionsList()) {
            TransactionInfo txInfo = blockchain.getTransactionInfo(tx.getHash().getBytes());
            receipts.add(txInfo.getReceipt());
        }

        Message responseMessage = new BlockReceiptsMessage(requestId, receipts);
        msgQueue.sendMessage(responseMessage);
    }

    public void processBlockReceiptsMessage(long id, List<TransactionReceipt> blockReceipts, MessageQueue msgQueue) {
        throw new UnsupportedOperationException("Not supported BlockReceipt processing");
    }

    public void processGetTransactionIndex(MessageQueue msgqueue, long id, byte[] hash) {
        logger.debug("transactionID request Message Received");

        TransactionInfo txinfo = blockchain.getTransactionInfo(hash);

        if (txinfo == null) {
            // Don't waste time sending an empty response.
            return;
        }

        byte[] blockHash = txinfo.getBlockHash();
        long blockNumber = blockchain.getBlockByHash(blockHash).getNumber();
        long txIndex = txinfo.getIndex();

        TransactionIndexMessage response = new TransactionIndexMessage(id, blockNumber, blockHash, txIndex);
        msgqueue.sendMessage(response);
    }

    public void processTransactionIndexMessage(MessageQueue msgqueue, TransactionIndexMessage message) {
        throw new UnsupportedOperationException("Not supported TransactionIndexMessage processing");
    }

    public void processGetCodeMessage(long requestId, byte[] blockHash, byte[] address, MessageQueue msgQueue) {
        logger.trace("Processing code request {} block {} code {}", requestId, Hex.toHexString(blockHash), Hex.toHexString(address));

        final Block block = blockStore.getBlockByHash(blockHash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        RepositorySnapshot repositorySnapshot = repositoryLocator.snapshotAt(block.getHeader());
        RskAddress addr = new RskAddress(address);
        Keccak256 codeHash = repositorySnapshot.getCodeHash(addr);

        CodeMessage response = new CodeMessage(requestId, codeHash.getBytes());
        msgQueue.sendMessage(response);
    }

    public void processCodeMessage(long id, byte[] codeHash, MessageQueue msgQueue) {
        throw new UnsupportedOperationException("Not supported Code processing");
    }

    public void processTestMessage(TestMessage testMessage, MessageQueue msgQueue) {
        msgQueue.sendMessage(testMessage);
    }
}