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

package co.rsk.test;

import co.rsk.blockchain.utils.BlockGenerator;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 8/7/2016.
 */
public class WorldTest {
    @Test
    public void getUnknownBlockByName() {
        World world = new World();

        Assertions.assertNull(world.getBlockByName("foo"));
    }

    @Test
    public void saveAndGetBlock() {
        World world = new World();

        Block block = new BlockGenerator().getBlock(1);

        world.saveBlock("b01", block);
        Assertions.assertSame(block, world.getBlockByName("b01"));
    }

    @Test
    public void getGenesisBlock() {
        World world = new World();

        Block genesis = world.getBlockByName("g00");

        Assertions.assertNotNull(genesis);
        Assertions.assertEquals(0, genesis.getNumber());

        Block best = world.getBlockChain().getStatus().getBestBlock();

        Assertions.assertNotNull(best);
        Assertions.assertEquals(0, best.getNumber());
        Assertions.assertEquals(genesis.getHash(), best.getHash());
    }

    @Test
    public void getBlockChain() {
        World world = new World();

        Assertions.assertNotNull(world.getBlockChain());
    }

    @Test
    public void getUnknownAccountByName() {
        World world = new World();

        Assertions.assertNull(world.getAccountByName("foo"));
    }

    @Test
    public void saveAndGetAccount() {
        World world = new World();

        Account account = new Account(new ECKey(Utils.getRandom()));

        world.saveAccount("acc1", account);
        Assertions.assertSame(account, world.getAccountByName("acc1"));
    }
}
