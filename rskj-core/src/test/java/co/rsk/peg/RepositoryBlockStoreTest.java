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
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.TestSystemProperties;
import co.rsk.db.RepositoryImplForTesting;
import org.apache.commons.lang3.tuple.Triple;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RepositoryBlockStoreTest {

    private static final Logger logger = LoggerFactory.getLogger("test");

    @Before
    public void doBefore() throws IOException {
    }

    @After
    public void doAfter() throws IOException {
    }

    @Test
    public void test() throws Exception {
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
        Repository repository = new RepositoryImplForTesting();
        TestSystemProperties config = new TestSystemProperties();
        RepositoryBlockStore store = new RepositoryBlockStore(config, repository, PrecompiledContracts.BRIDGE_ADDR);
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
        RepositoryBlockStore store2 = new RepositoryBlockStore(config, repository, PrecompiledContracts.BRIDGE_ADDR);


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
