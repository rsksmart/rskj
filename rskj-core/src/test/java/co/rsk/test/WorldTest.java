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
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.Utils;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Created by ajlopez on 8/7/2016.
 */
public class WorldTest {
    @Test
    public void getUnknownBlockByName() {
        World world = new World();

        Assert.assertNull(world.getBlockByName("foo"));
    }

    @Test
    public void saveAndGetBlock() {
        World world = new World();

        Block block = new BlockGenerator().getBlock(1);

        world.saveBlock("b01", block);
        Assert.assertSame(block, world.getBlockByName("b01"));
    }

    @Test
    public void getGenesisBlock() {
        World world = new World();

        Block genesis = world.getBlockByName("g00");

        Assert.assertNotNull(genesis);
        assertEquals(0, genesis.getNumber());

        Block best = world.getBlockChain().getStatus().getBestBlock();

        Assert.assertNotNull(best);
        assertEquals(0, best.getNumber());
        assertEquals(genesis.getHash(), best.getHash());
    }

    @Test
    public void getBlockChain() {
        World world = new World();

        Assert.assertNotNull(world.getBlockChain());
    }

    @Test
    public void getUnknownAccountByName() {
        World world = new World();

        Assert.assertNull(world.getAccountByName("foo"));
    }

    @Test
    public void saveAndGetAccount() {
        World world = new World();

        Account account = new Account(new ECKey(Utils.getRandom()));

        world.saveAccount("acc1", account);
        Assert.assertSame(account, world.getAccountByName("acc1"));
    }

    @Test
    public void customTimeBetweenBlocks() throws FileNotFoundException, DslProcessorException {
        World world = new World();
        long timeBetweenBlocks = TimeUnit.SECONDS.toMillis(30);
        world.setCustomTimeBetweenBlocks(timeBetweenBlocks);

        DslParser parser = DslParser.fromResource("dsl/time_between_blocks.txt");

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        assertEquals(0, world.getBlockByName("g00").getTimestamp());
        assertEquals(timeBetweenBlocks, timeDifferenceBetweenBlocks(world, "g00", "b01"));
        assertEquals(timeBetweenBlocks, timeDifferenceBetweenBlocks(world, "b01", "b02"));
        assertEquals(timeBetweenBlocks, timeDifferenceBetweenBlocks(world, "b02", "b03"));
        assertEquals(timeBetweenBlocks, timeDifferenceBetweenBlocks(world, "b03", "b04"));
    }

    @Test
    public void customTimeBetweenBlocks_timeShouldBePositive() throws FileNotFoundException, DslProcessorException {
        World world = new World();
        long timeBetweenBlocks = TimeUnit.SECONDS.toMillis(0);
        world.setCustomTimeBetweenBlocks(timeBetweenBlocks);

        DslParser parser = DslParser.fromResource("dsl/time_between_blocks.txt");

        WorldDslProcessor processor = new WorldDslProcessor(world);

        try {
            processor.processCommands(parser);
            fail("this shouldn't happen");
        } catch (IllegalArgumentException e) {
            assertEquals("customTimeBetweenBlocks should be positive", e.getMessage());
        }
    }

    private long timeDifferenceBetweenBlocks(World world, String parent, String child) {
        Block p = world.getBlockByName(parent);
        Block c = world.getBlockByName(child);

        assertEquals(p.getHashJsonString(), c.getParentHashJsonString());

        return Math.abs(p.getTimestamp() - c.getTimestamp());
    }
}

