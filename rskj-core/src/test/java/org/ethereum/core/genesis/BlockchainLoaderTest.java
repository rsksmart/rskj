/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.core.genesis;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.listener.EthereumListener;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigInteger;

public class BlockchainLoaderTest {

    @Test
    public void testLoadBlockchainEmptyBlockchain() throws IOException {
        String jsonFile = "blockchain_loader_genesis.json";
        // Initial state is
        //"dabadabadabadabadabadabadabadabadaba0001" : {
        //    "balance" : "2000",
        //    "nonce" : "24"
        //},
        //"dabadabadabadabadabadabadabadabadaba0002" : {
        //    "balance" : "1000"
        //},
        //"77045e71a7a2c50903d88e564cd72fab11e82051" : {
        //    "balance" : "10",
        //    "nonce" : "25",
        //"data" : {
        //    "0000000000000000000000000000000000000000000000000000000000000001" : "03",
        //    "0000000000000000000000000000000000000000000000000000000000000000" : "01"
        //}

        TestSystemProperties systemProperties = Mockito.mock(TestSystemProperties.class);

        Constants constants = Mockito.mock(Constants.class);
        Mockito.when(constants.getInitialNonce()).thenReturn(BigInteger.ZERO);

        BlockchainNetConfig blockchainNetConfig = Mockito.mock(BlockchainNetConfig.class);
        Mockito.when(blockchainNetConfig.getCommonConstants()).thenReturn(constants);

        Mockito.when(systemProperties.databaseDir()).thenReturn(new TestSystemProperties().databaseDir());
        Mockito.when(systemProperties.getBlockchainConfig()).thenReturn(blockchainNetConfig);
        Mockito.when(systemProperties.genesisInfo()).thenReturn(jsonFile);

        BlockStore blockStore = Mockito.mock(BlockStore.class);
        Mockito.when(blockStore.getBestBlock()).thenReturn(null);

        EthereumListener ethereumListener = Mockito.mock(EthereumListener.class);

        // To use getAccountsKeys() the trie must not be secure
        Repository repository = new MutableRepository(new MutableTrieImpl(new TrieImpl(new TrieStoreImpl(new HashMapDB().setClearOnClose(false)), true)));

        BlockChainLoader blockChainLoader = new BlockChainLoader(systemProperties, repository, blockStore, null, null, ethereumListener, null,
                                                                 RskTestFactory.getGenesisInstance(systemProperties)
        );

        blockChainLoader.loadBlockchain();

        Assert.assertEquals(5, repository.getAccountsKeys().size());

        RskAddress daba01 = new RskAddress("dabadabadabadabadabadabadabadabadaba0001");
        Assert.assertEquals(Coin.valueOf(2000), repository.getBalance(daba01));
        Assert.assertEquals(BigInteger.valueOf(24), repository.getNonce(daba01));

        RskAddress daba02 = new RskAddress("dabadabadabadabadabadabadabadabadaba0002");
        Assert.assertEquals(Coin.valueOf(1000), repository.getBalance(daba02));
        Assert.assertEquals(BigInteger.ZERO, repository.getNonce(daba02));

        RskAddress address = new RskAddress("77045e71a7a2c50903d88e564cd72fab11e82051");
        Assert.assertEquals(Coin.valueOf(10), repository.getBalance(address));
        Assert.assertEquals(BigInteger.valueOf(25), repository.getNonce(address));
        Assert.assertEquals(DataWord.ONE, repository.getStorageValue(address, DataWord.ZERO));
        Assert.assertEquals(new DataWord(3), repository.getStorageValue(address, DataWord.ONE));
        Assert.assertEquals(274, repository.getCode(address).length);

    }

}
