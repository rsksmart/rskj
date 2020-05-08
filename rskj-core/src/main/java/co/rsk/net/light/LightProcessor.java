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
import co.rsk.db.RepositorySnapshot;
import co.rsk.db.RepositoryLocator;
import co.rsk.net.light.message.*;
import org.ethereum.core.*;
import org.ethereum.net.message.Message;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
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
     * @param lightPeer
     */
    public void processGetBlockReceiptsMessage(long requestId, byte[] blockHash, LightPeer lightPeer) {
        String blockHashLog = Hex.toHexString(blockHash);
        logger.trace("Processing block receipts request {} block {}", requestId, blockHashLog);
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
        lightPeer.sendMessage(responseMessage);
    }

    public void processBlockReceiptsMessage(long id, List<TransactionReceipt> blockReceipts, LightPeer lightPeer) {
        throw new UnsupportedOperationException("Not supported BlockReceipt processing");
    }

    public void processGetTransactionIndex(long id, byte[] hash, LightPeer lightPeer) {
        logger.debug("Get Transaction Index Message Received");

        TransactionInfo txinfo = blockchain.getTransactionInfo(hash);

        if (txinfo == null) {
            // Don't waste time sending an empty response.
            return;
        }

        byte[] blockHash = txinfo.getBlockHash();
        long blockNumber = blockchain.getBlockByHash(blockHash).getNumber();
        long txIndex = txinfo.getIndex();

        TransactionIndexMessage response = new TransactionIndexMessage(id, blockNumber, blockHash, txIndex);
        lightPeer.sendMessage(response);
    }

    public void processTransactionIndexMessage(long id, long blockNumber, byte[] blockHash, long txIndex, LightPeer lightPeer) {
        throw new UnsupportedOperationException("Not supported TransactionIndexMessage processing");
    }

    public void processGetCodeMessage(long requestId, byte[] blockHash, byte[] address, LightPeer lightPeer) {
        String blockHashLog = Hex.toHexString(blockHash);
        String addressLog = Hex.toHexString(address);
        logger.trace("Processing code request {} block {} code {}", requestId, blockHashLog, addressLog);

        final Block block = blockStore.getBlockByHash(blockHash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        RepositorySnapshot repositorySnapshot = repositoryLocator.snapshotAt(block.getHeader());
        RskAddress addr = new RskAddress(address);
        Keccak256 codeHash = repositorySnapshot.getCodeHash(addr);

        CodeMessage response = new CodeMessage(requestId, codeHash.getBytes());
        lightPeer.sendMessage(response);
    }

    public void processCodeMessage(long id, byte[] codeHash, LightPeer lightPeer) {
        throw new UnsupportedOperationException("Not supported Code processing");
    }

    public void processGetAccountsMessage(long id, byte[] blockHash, byte[] addressHash, LightPeer lightPeer) {
        logger.debug("Get Accounts Message Received: id {}, blockhash: {}, addressHash {}", id, blockHash, addressHash);

        final Block block = blockStore.getBlockByHash(blockHash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        RepositorySnapshot repositorySnapshot = repositoryLocator.snapshotAt(block.getHeader());
        RskAddress address = new RskAddress(addressHash);
        AccountState state = repositorySnapshot.getAccountState(address);

        AccountsMessage response = new AccountsMessage(id, new byte[] {0x00}, state.getNonce().longValue(),
                state.getBalance().asBigInteger().longValue(), repositorySnapshot.getCodeHash(address).getBytes(),
                repositorySnapshot.getRoot());

        lightPeer.sendMessage(response);
    }

    public void processAccountsMessage(long id, byte[] merkleInclusionProof, long nonce, long balance,
                                       byte[] codeHash, byte[] storageRoot, LightPeer lightPeer) {
        throw new UnsupportedOperationException("Not supported AccountsMessage processing");
    }

    public void processGetBlockHeaderMessage(long id, byte[] blockHash, LightPeer lightPeer) {
        String blockHashLog = Hex.toHexString(blockHash);
        logger.trace("Processing block header request {} block {}", id, blockHashLog);

        final Block block = blockStore.getBlockByHash(blockHash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        BlockHeaderMessage response = new BlockHeaderMessage(id, block.getHeader());
        lightPeer.sendMessage(response);
    }

    public void processGetBlockBodyMessage(long id, byte[] blockHash, LightPeer lightPeer) {
        String logBlockHash = Hex.toHexString(blockHash);
        logger.trace("Processing block header request {} block {}", id, logBlockHash);

        final Block block = blockStore.getBlockByHash(blockHash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        BlockBodyMessage response = new BlockBodyMessage(id, block.getTransactionsList(), block.getUncleList());
        lightPeer.sendMessage(response);
    }

    public void processBlockBodyMessage(long id, List<BlockHeader> uncles, List<Transaction> transactions, LightPeer lightPeer) {
        throw new UnsupportedOperationException("Not supported BlockBody processing");
    }

    public void processGetStorageMessage(long id, byte[] blockHash, byte[] addressHash, byte[] storageKeyHash, LightPeer lightPeer) {
        String logBlockHash = Hex.toHexString(blockHash);
        String logAddressHash = Hex.toHexString(addressHash);
        String logStrorageKey = Hex.toHexString(storageKeyHash);
        logger.trace("Processing storage request {} block {} address {} storage key {}", id,
                logBlockHash, logAddressHash, logStrorageKey);
        final Block block = blockStore.getBlockByHash(blockHash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }
        RepositorySnapshot repositorySnapshot = repositoryLocator.snapshotAt(block.getHeader());
        RskAddress address = new RskAddress(addressHash);
        byte[] storageValue = repositorySnapshot.getStorageBytes(address, DataWord.valueOf(storageKeyHash));

        if (storageValue == null) {
            // Don't waste time sending an empty response.
            return;
        }

        StorageMessage response = new StorageMessage(id, new byte[] {0x00}, storageValue);
        lightPeer.sendMessage(response);
    }

    public void processStorageMessage(long id, byte[] merkleInclusionProof, byte[] storageValue, LightPeer lightPeer) {
        throw new UnsupportedOperationException("Not supported Storage processing");
    }
}