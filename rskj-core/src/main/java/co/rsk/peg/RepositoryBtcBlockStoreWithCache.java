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
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.util.MaxSizeHashMap;
import java.util.Optional;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
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

    private static final Logger logger = LoggerFactory.getLogger(RepositoryBtcBlockStoreWithCache.class);

    private static final String BLOCK_STORE_CHAIN_HEAD_KEY = "blockStoreChainHead";
    private static final int DEFAULT_MAX_DEPTH_BLOCK_CACHE = 5_000;
    private static final int DEFAULT_MAX_SIZE_BLOCK_CACHE = 10_000;

    private final Repository repository;
    private final RskAddress contractAddress;
    private final NetworkParameters btcNetworkParams;
    private final BridgeConstants bridgeConstants;
    private final BridgeStorageProvider bridgeStorageProvider;
    private final ActivationConfig.ForBlock activations;
    private final int maxDepthBlockCache;
    private final Map<Sha256Hash, StoredBlock> cacheBlocks;

    public RepositoryBtcBlockStoreWithCache(
        NetworkParameters btcNetworkParams,
        Repository repository,
        Map<Sha256Hash, StoredBlock> cacheBlocks,
        RskAddress contractAddress,
        BridgeConstants bridgeConstants,
        BridgeStorageProvider bridgeStorageProvider,
        ForBlock activations) {

        this(
            btcNetworkParams,
            repository,
            cacheBlocks,
            contractAddress,
            bridgeConstants,
            bridgeStorageProvider,
            activations,
            DEFAULT_MAX_DEPTH_BLOCK_CACHE
        );
    }

    public RepositoryBtcBlockStoreWithCache(
        NetworkParameters btcNetworkParams,
        Repository repository,
        Map<Sha256Hash, StoredBlock> cacheBlocks,
        RskAddress contractAddress,
        BridgeConstants bridgeConstants,
        BridgeStorageProvider bridgeStorageProvider,
        ForBlock activations,
        int maxDepthBlockCache) {

        this.cacheBlocks = cacheBlocks;
        this.repository = repository;
        this.contractAddress = contractAddress;
        this.btcNetworkParams = btcNetworkParams;
        this.bridgeConstants = bridgeConstants;
        this.bridgeStorageProvider = bridgeStorageProvider;
        this.activations = activations;
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
        logger.trace("[get] Looking in storage for block with hash {}", hash);
        byte[] ba = repository.getStorageBytes(contractAddress, DataWord.valueFromHex(hash.toString()));
        if (ba == null) {
            logger.trace("[get] Block with hash {} not found in storage", hash);
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
        setMainChainBlock(newChainHead.getHeight(), newChainHead.getHeader().getHash());
    }

    @Override
    public Optional<StoredBlock> getInMainchain(int height) {
        Optional<Sha256Hash> bestBlockHash = bridgeStorageProvider.getBtcBestBlockHashByHeight(height);
        if (!bestBlockHash.isPresent()) {
            logger.trace("[getInMainchain] Block at height {} not present in storage", height);
            return Optional.empty();
        }

        StoredBlock block = get(bestBlockHash.get());
        if (block == null) {
            logger.trace("[getInMainchain] Block with hash {} not found in storage", bestBlockHash.get());
            return Optional.empty();
        }

        logger.trace("[getInMainchain] Found block with hash {} at height {}", bestBlockHash.get(), height);
        return Optional.of(block);
    }

    @Override
    public void setMainChainBlock(int height, Sha256Hash blockHash) {
        logger.trace("[setMainChainBlock] Set block with hash {} at height {}", blockHash, height);
        bridgeStorageProvider.setBtcBestBlockHashByHeight(height, blockHash);
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
            logger.trace("[getFromCache] Block with hash {} not found in cache", branchBlockHash);
            return null;
        }
        return cacheBlocks.get(branchBlockHash);
    }

    @Override
    public StoredBlock getStoredBlockAtMainChainHeight(int height) throws BlockStoreException {
        StoredBlock chainHead = getChainHead();
        int depth = chainHead.getHeight() - height;
        logger.trace("Getting btc block at depth: {}", depth);

        if (depth < 0) {
            String message = String.format(
                "Height provided is higher than chain head. provided: %n. chain head: %n",
                height,
                chainHead.getHeight()
            );
            logger.trace("[getStoredBlockAtMainChainHeight] {}", message);
            throw new BlockStoreException(message);
        }

        if (activations.isActive(ConsensusRule.RSKIP199)) {
            int btcHeightWhenBlockIndexActivates = this.bridgeConstants.getBtcHeightWhenBlockIndexActivates();
            int maxDepthToSearch = this.bridgeConstants.getMaxDepthToSearchBlocksBelowIndexActivation();
            int limit;
            if (chainHead.getHeight() - btcHeightWhenBlockIndexActivates > maxDepthToSearch) {
                limit = btcHeightWhenBlockIndexActivates;
            } else {
                limit = chainHead.getHeight() - maxDepthToSearch;
            }
            logger.trace("[getStoredBlockAtMainChainHeight] Chain head height is {} and the depth limit {}", chainHead.getHeight(), limit);

            if (height < limit) {
                String message = String.format(
                    "Height provided is lower than the depth limit defined to search for blocks. Provided: %n, limit: %n",
                    height,
                    limit
                );
                logger.trace("[getStoredBlockAtMainChainHeight] {}", message);
                throw new BlockStoreException(message);
            }
        }

        StoredBlock block;
        Optional<StoredBlock> blockOptional = getInMainchain(height);
        if (blockOptional.isPresent()) {
            block = blockOptional.get();
        } else {
            block = getStoredBlockAtMainChainDepth(depth);
        }

        return block;
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
    @Deprecated
    public StoredBlock getStoredBlockAtMainChainDepth(int depth) throws BlockStoreException {
        logger.trace("[getStoredBlockAtMainChainDepth] Looking for block at depth {}", depth);
        StoredBlock chainHead = getChainHead();
        Sha256Hash blockHash = chainHead.getHeader().getHash();

        for (int i = 0; i < depth && blockHash != null; i++) {
            //If its older than cache go to disk
            StoredBlock currentBlock = getFromCache(blockHash);
            if (currentBlock == null) {
                logger.trace("[getStoredBlockAtMainChainDepth] Block with hash {} not in cache, getting from disk", blockHash);
                currentBlock = get(blockHash);
                if (currentBlock == null) {
                    return null;
                }
            }
            blockHash = currentBlock.getHeader().getPrevBlockHash();
        }

        if (blockHash == null) {
            logger.trace("[getStoredBlockAtMainChainDepth] Block not found");
            return null;
        }
        StoredBlock block = getFromCache(blockHash);
        if (block == null) {
            block = get(blockHash);
        }
        int expectedHeight = chainHead.getHeight() - depth;
        if (block != null && block.getHeight() != expectedHeight) {
            String message = String.format("Block %s at depth %d Height is %d but should be %d",
                block.getHeader().getHash(),
                depth,
                block.getHeight(),
                expectedHeight
            );
            logger.trace("[getStoredBlockAtMainChainDepth] {}", message);
            throw new BlockStoreException(message);
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
        public BtcBlockStoreWithCache newInstance(
            Repository track,
            BridgeConstants bridgeConstants,
            BridgeStorageProvider bridgeStorageProvider,
            ActivationConfig.ForBlock activations) {

            return new RepositoryBtcBlockStoreWithCache(
                btcNetworkParams,
                track,
                cacheBlocks,
                contractAddress,
                bridgeConstants,
                bridgeStorageProvider,
                activations,
                this.maxDepthBlockCache
            );
        }
    }
}
