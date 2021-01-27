/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.util.MaxSizeHashMap;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;


/**
 * Implementation of a bitcoinj blockstore that persists to RSK's Repository
 *
 * @author Oscar Guindzberg
 */
public class RepositoryBtcBlockStoreWithCache implements BtcBlockStoreWithCache {

    private static final Logger logger = LoggerFactory.getLogger("btcBlockStore");

    private static final String BLOCK_STORE_CHAIN_HEAD_KEY = "blockStoreChainHead";
    private static final int DEFAULT_MAX_DEPTH_BLOCK_CACHE = 5_000;
    private static final int DEFAULT_MAX_SIZE_BLOCK_CACHE = 10_000;

    private final Repository repository;
    private final RskAddress contractAddress;
    private final NetworkParameters btcNetworkParams;
    private final int maxDepthBlockCache;
    private final Map<Sha256Hash, StoredBlock> cacheBlocks;

    public RepositoryBtcBlockStoreWithCache(NetworkParameters btcNetworkParams, Repository repository, Map<Sha256Hash, StoredBlock> cacheBlocks, RskAddress contractAddress) {
        this(btcNetworkParams, repository, cacheBlocks, contractAddress, DEFAULT_MAX_DEPTH_BLOCK_CACHE);
    }

    public RepositoryBtcBlockStoreWithCache(NetworkParameters btcNetworkParams, Repository repository, Map<Sha256Hash, StoredBlock> cacheBlocks, RskAddress contractAddress, int maxDepthBlockCache) {
        this.cacheBlocks = cacheBlocks;
        this.repository = repository;
        this.contractAddress = contractAddress;
        this.btcNetworkParams = btcNetworkParams;
        this.maxDepthBlockCache = maxDepthBlockCache;
        checkIfInitialized();
    }

    @Override
    public synchronized void put(StoredBlock storedBlock) {
        Sha256Hash hash = storedBlock.getHeader().getHash();
        byte[] ba = storedBlockToByteArray(storedBlock);
        repository.addStorageBytes(contractAddress, DataWord.valueFromHex(hash.toString()), ba);
        if (cacheBlocks != null) {
            StoredBlock chainHead = getChainHead();
            if (chainHead == null || chainHead.getHeight() - storedBlock.getHeight() < this.maxDepthBlockCache) {
                cacheBlocks.put(storedBlock.getHeader().getHash(), storedBlock);
            }
        }
    }

    @Override
    public synchronized StoredBlock get(Sha256Hash hash) {
        byte[] ba = repository.getStorageBytes(contractAddress, DataWord.valueFromHex(hash.toString()));
        if (ba == null) {
            return null;
        }
        StoredBlock storedBlock = byteArrayToStoredBlock(ba);
        return storedBlock;
    }

