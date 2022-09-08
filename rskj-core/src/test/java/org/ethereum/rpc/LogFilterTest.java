/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package org.ethereum.rpc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositoryLocator;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RskTestFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 17/01/2018.
 */
public class LogFilterTest {
    @Test
    public void noEvents() {
        LogFilter filter = new LogFilter(null, null, false, false);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.length);
    }

    @Test
    public void noEventsAfterEmptyBlock() {
        LogFilter filter = new LogFilter(null, null, false, false);

        Block block = new BlockGenerator().getBlock(1);

        filter.newBlockReceived(block);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.length);
    }

    @Test
    public void eventAfterBlockWithEvent() {
        RskTestFactory factory = new RskTestFactory();
        Blockchain blockchain = factory.getBlockchain();
        BlockStore blockStore = factory.getBlockStore();
        RepositoryLocator repositoryLocator = factory.getRepositoryLocator();
        Web3ImplLogsTest.addEmptyBlockToBlockchain(blockchain, blockStore, repositoryLocator, factory.getTrieStore());
        Block block = blockchain.getBestBlock();

        AddressesTopicsFilter atfilter = new AddressesTopicsFilter(new RskAddress[0], null);

        LogFilter filter = new LogFilter(atfilter, blockchain, false, true);

        filter.newBlockReceived(block);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
    }

    @Test
    public void twoEventsAfterTwoBlocksWithEventAndToLatestBlock() {
        RskTestFactory factory = new RskTestFactory();
        Blockchain blockchain = factory.getBlockchain();
        BlockStore blockStore = factory.getBlockStore();
        RepositoryLocator repositoryLocator = factory.getRepositoryLocator();
        Web3ImplLogsTest.addEmptyBlockToBlockchain(blockchain, blockStore, repositoryLocator, factory.getTrieStore());
        Block block = blockchain.getBestBlock();

        AddressesTopicsFilter atfilter = new AddressesTopicsFilter(new RskAddress[0], null);

        LogFilter filter = new LogFilter(atfilter, blockchain, false, true);

        filter.newBlockReceived(block);
        filter.newBlockReceived(block);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.length);
    }

    @Test
    public void onlyOneEventAfterTwoBlocksWithEventAndFromLatestBlock() {
        RskTestFactory factory = new RskTestFactory();
        Blockchain blockchain = factory.getBlockchain();
        BlockStore blockStore = factory.getBlockStore();
        RepositoryLocator repositoryLocator = factory.getRepositoryLocator();
        Web3ImplLogsTest.addEmptyBlockToBlockchain(blockchain, blockStore, repositoryLocator, factory.getTrieStore());
        Block block = blockchain.getBestBlock();

        AddressesTopicsFilter atfilter = new AddressesTopicsFilter(new RskAddress[0], null);

        LogFilter filter = new LogFilter(atfilter, blockchain, true, true);

        filter.newBlockReceived(block);
        filter.newBlockReceived(block);

        Object[] result = filter.getEvents();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
    }
}
