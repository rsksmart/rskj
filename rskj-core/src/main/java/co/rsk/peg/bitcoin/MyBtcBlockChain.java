/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.rsk.peg.bitcoin;

import static com.google.common.base.Preconditions.checkArgument;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bitcoinj.store.BlockStoreException;

// TODO: Rename this class to SPVBlockChain at some point.

/**
 * A BlockChain implements the <i>simplified payment verification</i> mode of the Bitcoin protocol. It is the right
 * choice to use for programs that have limited resources as it won't verify transactions signatures or attempt to store
 * all of the block chain. Really, this class should be called SPVBlockChain but for backwards compatibility it is not.
 */
public class MyBtcBlockChain extends MyBtcAbstractBlockChain {
    /** Keeps a map of block hashes to StoredBlocks. */
    protected final BtcBlockStore blockStore;


    /**
     * Constructs a BlockChain that has no wallet at all. This is helpful when you don't actually care about sending
     * and receiving coins but rather, just want to explore the network data structures.
     */
    public MyBtcBlockChain(Context context, BtcBlockStore blockStore) throws BlockStoreException {
        super(context, blockStore);
        this.blockStore = blockStore;
    }

    @Override
    protected StoredBlock addToBlockStore(StoredBlock storedPrev, BtcBlock blockHeader)
            throws BlockStoreException, VerificationException {
        StoredBlock newBlock = storedPrev.build(blockHeader);
        blockStore.put(newBlock);
        return newBlock;
    }

    @Override
    protected void rollbackBlockStore(int height) throws BlockStoreException {
        try {
            int currentHeight = getBestChainHeight();
            checkArgument(height >= 0 && height <= currentHeight, "Bad height: %s", height);
            if (height == currentHeight)
                return; // nothing to do

            // Look for the block we want to be the new chain head
            StoredBlock newChainHead = blockStore.getChainHead();
            while (newChainHead.getHeight() > height) {
                newChainHead = newChainHead.getPrev(blockStore);
                if (newChainHead == null)
                    throw new BlockStoreException("Unreachable height");
            }

            // Modify store directly
            blockStore.put(newChainHead);
            this.setChainHead(newChainHead);
        } finally {
        }
    }

    @Override
    protected boolean shouldVerifyTransactions() {
        return false;
    }

    @Override
    protected void doSetChainHead(StoredBlock chainHead) throws BlockStoreException {
        blockStore.setChainHead(chainHead);
    }

    @Override
    protected void notSettingChainHead() throws BlockStoreException {
        // We don't use DB transactions here, so we don't need to do anything
    }

    @Override
    protected StoredBlock getStoredBlockInCurrentScope(Sha256Hash hash) throws BlockStoreException {
        return blockStore.get(hash);
    }

    @Override
    public boolean add(FilteredBlock block) throws VerificationException {
        boolean success = super.add(block);
        if (success) {
            trackFilteredTransactions(block.getTransactionCount());
        }
        return success;
    }
}