    @Override
    public synchronized StoredBlock getChainHead() {
        byte[] ba = repository.getStorageBytes(contractAddress, DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY));
        if (ba == null) {
            return null;
        }
        return byteArrayToStoredBlock(ba);
    }

    @Override
    public synchronized void setChainHead(StoredBlock newChainHead) {
        logger.trace("Set new chain head with height: {}.", newChainHead.getHeight());
        byte[] ba = storedBlockToByteArray(newChainHead);
        repository.addStorageBytes(contractAddress, DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY), ba);
        if (cacheBlocks != null) {
            populateCache(newChainHead);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public NetworkParameters getParams() {
        return btcNetworkParams;
    }

    @Override
    public StoredBlock getFromCache(Sha256Hash branchBlockHash) {
        if (cacheBlocks == null) {
            return null;
        }
        return cacheBlocks.get(branchBlockHash);
    }

    @Override
    public StoredBlock getStoredBlockAtMainChainHeight(int height) throws BlockStoreException {
        StoredBlock chainHead = getChainHead();
        int depth = chainHead.getHeight() - height;
        logger.trace("Getting btc block at depth: {}", depth);
        return getStoredBlockAtMainChainDepth(depth);
    }

    private synchronized void populateCache(StoredBlock chainHead) {
        logger.trace("Populating BTC Block Store Cache.");
        if (this.btcNetworkParams.getGenesisBlock().equals(chainHead.getHeader())) {
            return;
        }
        cacheBlocks.put(chainHead.getHeader().getHash(), chainHead);
        Sha256Hash blockHash = chainHead.getHeader().getPrevBlockHash();
        int depth = this.maxDepthBlockCache - 1;
        while (blockHash != null && depth > 0) {
            if (cacheBlocks.get(blockHash) != null) {
                break;
            }
            StoredBlock currentBlock = get(blockHash);
            if (currentBlock == null) {
                break;
            }
            cacheBlocks.put(currentBlock.getHeader().getHash(), currentBlock);
            depth--;
            blockHash = currentBlock.getHeader().getPrevBlockHash();
        }
        logger.trace("END Populating BTC Block Store Cache.");
    }

    @Override
    public StoredBlock getStoredBlockAtMainChainDepth(int depth) throws BlockStoreException {
        StoredBlock chainHead = getChainHead();
        Sha256Hash blockHash = chainHead.getHeader().getHash();

        for (int i = 0; i < depth && blockHash != null; i++) {
            //If its older than cache go to disk
            StoredBlock currentBlock = getFromCache(blockHash);
            if (currentBlock == null) {
                logger.trace("Missing cache (depth={}/{}), getting from store.", i, depth);
                currentBlock = get(blockHash);
                if (currentBlock == null) {
                    return null;
                }
            }
            blockHash = currentBlock.getHeader().getPrevBlockHash();
        }

        if (blockHash == null) {
            return null;
        }
        StoredBlock block = getFromCache(blockHash);
        if (block == null) {
            block = get(blockHash);
        }
        int expectedHeight = chainHead.getHeight() - depth;
        if (block != null && block.getHeight() != expectedHeight) {
            throw new BlockStoreException(String.format("Block %s at depth %d Height is %d but should be %d",
                    block.getHeader().getHash(),
                    depth,
                    block.getHeight(),
                    expectedHeight));
        }


        return block;
    }

    private byte[] storedBlockToByteArray(StoredBlock block) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(128);
        block.serializeCompact(byteBuffer);
        byte[] ba = new byte[byteBuffer.position()];
        byteBuffer.flip();
        byteBuffer.get(ba);
        return ba;
    }

    private StoredBlock byteArrayToStoredBlock(byte[] ba) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(ba);
        return StoredBlock.deserializeCompact(btcNetworkParams, byteBuffer);
    }

    private void checkIfInitialized() {
        if (getChainHead() == null) {
            BtcBlock genesisHeader = this.btcNetworkParams.getGenesisBlock().cloneAsHeader();
            StoredBlock storedGenesis = new StoredBlock(genesisHeader, genesisHeader.getWork(), 0);
            put(storedGenesis);
            setChainHead(storedGenesis);
        }
    }

    public static class Factory implements BtcBlockStoreWithCache.Factory {

        private final int maxSizeBlockCache;
        //This is ok as we don't have parallel execution, in the feature we should move to a concurrentHashMap
        private final Map<Sha256Hash, StoredBlock> cacheBlocks;
        private final RskAddress contractAddress;
        private final NetworkParameters btcNetworkParams;
        private final int maxDepthBlockCache;

        @VisibleForTesting
        public Factory(NetworkParameters btcNetworkParams) {
            this(btcNetworkParams, DEFAULT_MAX_DEPTH_BLOCK_CACHE, DEFAULT_MAX_SIZE_BLOCK_CACHE);
        }

        public Factory(NetworkParameters btcNetworkParams, int maxDepthBlockCache, int maxSizeBlockCache) {
            this.contractAddress = PrecompiledContracts.BRIDGE_ADDR;
            this.btcNetworkParams = btcNetworkParams;
            this.maxDepthBlockCache = maxDepthBlockCache;
            this.maxSizeBlockCache = maxSizeBlockCache;
            this.cacheBlocks = new MaxSizeHashMap<>(this.maxSizeBlockCache, true);

            if (this.maxDepthBlockCache > this.maxSizeBlockCache) {
                logger.warn("Max depth ({}) is greater than Max Size ({}). This could lead to a misbehaviour.", this.maxDepthBlockCache, this.maxSizeBlockCache);
            }

            if (this.maxDepthBlockCache < DEFAULT_MAX_DEPTH_BLOCK_CACHE) {
                logger.warn("Max depth ({}) is lower than the default ({}). This could lead to a misbehaviour.", this.maxDepthBlockCache, DEFAULT_MAX_DEPTH_BLOCK_CACHE);
            }
        }

        @Override
        public BtcBlockStoreWithCache newInstance(Repository track) {
            return new RepositoryBtcBlockStoreWithCache(btcNetworkParams, track, cacheBlocks, contractAddress, this.maxDepthBlockCache);
        }
    }

}
