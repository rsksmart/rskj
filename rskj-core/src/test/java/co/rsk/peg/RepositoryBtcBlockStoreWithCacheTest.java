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
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import org.apache.commons.lang3.tuple.Triple;
import org.ethereum.core.Repository;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Test;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.math.BigInteger;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class RepositoryBtcBlockStoreWithCacheTest {

    NetworkParameters networkParameters = BridgeRegTestConstants.getInstance().getBtcParams();

    private BtcBlockStoreWithCache createBlockStore() {
        Repository repository =  createRepository();
        RepositoryBtcBlockStoreWithCache.Factory factory = createBlockStoreFactory();
        return createBlockStoreWithTrack(factory, repository.startTracking());
    }

    private BtcBlockStoreWithCache createBlockStoreWithTrack(RepositoryBtcBlockStoreWithCache.Factory factory, Repository track) {
        return factory.newInstance(track, null, null);
    }

    private RepositoryBtcBlockStoreWithCache.Factory createBlockStoreFactory() {
        return new RepositoryBtcBlockStoreWithCache.Factory(networkParameters);
    }

    private Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    @Test
    public void getChainHead_Test() throws BlockStoreException {
        BtcBlockStoreWithCache btcBlockStore = createBlockStore();

        assertEquals(networkParameters.getGenesisBlock(), btcBlockStore.getChainHead().getHeader());
    }

    @Test
    public void getParams_Test() {
        BtcBlockStoreWithCache btcBlockStore = createBlockStore();

        assertEquals(networkParameters, btcBlockStore.getParams());
    }

    @Test
    public void setChainHead_Test() throws BlockStoreException {
        BtcBlockStoreWithCache btcBlockStore = createBlockStore();

        BtcBlock genesis = networkParameters.getGenesisBlock();
        StoredBlock firstStoredBlock = createStoredBlock(genesis, 1, 0);
        Sha256Hash firstBlockHash = firstStoredBlock.getHeader().getHash();

        //Check that genesis block is the Head
        assertEquals(genesis.getHash(), btcBlockStore.getChainHead().getHeader().getHash());

        //There should always be a put before a setChainHead
        btcBlockStore.put(firstStoredBlock);
        //Test that put worked correctly
        assertEquals(firstStoredBlock, btcBlockStore.getFromCache(firstBlockHash));
        assertEquals(firstStoredBlock, btcBlockStore.get(firstBlockHash));
        assertEquals(genesis, btcBlockStore.getChainHead().getHeader());

        btcBlockStore.setChainHead(firstStoredBlock);
        //Test that set Chain Head worked correctly
        assertEquals(firstStoredBlock, btcBlockStore.getChainHead());

    }

    private StoredBlock createStoredBlock(BtcBlock parent, int height, int nonce) {
        BtcBlock firstBlockHeader = new BtcBlock(networkParameters, 2l, parent.getHash(), Sha256Hash.ZERO_HASH, parent.getTimeSeconds()+1, parent.getDifficultyTarget(), nonce, new ArrayList<>());
        return new StoredBlock(firstBlockHeader, new BigInteger("0"), height);
    }

    @Test
    public void ifCacheNullAlwaysGoToDisk_Test() throws BlockStoreException {
        Repository repository =  createRepository();
        BtcBlockStoreWithCache btcBlockStore = new RepositoryBtcBlockStoreWithCache(
            networkParameters,
            repository.startTracking(),
            null,
            PrecompiledContracts.BRIDGE_ADDR,
            null,
            null
        );

        BtcBlock genesis = networkParameters.getGenesisBlock();

        StoredBlock firstStoredBlock = createStoredBlock(genesis, 1, 0);
        Sha256Hash firstBlockHash = firstStoredBlock.getHeader().getHash();

        btcBlockStore.put(firstStoredBlock);
        btcBlockStore.setChainHead(firstStoredBlock);

        assertEquals(firstStoredBlock, btcBlockStore.get(firstBlockHash));
        assertNull(btcBlockStore.getFromCache(firstBlockHash));

        assertEquals(firstStoredBlock, btcBlockStore.getChainHead());
        assertEquals(genesis, btcBlockStore.getStoredBlockAtMainChainDepth(1).getHeader());
        assertEquals(genesis, btcBlockStore.getStoredBlockAtMainChainHeight(0).getHeader());
    }

    @Test
    public void put_oldBlockShouldNotGoToCache_Test() throws BlockStoreException {
        BtcBlockStoreWithCache btcBlockStore = createBlockStore();

        BtcBlock genesis = networkParameters.getGenesisBlock();

        //Set chain head at height 6000
        StoredBlock storedBlock1 = createStoredBlock(genesis, 6000, 0);
        Sha256Hash firstBlockHash = storedBlock1.getHeader().getHash();
        btcBlockStore.put(storedBlock1);
        btcBlockStore.setChainHead(storedBlock1);

        //Store a block of height 1 which is lesser than chainHead - 5000
        StoredBlock storedBlock2 = createStoredBlock(genesis, 1, 1);
        Sha256Hash secondBlockHash = storedBlock2.getHeader().getHash();
        btcBlockStore.put(storedBlock2);

        assertEquals(storedBlock1, btcBlockStore.getFromCache(firstBlockHash));
        assertNull(btcBlockStore.getFromCache(secondBlockHash));
        assertEquals(storedBlock1, btcBlockStore.get(firstBlockHash));
        assertEquals(storedBlock2, btcBlockStore.get(secondBlockHash));
        assertEquals(storedBlock1, btcBlockStore.getChainHead());
    }

    @Test
    public void cacheLivesAcrossInstances_Test() throws BlockStoreException {
        Repository repository =  createRepository();
        RepositoryBtcBlockStoreWithCache.Factory factory = createBlockStoreFactory();
        BtcBlockStoreWithCache btcBlockStore = createBlockStoreWithTrack(factory, repository.startTracking());

        BtcBlock genesis = networkParameters.getGenesisBlock();
        StoredBlock firstStoredBlock = createStoredBlock(genesis, 1, 0);
        Sha256Hash firstBlockHash = firstStoredBlock.getHeader().getHash();
        btcBlockStore.put(firstStoredBlock);

        //Cache should have the genesis block and the one we just added
        assertNotNull(btcBlockStore.getFromCache(genesis.getHash()));
        assertEquals(firstStoredBlock, btcBlockStore.getFromCache(firstBlockHash));

        BtcBlockStoreWithCache btcBlockStore2 = createBlockStoreWithTrack(factory, repository.startTracking());
        StoredBlock secondStoredBlock = createStoredBlock(firstStoredBlock.getHeader(), 2, 0);
        Sha256Hash secondBlockHash = secondStoredBlock.getHeader().getHash();
        btcBlockStore2.put(secondStoredBlock);

        //Trie of btcBlockStore1  should have only te second one
        assertEquals(firstStoredBlock, btcBlockStore.get(firstBlockHash));
        assertNull(btcBlockStore.get(secondBlockHash));

        //Trie of btcBlockStore2  should have only te second one
        assertNull(btcBlockStore2.get(firstBlockHash));
        assertEquals(secondStoredBlock, btcBlockStore2.get(secondBlockHash));

        //Cache has both of them
        assertEquals(firstStoredBlock, btcBlockStore2.getFromCache(firstBlockHash));
        assertEquals(secondStoredBlock, btcBlockStore2.getFromCache(secondBlockHash));
    }


    @Test
    public void getStoredBlockAtMainChainDepth_And_Height_Test() throws BlockStoreException {
        BtcBlockStoreWithCache btcBlockStore = createBlockStore();
        BtcBlock genesis = networkParameters.getGenesisBlock();

        StoredBlock storedBlock1 = createStoredBlock(genesis, 1, 0);
        btcBlockStore.put(storedBlock1);
        StoredBlock storedBlock2 = createStoredBlock(storedBlock1.getHeader(), 2, 0);
        btcBlockStore.put(storedBlock2);
        StoredBlock storedBlock3 = createStoredBlock(storedBlock2.getHeader(), 3, 0);
        btcBlockStore.put(storedBlock3);
        StoredBlock storedBlock4 = createStoredBlock(storedBlock3.getHeader(), 4, 0);
        btcBlockStore.put(storedBlock4);

        btcBlockStore.setChainHead(storedBlock4);
        assertEquals(storedBlock4, btcBlockStore.getChainHead());
        int maxHeight = storedBlock4.getHeight();

        //Check getStoredBlockAtMainChainDepth
        assertEquals(storedBlock3, btcBlockStore.getStoredBlockAtMainChainDepth(maxHeight - storedBlock3.getHeight()));
        assertEquals(storedBlock2, btcBlockStore.getStoredBlockAtMainChainDepth(maxHeight - storedBlock2.getHeight()));
        assertEquals(storedBlock1, btcBlockStore.getStoredBlockAtMainChainDepth(maxHeight - storedBlock1.getHeight()));
        assertEquals(genesis, btcBlockStore.getStoredBlockAtMainChainDepth(maxHeight).getHeader());

        //Check getStoredBlockAtMainChainHeight
        assertEquals(storedBlock4, btcBlockStore.getStoredBlockAtMainChainHeight(4));
        assertEquals(storedBlock3, btcBlockStore.getStoredBlockAtMainChainHeight(3));
        assertEquals(storedBlock2, btcBlockStore.getStoredBlockAtMainChainHeight(2));
        assertEquals(storedBlock1, btcBlockStore.getStoredBlockAtMainChainHeight(1));

    }

    @Test
    public void getStoredBlockAtMainChainDepth_Error_Test() throws BlockStoreException {
        BtcBlockStoreWithCache btcBlockStore = createBlockStore();
        BtcBlock genesis = networkParameters.getGenesisBlock();

        BtcBlock parent = genesis;
        BtcBlock blockHeader1 = new BtcBlock(networkParameters, 2l, parent.getHash(), Sha256Hash.ZERO_HASH, parent.getTimeSeconds()+1, parent.getDifficultyTarget(), 0, new ArrayList<>());
        StoredBlock storedBlock1 = new StoredBlock(blockHeader1, new BigInteger("0"), 2);
        btcBlockStore.put(storedBlock1);

        parent = blockHeader1;
        BtcBlock blockHeader2 = new BtcBlock(networkParameters, 2l, parent.getHash(), Sha256Hash.ZERO_HASH, parent.getTimeSeconds()+1, parent.getDifficultyTarget(), 0, new ArrayList<>());
        StoredBlock storedBlock2 = new StoredBlock(blockHeader2, new BigInteger("0"), 2);
        btcBlockStore.put(storedBlock2);

        btcBlockStore.setChainHead(storedBlock2);
        //getStoredBlockAtMainChainDepth should fail as the block is at a inconsistent height
        try {
            btcBlockStore.getStoredBlockAtMainChainDepth(1);
            assertTrue(false);
        } catch(BlockStoreException e) {
            assertTrue(true);
        }
        //getStoredBlockAtMainChainHeight should fail as the block is at a inconsistent height
        try {
            btcBlockStore.getStoredBlockAtMainChainHeight(1);
            assertTrue(false);
        } catch(BlockStoreException e) {
            assertTrue(true);
        }
    }

    @Test
    public void checkDifferentInstancesWithSameRepoHaveSameContentTest() throws Exception {
//        This Is how I produced RepositoryBlockStore_data.ser. I had a bitcoind in regtest with 613 blocks + genesis block
//        NetworkParameters params = RegTestParams.get();
//        Context context = new Context(params);
//        Wallet wallet = new Wallet(context);
//        BlockStore store = new SPVBlockStore(params, new File("spvBlockstore"));
//        AbstractBlockChain chain = new BlockChain(context, wallet, store);
//        PeerGroup peerGroup = new PeerGroup(context, chain);
//        peerGroup.start();
//        final DownloadProgressTracker listener = new DownloadProgressTracker();
//        peerGroup.startBlockChainDownload(listener);
//        listener.await();
//        peerGroup.stop();
//        StoredBlock storedBlock = chain.getChainHead();
//        FileOutputStream fos = new FileOutputStream("RepositoryBlockStore_data.ser");
//        ObjectOutputStream oos = new ObjectOutputStream(fos);
//        for (int i = 0; i < 614; i++) {
//            Triple<byte[], BigInteger , Integer> tripleStoredBlock = new ImmutableTriple<>(storedBlock.getHeader().bitcoinSerialize(), storedBlock.getChainWork(), storedBlock.getHeight());
//            oos.writeObject(tripleStoredBlock);
//            storedBlock = store.get(storedBlock.getHeader().getPrevBlockHash());
//        }
//        oos.close();

        // Read original store
        InputStream fileInputStream = ClassLoader.getSystemResourceAsStream("peg/RepositoryBlockStore_data.ser");
        ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
        Repository repository = new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
        BtcBlockStoreWithCache store = btcBlockStoreFactory.newInstance(repository, null, null);
        for (int i = 0; i < 614; i++) {
            Triple<byte[], BigInteger , Integer> tripleStoredBlock = (Triple<byte[], BigInteger , Integer>) objectInputStream.readObject();
            BtcBlock header = RegTestParams.get().getDefaultSerializer().makeBlock(tripleStoredBlock.getLeft());
            StoredBlock storedBlock = new StoredBlock(header, tripleStoredBlock.getMiddle(), tripleStoredBlock.getRight());
            if (i==0) {
                store.setChainHead(storedBlock);
            }
            store.put(storedBlock);
        }

        // Create a new instance of the store
        BtcBlockStoreWithCache store2 =btcBlockStoreFactory.newInstance(repository, null, null);

        // Check a specific block that used to fail when we had a bug
        assertEquals(store.get(Sha256Hash.wrap("373941fe83961cf70e181e468abc5f9f7cc440c711c3d06948fa66f3912ed27a")),
                store2.get(Sha256Hash.wrap("373941fe83961cf70e181e468abc5f9f7cc440c711c3d06948fa66f3912ed27a")));

        //Check new instance content is identical to the original one
        StoredBlock storedBlock = store.getChainHead();
        StoredBlock storedBlock2 = store2.getChainHead();
        int headHeight = storedBlock.getHeight();
        for (int i = 0; i < headHeight; i++) {
            assertNotNull(storedBlock);
            assertEquals(storedBlock, storedBlock2);
            Sha256Hash prevBlockHash = storedBlock.getHeader().getPrevBlockHash();
            storedBlock = store.get(prevBlockHash);
            storedBlock2 = store2.get(prevBlockHash);
        }
    }
}
