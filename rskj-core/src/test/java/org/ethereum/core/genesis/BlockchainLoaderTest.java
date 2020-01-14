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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.RepositorySnapshot;
import org.ethereum.core.Blockchain;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;

public class BlockchainLoaderTest {

    @Test
    public void testLoadBlockchainEmptyBlockchain() throws IOException {
        RskTestFactory objects = new RskTestFactory() {
            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "blockchain_loader_genesis.json", BigInteger.ZERO, true, true, true);
            }
        };
        Blockchain blockchain = objects.getBlockchain();// calls loadBlockchain
        RepositorySnapshot repository = objects.getRepositoryLocator().getRepositoryAt(blockchain.getBestBlock().getHeader());

        int genesisAccountKeysSize = 12; // PCCs + test accounts in blockchain_loader_genesis.json
        Assert.assertEquals(genesisAccountKeysSize, repository.getAccountsKeys().size());

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
        Assert.assertEquals(DataWord.valueOf(3), repository.getStorageValue(address, DataWord.ONE));
        Assert.assertEquals(274, repository.getCode(address).length);

    }

}
