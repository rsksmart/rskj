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

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Implementation of a bitcoinj blockstore that persists to RSK's Repository
 * @author Oscar Guindzberg
 */
public class RepositoryBlockStore implements BtcBlockStoreWithCache {

    public static final String BLOCK_STORE_CHAIN_HEAD_KEY = "blockStoreChainHead";

    public static final int MAX_SIZE_MAP_STORED_BLOCKS = 50_000;
    private final Map<Sha256Hash, StoredBlock> knownBlocks;

    private final Repository repository;
    private final RskAddress contractAddress;

    private final NetworkParameters params;

    public static final int MAX_SIZE_MAP_INDEXED_BLOCKS = 5000;
    private final Map<Integer, Map<Sha256Hash,BtcBlockInfo>> index;
    private BtcBlockInfo chainHeadInfo;


    public RepositoryBlockStore(BridgeConstants bridgeConstants, Repository repository, RskAddress contractAddress) {
        this.knownBlocks = new MaxSizeHashMap<>(MAX_SIZE_MAP_STORED_BLOCKS, true);
        this.index = new MaxSizeHashMap<>(MAX_SIZE_MAP_INDEXED_BLOCKS, true);
        this.chainHeadInfo = null;
        this.repository = repository;
        this.contractAddress = contractAddress;

        // Insert the genesis block.
        try {
            this.params = bridgeConstants.getBtcParams();
            if (getChainHead()==null) {
                BtcBlock genesisHeader = params.getGenesisBlock().cloneAsHeader();
                StoredBlock storedGenesis = new StoredBlock(genesisHeader, genesisHeader.getWork(), 0);
                put(storedGenesis);
                setChainHead(storedGenesis);
            }
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);  // Cannot happen.
        } catch (VerificationException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    @Override
    public synchronized void put(StoredBlock block) throws BlockStoreException {
        Sha256Hash hash = block.getHeader().getHash();
        byte[] ba = storedBlockToByteArray(block);
        repository.addStorageBytes(contractAddress, DataWord.valueFromHex(hash.toString()), ba);

        storeBlockInCache(block, false);
    }


    @Override
    public int getMinIndexedBlockHeight() {
        int minIndexedBlockHeight = this.chainHeadInfo.getHeight() - MAX_SIZE_MAP_INDEXED_BLOCKS;
        if(minIndexedBlockHeight<0) {
            minIndexedBlockHeight = 0;
        }
        return minIndexedBlockHeight;
    }

    public static class BtcBlockInfo implements Serializable {
        private static final long serialVersionUID = 6706746350128478753L;

        private Sha256Hash hash;
        private Sha256Hash prevHash;
        private int height;
        private boolean mainChain;
        BtcBlockInfo(Sha256Hash hash, Sha256Hash prevHash, int height) {
            this.hash = hash;
            this.prevHash = prevHash;
            this.height = height;
            this.mainChain = false;
        }
        public int getHeight() { return height; }

        public Sha256Hash getHash() { return hash; }

        public Sha256Hash getPreviousHash() { return prevHash; }

        public boolean isMainChain() { return mainChain; }

        public void setMainChain(boolean mainChain) {
            this.mainChain = mainChain;
        }
    }

    @Override
    public synchronized StoredBlock get(Sha256Hash hash) throws BlockStoreException {
        StoredBlock storedBlock = knownBlocks.get(hash);

        if (storedBlock != null) {
            return storedBlock;
        }

        byte[] ba = repository.getStorageBytes(contractAddress, DataWord.valueFromHex(hash.toString()));

        if (ba==null) {
            return null;
        }

        storedBlock = byteArrayToStoredBlock(ba);
        if(chainHeadInfo.getHeight() - storedBlock.getHeight() < MAX_SIZE_MAP_INDEXED_BLOCKS) {
            knownBlocks.put(hash, storedBlock);
        }

        return storedBlock;
    }

    @Override
    public synchronized StoredBlock getChainHead() throws BlockStoreException {
        if (this.chainHeadInfo == null) {
            byte[] ba = repository.getStorageBytes(contractAddress, DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY));
            if (ba == null) {
                return null;
            }
            StoredBlock storedBlock = byteArrayToStoredBlock(ba);
            BtcBlockInfo blockInfo = storeBlockInCache(storedBlock, true);
            chainHeadInfo = blockInfo;
            if(knownBlocks.get(chainHeadInfo.getPreviousHash()) == null) {
                populateCache(storedBlock);
            }

            return storedBlock;
        } else {
            return knownBlocks.get(chainHeadInfo.getHash());
        }

    }

    public synchronized void populateCache(StoredBlock chainHead) throws BlockStoreException {
        storeBlockInCache(chainHead, true);
        Sha256Hash blockHash = chainHead.getHeader().getPrevBlockHash();
        int spaceInCache = MAX_SIZE_MAP_INDEXED_BLOCKS-1;
        while (blockHash != null && spaceInCache > 0) {
            StoredBlock currentBlock = get(blockHash);
            if (currentBlock == null) {
                break;
            }
            storeBlockInCache(currentBlock, true);
            spaceInCache--;
            blockHash = currentBlock.getHeader().getPrevBlockHash();
        }
    }

