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
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Genesis;
import org.ethereum.core.Repository;
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
            public Genesis buildGenesis() {
                return GenesisLoader.loadGenesis("blockchain_loader_genesis.json", BigInteger.ZERO, true, true);
            }
        };
        objects.getBlockchain(); // calls loadBlockchain
        Repository repository = objects.getRepository();

        Assert.assertEquals(5, repository.getAccountsKeys().size());

        Assert.assertEquals(Coin.valueOf(2000), repository.getBalance(new RskAddress("dabadabadabadabadabadabadabadabadaba0001")));
        Assert.assertEquals(BigInteger.valueOf(24), repository.getNonce(new RskAddress("dabadabadabadabadabadabadabadabadaba0001")));

        Assert.assertEquals(Coin.valueOf(1000), repository.getBalance(new RskAddress("dabadabadabadabadabadabadabadabadaba0002")));
        Assert.assertEquals(BigInteger.ZERO, repository.getNonce(new RskAddress("dabadabadabadabadabadabadabadabadaba0002")));

        RskAddress address = new RskAddress("77045e71a7a2c50903d88e564cd72fab11e82051");
        Assert.assertEquals(Coin.valueOf(10), repository.getBalance(address));
        Assert.assertEquals(BigInteger.valueOf(25), repository.getNonce(address));
        Assert.assertEquals(DataWord.ONE, repository.getStorageValue(address, DataWord.ZERO));
        Assert.assertEquals(DataWord.valueOf(3), repository.getStorageValue(address, DataWord.ONE));
        Assert.assertEquals(274, repository.getCode(address).length);

    }

}
