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

package co.rsk.trie;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.Coin;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieImpl;
import co.rsk.remasc.RemascTransaction;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.TransactionFactoryHelper;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TrieCopierTest {
    private static Random random = new Random();

    @Test
    public void copyTrie() {
        HashMapDB map1 = new HashMapDB();
        TrieStoreImpl store1 = new TrieStoreImpl(map1);
        HashMapDB map2 = new HashMapDB();
        TrieStoreImpl store2 = new TrieStoreImpl(map2);

        int nvalues = 10;
        byte[][] values = createValues(nvalues, 100);

        Trie trie = new TrieImpl(store1, true);

        for (int k = 0; k < nvalues; k++)
            trie = trie.put(k + "", values[k]);

        trie.save();

        TrieCopier.trieStateCopy(store1, store2, trie.getHash());

        Trie result = store2.retrieve(trie.getHash().getBytes());

        Assert.assertNotNull(result);
        Assert.assertEquals(trie.getHash(), result.getHash());

        for (int k = 0; k < nvalues; k++)
            Assert.assertArrayEquals(trie.get(k + ""), result.get(k + ""));
    }

    @Test
    public void copyThreeTries() {
        HashMapDB map1 = new HashMapDB();
        TrieStoreImpl store1 = new TrieStoreImpl(map1);
        HashMapDB map2 = new HashMapDB();
        TrieStoreImpl store2 = new TrieStoreImpl(map2);

        int nvalues = 30;
        byte[][] values = createValues(nvalues, 100);

        Trie trie = new TrieImpl(store1, true);

        for (int k = 0; k < nvalues - 2; k++)
            trie = trie.put(k + "", values[k]);

        trie.save();
        Keccak256 hash1 = trie.getHash();

        trie.put((nvalues - 2) + "", values[nvalues - 2]);
        trie.save();
        Keccak256 hash2 = trie.getHash();

        trie.put((nvalues - 1) + "", values[nvalues - 1]);
        trie.save();
        Keccak256 hash3 = trie.getHash();

        TrieCopier.trieStateCopy(store1, store2, hash1);
        TrieCopier.trieStateCopy(store1, store2, hash2);
        TrieCopier.trieStateCopy(store1, store2, hash3);

        Trie result1 = store2.retrieve(hash1.getBytes());

        Assert.assertNotNull(result1);
        Assert.assertEquals(hash1, result1.getHash());

        for (int k = 0; k < nvalues - 2; k++)
            Assert.assertArrayEquals(trie.get(k + ""), result1.get(k + ""));

        Trie result2 = store2.retrieve(hash2.getBytes());

        Assert.assertNotNull(result2);
        Assert.assertEquals(hash2, result2.getHash());
        Assert.assertNull(result1.get((nvalues - 2) + ""));
        Assert.assertArrayEquals(trie.get((nvalues - 2) + ""), result2.get((nvalues - 2) + ""));

        Trie result3 = store2.retrieve(hash3.getBytes());

        Assert.assertNotNull(result3);
        Assert.assertEquals(hash3, result3.getHash());
        Assert.assertNull(result1.get((nvalues - 1) + ""));
        Assert.assertNull(result2.get((nvalues - 1) + ""));
        Assert.assertArrayEquals(trie.get((nvalues - 1) + ""), result3.get((nvalues - 1) + ""));
    }

    @Test
    public void copyBlockchainHeightTwoStates() {
        TrieStore store = new TrieStoreImpl(new HashMapDB().setClearOnClose(false));
        TrieStore store2 = new TrieStoreImpl(new HashMapDB().setClearOnClose(false));
        Repository repository = new MutableRepository(new MutableTrieImpl(new TrieImpl(store, true)));
        World world = new World(repository);

        Blockchain blockchain = createBlockchain(world);

        addBlocks(world, blockchain, 10);

        byte[] state8 = blockchain.getBlockByNumber(8).getStateRoot();
        byte[] state9 = blockchain.getBlockByNumber(9).getStateRoot();

        TrieCopier.trieStateCopy(store, store2, blockchain, 9);

        Repository repository91 = repository.getSnapshotTo(state9);
        Repository repository92 = new MutableRepository(new MutableTrieImpl(new TrieImpl(store2, false))).getSnapshotTo(state9);

        Assert.assertNotNull(repository91);
        Assert.assertNotNull(repository92);

        Account account1 = new AccountBuilder().name("account1").balance(new Coin(BigInteger.valueOf(10000000))).build();
        Assert.assertEquals(repository91.getBalance(account1.getAddress()), repository92.getBalance(account1.getAddress()));

        Repository repository81 = repository.getSnapshotTo(state8);

        Assert.assertNotNull(repository81);
        Assert.assertNull(store2.retrieve(state8));
    }

    @Test
    public void copyBlockchainHeightTwoContractStates() {
        /* Temporary deactivated
        TrieStore store = new TrieStoreImpl(new HashMapDB().setClearOnClose(false));
        TrieStore store2 = new TrieStoreImpl(new HashMapDB().setClearOnClose(false));
        Repository repository = new RepositoryImpl(store);
        World world = new World(repository);

        Blockchain blockchain = createBlockchain(world);

        addBlocks(world, blockchain, 100);

        byte[] state99 = blockchain.getBlockByNumber(99).getStateRoot();

        TrieCopier.trieContractStateCopy(store2, blockchain, 99, 100, world.getRepository(), PrecompiledContracts.REMASC_ADDR);

        Repository repository99 = repository.getSnapshotTo(state99);
        AccountState accountState99 = repository99.getAccountState(PrecompiledContracts.REMASC_ADDR);
        Assert.assertNotNull(store2.retrieve(accountState99.getStateRoot()));
         */
    }

    private static Blockchain createBlockchain(World world) {
        // add accounts with balance to genesis state
        new AccountBuilder(world).name("account1").balance(new Coin(BigInteger.valueOf(10000000))).build();
        new AccountBuilder(world).name("account2").balance(new Coin(BigInteger.valueOf(10000000))).build();

        return world.getBlockChain();
    }

    private static void addBlocks(World world, Blockchain blockchain, int nblocks) {
        for (int k = 0; k < nblocks; k++) {
            Transaction tx = TransactionFactoryHelper.createSampleTransaction(1, 2, 100, k);
            Transaction rtx = new RemascTransaction(blockchain.getBestBlock().getNumber() + 1);
            List<Transaction> txs = new ArrayList<>();
            txs.add(tx);
            txs.add(rtx);

            Block block = new BlockGenerator().createChildBlock(blockchain.getBestBlock(), txs);
            BlockExecutor blockExecutor = world.getBlockExecutor();
            blockExecutor.executeAndFillAll(block, blockchain.getBestBlock());

            Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block));
            Assert.assertEquals(block.getHash(), blockchain.getBestBlock().getHash());
        }
    }

    private byte[][] createValues(int nvalues, int length) {
        byte[][] values = new byte[nvalues][];

        for (int k = 0; k < nvalues; k++) {
            byte[] value = new byte[length];
            random.nextBytes(value);
            values[k] = value;
        }

        return values;
    }
}