    @Override
    public synchronized void setChainHead(StoredBlock chainHead) throws BlockStoreException {
        byte[] ba = storedBlockToByteArray(chainHead);
        repository.addStorageBytes(contractAddress, DataWord.fromString(BLOCK_STORE_CHAIN_HEAD_KEY), ba);

        if(chainHeadInfo != null) {
            reBranch(chainHead);
        } else {
            populateCache(chainHead);
        }

        chainHeadInfo = index.get(chainHead.getHeight()).get(chainHead.getHeader().getHash());
    }

    private synchronized BtcBlockInfo storeBlockInCache(StoredBlock storedBlock, boolean isMainChain) {
        Sha256Hash hash = storedBlock.getHeader().getHash();
        knownBlocks.put(hash, storedBlock);
        Map<Sha256Hash, BtcBlockInfo> currentChainMap = index.getOrDefault(storedBlock.getHeight(), new HashMap<>());
        BtcBlockInfo currentChainInfo = currentChainMap.getOrDefault(hash, new BtcBlockInfo(hash, storedBlock.getHeader().getPrevBlockHash(), storedBlock.getHeight()));
        currentChainInfo.setMainChain(isMainChain);
        currentChainMap.put(hash, currentChainInfo);
        index.put(storedBlock.getHeight(), currentChainMap);
        return currentChainInfo;
    }

    private synchronized void reBranch(StoredBlock forkBlock){

        StoredBlock currentChainCursor = knownBlocks.get(chainHeadInfo.getHash());
        StoredBlock newChainCursor = forkBlock;
        // Loop until we find the block both chains have in common. Example:
        //
        //    A -> B -> C -> D
        //         \--> E -> F -> G
        //
        // reBranch will return block B. oldChainHead = D and newChainHead = G.
        storeBlockInCache(currentChainCursor, false);
        storeBlockInCache(newChainCursor, true);

        while (!currentChainCursor.equals(newChainCursor)) {
            if (currentChainCursor.getHeight() > newChainCursor.getHeight()) {
                currentChainCursor = knownBlocks.get(currentChainCursor.getHeader().getPrevBlockHash());
                checkNotNull(currentChainCursor, "Attempt to follow an orphan chain");
                storeBlockInCache(currentChainCursor, false);
            } else {
                newChainCursor = knownBlocks.get(newChainCursor.getHeader().getPrevBlockHash());
                if(newChainCursor == null) {
                    //This happens if we change the head from a checkpoint
                    break;
                }
                storeBlockInCache(newChainCursor, true);
                checkNotNull(newChainCursor, "Attempt to follow an orphan chain");
            }
        }

    }

    @Override
    public void close() {
    }

    @Override
    public NetworkParameters getParams() {
        return params;
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
        return StoredBlock.deserializeCompact(params, byteBuffer);
    }


    public StoredBlock getFromCache(Sha256Hash branchBlockHash) throws BlockStoreException {
        StoredBlock branchBlock = knownBlocks.get(branchBlockHash);

        return branchBlock;
    }

    public BtcBlockInfo getFromCacheMainChain(Sha256Hash branchBlockHash) throws BlockStoreException {
        StoredBlock branchBlock = knownBlocks.get(branchBlockHash);
        if (branchBlock == null) {
            throw new BlockStoreException("Requested block : " + branchBlockHash + " not found in cache ");
        }
        Map<Sha256Hash,BtcBlockInfo> btcBlockInfoMap = index.get(branchBlock.getHeight());
        if(btcBlockInfoMap == null){
            throw new BlockStoreException("Requested block height not found in cache");
        }
        BtcBlockInfo btcBlockInfo = btcBlockInfoMap.get(branchBlockHash);
        if(btcBlockInfo == null){
            throw new BlockStoreException("Requested block height and hash not found in cache");
        }
        return btcBlockInfo;
    }

    public StoredBlock getMainChainCacheAtHeight(int height) {
        Map<Sha256Hash, BtcBlockInfo> blockInfoMap = index.get(height);
        if (blockInfoMap == null) {
            return null;
        }
        for (Map.Entry<Sha256Hash, BtcBlockInfo> entry : blockInfoMap.entrySet()) {
            if (entry.getValue().isMainChain()) {
                return knownBlocks.get(entry.getKey());
            }
        }
        return null;
    }

    public StoredBlock getStoredBlockAtHeight(int height) throws  BlockStoreException{
        StoredBlock block = getMainChainCacheAtHeight(height);
        if (block != null) {
            return block;
        }
        if(height > chainHeadInfo.getHeight()) {
            return null;
        }
        //Should be in cache if it has this height
        if (height > getMinIndexedBlockHeight() && block == null) {
            return null;
        }

        //If its older than cache go to disk
        StoredBlock lastBlockInCache = getMainChainCacheAtHeight(getMinIndexedBlockHeight());
        Sha256Hash blockHash = lastBlockInCache.getHeader().getHash();
        int depth = lastBlockInCache.getHeight() - height;

        for (int i = 0; i < depth; i++) {
            if (blockHash == null) {
                return null;
            }

            StoredBlock currentBlock = get(blockHash);
            if (currentBlock == null) {
                return null;
            }
            Sha256Hash prevBlockHash = currentBlock.getHeader().getPrevBlockHash();
            blockHash = prevBlockHash;
        }

        if (blockHash == null) {
            return null;
        }

        block = get(blockHash);

        if (block != null) {
            if (block.getHeight() != height) {
                throw new IllegalStateException("Block height is " + block.getHeight() + " but should be " + height);
            }
        }
        return block;
    }



}
