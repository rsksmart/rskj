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
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.net.light.message.*;
import com.google.common.annotations.VisibleForTesting;
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
import static java.util.stream.LongStream.*;

/**
 * Created by Julian Len and Sebastian Sicardi on 21/10/19.
 */
public class LightProcessor {
    private static final Logger logger = LoggerFactory.getLogger("lightprocessor");
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
     * @param id the id of the request
     * @param blockHash the requested block hash.
     * @param lightPeer the connected peer
     */
    public void processGetBlockReceiptsMessage(long id, byte[] blockHash, LightPeer lightPeer) {
        String blockHashLog = Hex.toHexString(blockHash);
        logger.trace("Processing block receipts request {} block {}", id, blockHashLog);
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

        Message responseMessage = new BlockReceiptsMessage(id, receipts);
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

    public void processGetCodeMessage(long id, byte[] blockHash, byte[] address, LightPeer lightPeer) {
        String blockHashLog = Hex.toHexString(blockHash);
        String addressLog = Hex.toHexString(address);
        logger.trace("Processing code request {} block {} code {}", id, blockHashLog, addressLog);

        final Block block = blockStore.getBlockByHash(blockHash);

        if (block == null) {
            // Don't waste time sending an empty response.
            return;
        }

        RepositorySnapshot repositorySnapshot = repositoryLocator.snapshotAt(block.getHeader());
        RskAddress addr = new RskAddress(address);
        byte[] bytecode = repositorySnapshot.getCode(addr);

        if (bytecode == null) {
            // Don't waste time sending an empty response.
            return;
        }
        CodeMessage response = new CodeMessage(id, bytecode);
        lightPeer.sendMessage(response);
    }

    public void processCodeMessage(long id, byte[] bytecode, LightPeer lightPeer) {
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

        // TODO: ssicardi: Merkle proof getter and validator
        AccountsMessage response = new AccountsMessage(id, new byte[] {0x00}, state.getNonce().longValue(),
                state.getBalance().asBigInteger().longValue(), repositorySnapshot.getCodeHash(address).getBytes(),
                repositorySnapshot.getRoot());

        lightPeer.sendMessage(response);
    }

    public void processAccountsMessage(long id, byte[] merkleInclusionProof, long nonce, long balance,
                                       byte[] codeHash, byte[] storageRoot, LightPeer lightPeer) {
        throw new UnsupportedOperationException("Not supported AccountsMessage processing");
    }

    public void processGetBlockHeadersByHashMessage(long id, byte[] startBlockHash, int max, int skip, boolean reverse, LightPeer lightPeer) {
        String blockHashLog = Hex.toHexString(startBlockHash);
        logger.trace("Processing block header request {} block {} from {}", id, blockHashLog, lightPeer.getPeerIdShort());

        Block startBlock = blockStore.getBlockByHash(startBlockHash);
        processGetBlockHeaderMessage(id, max, skip, reverse, lightPeer, startBlock);
    }

    public void processGetBlockHeadersByNumberMessage(long id, long blockNumber, int max, int skip, boolean reverse, LightPeer lightPeer) {
        logger.trace("Processing block header request {} block {} from {}", id, blockNumber, lightPeer.getPeerIdShort());

        Block startBlock = blockStore.getChainBlockByNumber(blockNumber);
        processGetBlockHeaderMessage(id, max, skip, reverse, lightPeer, startBlock);
    }

    private void processGetBlockHeaderMessage(long id, int max, int skip, boolean reverse, LightPeer lightPeer, Block startBlock) {
        if (max == 0) {
            return;
        }

        if (startBlock == null) {
            return;
        }

        List<BlockHeader> headers = new ArrayList<>();

        if (max == 1) {
            headers.add(startBlock.getHeader());
            BlockHeadersMessage response = new BlockHeadersMessage(id, headers);
            lightPeer.sendMessage(response);
            return;
        }

        headers = getBlockNumbersToResponse(max, skip, reverse, startBlock.getNumber(), blockStore.getBestBlock());

        if (headers.isEmpty()) {
            return;
        }

        BlockHeadersMessage response = new BlockHeadersMessage(id, headers);
        lightPeer.sendMessage(response);
    }

    @VisibleForTesting
    public List<BlockHeader> getBlockNumbersToResponse(int max, int skip, boolean reverse, long startNumber, Block bestBlock) {
        ArrayList<BlockHeader> headers = new ArrayList<>();

        long[] nums = range(0, max).map(num -> num * (skip + 1)).toArray();

        for (long num : nums) {

            if ((reverse && startNumber <= num) ||
                    (!reverse && num > bestBlock.getNumber() - startNumber)) {
                continue;
            }

            Block b;
            if (reverse) {
                b = blockStore.getChainBlockByNumber(startNumber - num);
            } else {
                b = blockStore.getChainBlockByNumber(startNumber + num);
            }

            if (b == null){
                continue;
            }
            headers.add(b.getHeader());
        }

        return headers;
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

        // TODO: ssicardi: Merkle proof getter and validator
        StorageMessage response = new StorageMessage(id, new byte[] {0x00}, storageValue);
        lightPeer.sendMessage(response);
    }

    public void processStorageMessage(long id, byte[] merkleInclusionProof, byte[] storageValue, LightPeer lightPeer) {
        throw new UnsupportedOperationException("Not supported Storage processing");
    }
}