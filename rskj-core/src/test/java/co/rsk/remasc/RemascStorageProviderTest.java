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

package co.rsk.remasc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositoryImpl;
import co.rsk.db.RepositoryImplForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SortedMap;

/**
 * Created by usuario on 13/04/2017.
 */
public class RemascStorageProviderTest {

    private final RskSystemProperties config = new RskSystemProperties();

    @Test
    public void getDefautRewardBalance() {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImpl(config);

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Coin.ZERO, provider.getRewardBalance());
    }

    @Test
    public void setAndGetRewardBalance() {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImpl(config);

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setRewardBalance(Coin.valueOf(1));

        Assert.assertEquals(Coin.valueOf(1), provider.getRewardBalance());
    }

    @Test
    public void setSaveRetrieveAndGetRewardBalance() throws IOException {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImplForTesting();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setRewardBalance(Coin.valueOf(255));

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Coin.valueOf(255), newProvider.getRewardBalance());
    }

    @Test
    public void getDefautBurnedBalance() {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImpl(config);

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Coin.ZERO, provider.getBurnedBalance());
    }

    @Test
    public void setAndGetBurnedBalance() {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImpl(config);

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBurnedBalance(Coin.valueOf(1));

        Assert.assertEquals(Coin.valueOf(1), provider.getBurnedBalance());
    }

    @Test
    public void setSaveRetrieveAndGetBurnedBalance() throws IOException {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImplForTesting();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBurnedBalance(Coin.valueOf(255));

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Coin.valueOf(255), newProvider.getBurnedBalance());
    }

    @Test
    public void getDefaultBrokenSelectionRule() {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImpl(config);

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Boolean.FALSE, provider.getBrokenSelectionRule());
    }

    @Test
    public void setAndGetBrokenSelectionRule() {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImpl(config);

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBrokenSelectionRule(Boolean.TRUE);

        Assert.assertEquals(Boolean.TRUE, provider.getBrokenSelectionRule());
    }

    @Test
    public void setSaveRetrieveAndGetBrokenSelectionRule() throws IOException {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImplForTesting();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        provider.setBrokenSelectionRule(Boolean.TRUE);

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        Assert.assertEquals(Boolean.TRUE, newProvider.getBrokenSelectionRule());
    }

    @Test
    public void getDefaultSiblings() {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImpl(config);

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        SortedMap<Long, List<Sibling>> map = provider.getSiblings();

        Assert.assertNotNull(map);
        Assert.assertTrue(map.isEmpty());
    }

    @Test
    public void setAndGetSiblings() {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImpl(config);

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block = blockGenerator.createChildBlock(genesis);

        Sibling sibling1 = new Sibling(genesis.getHeader(), genesis.getCoinbase(), 1);
        Sibling sibling2 = new Sibling(block.getHeader(), block.getCoinbase(), 2);

        List<Sibling> siblings = new ArrayList<>();
        siblings.add(sibling1);
        siblings.add(sibling2);

        provider.getSiblings().put(Long.valueOf(1), siblings);

        SortedMap<Long, List<Sibling>> map = provider.getSiblings();

        Assert.assertNotNull(map);
        Assert.assertFalse(map.isEmpty());
        Assert.assertTrue(map.containsKey(Long.valueOf(1)));

        Assert.assertEquals(2, map.get(Long.valueOf(1)).size());
    }

    @Test
    public void setSaveRetrieveAndGetSiblings() throws IOException {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImplForTesting();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block = blockGenerator.createChildBlock(genesis);

        Sibling sibling1 = new Sibling(genesis.getHeader(), genesis.getCoinbase(), 1);
        Sibling sibling2 = new Sibling(block.getHeader(), block.getCoinbase(), 2);

        List<Sibling> siblings = new ArrayList<>();
        siblings.add(sibling1);
        siblings.add(sibling2);

        provider.getSiblings().put(Long.valueOf(1), siblings);

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        SortedMap<Long, List<Sibling>> map = newProvider.getSiblings();

        Assert.assertNotNull(map);
        Assert.assertFalse(map.isEmpty());
        Assert.assertTrue(map.containsKey(Long.valueOf(1)));

        Assert.assertEquals(2, map.get(Long.valueOf(1)).size());
    }

    @Test
    public void setSaveRetrieveAndGetManySiblings() throws IOException {
        RskAddress accountAddress = randomAddress();
        Repository repository = new RepositoryImplForTesting();

        RemascStorageProvider provider = new RemascStorageProvider(repository, accountAddress);

        BlockGenerator blockGenerator = new BlockGenerator();
        Block genesis = blockGenerator.getGenesisBlock();
        Block block1 = blockGenerator.createChildBlock(genesis);
        Block block2 = blockGenerator.createChildBlock(block1);
        Block block3 = blockGenerator.createChildBlock(block2);
        Block block4 = blockGenerator.createChildBlock(block3);
        Block block5 = blockGenerator.createChildBlock(block4);

        Sibling sibling1 = new Sibling(genesis.getHeader(), genesis.getCoinbase(), 1);
        Sibling sibling2 = new Sibling(block1.getHeader(), block1.getCoinbase(), 2);
        Sibling sibling3 = new Sibling(block2.getHeader(), block2.getCoinbase(), 3);
        Sibling sibling4 = new Sibling(block3.getHeader(), block3.getCoinbase(), 4);
        Sibling sibling5 = new Sibling(block4.getHeader(), block4.getCoinbase(), 5);
        Sibling sibling6 = new Sibling(block5.getHeader(), block5.getCoinbase(), 6);

        List<Sibling> siblings0 = new ArrayList<>();
        List<Sibling> siblings1 = new ArrayList<>();
        List<Sibling> siblings2 = new ArrayList<>();

        siblings0.add(sibling1);
        siblings0.add(sibling2);

        siblings1.add(sibling3);
        siblings1.add(sibling4);

        siblings2.add(sibling5);
        siblings2.add(sibling6);

        provider.getSiblings().put(Long.valueOf(0), siblings0);
        provider.getSiblings().put(Long.valueOf(1), siblings1);
        provider.getSiblings().put(Long.valueOf(2), siblings2);

        provider.save();

        RemascStorageProvider newProvider = new RemascStorageProvider(repository, accountAddress);

        SortedMap<Long, List<Sibling>> map = newProvider.getSiblings();

        Assert.assertNotNull(map);
        Assert.assertFalse(map.isEmpty());

        Assert.assertTrue(map.containsKey(Long.valueOf(0)));
        Assert.assertTrue(map.containsKey(Long.valueOf(1)));
        Assert.assertTrue(map.containsKey(Long.valueOf(2)));

        Assert.assertEquals(2, map.get(Long.valueOf(0)).size());
        Assert.assertEquals(2, map.get(Long.valueOf(1)).size());
        Assert.assertEquals(2, map.get(Long.valueOf(2)).size());

        List<Sibling> list0 = map.get(Long.valueOf(0));
        List<Sibling> list1 = map.get(Long.valueOf(1));
        List<Sibling> list2 = map.get(Long.valueOf(2));

        Assert.assertEquals(1, list0.get(0).getIncludedHeight());
        Assert.assertArrayEquals(genesis.getHeader().getHash(), list0.get(0).getHash());
        Assert.assertEquals(2, list0.get(1).getIncludedHeight());
        Assert.assertArrayEquals(block1.getHeader().getHash(), list0.get(1).getHash());

        Assert.assertEquals(3, list1.get(0).getIncludedHeight());
        Assert.assertArrayEquals(block2.getHeader().getHash(), list1.get(0).getHash());
        Assert.assertEquals(4, list1.get(1).getIncludedHeight());
        Assert.assertArrayEquals(block3.getHeader().getHash(), list1.get(1).getHash());

        Assert.assertEquals(5, list2.get(0).getIncludedHeight());
        Assert.assertArrayEquals(block4.getHeader().getHash(), list2.get(0).getHash());
        Assert.assertEquals(6, list2.get(1).getIncludedHeight());
        Assert.assertArrayEquals(block5.getHeader().getHash(), list2.get(1).getHash());
    }

    private RskAddress randomAddress() {
        byte[] bytes = new byte[20];

        new Random().nextBytes(bytes);

        return new RskAddress(bytes);
    }
}
