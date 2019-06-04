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

package org.ethereum.db;

import co.rsk.test.World;
import co.rsk.test.builders.BlockBuilder;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.vm.LogInfo;
import org.junit.Assert;
import org.junit.Test;
import org.bouncycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 3/1/2016.
 */
public class ReceiptStoreImplTest {
    @Test
    public void getUnknownKey() {
        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());
        byte[] key = new byte[] { 0x01, 0x02 };

        TransactionInfo result = store.get(key);

        Assert.assertNull(result);
    }

    @Test
    public void addAndGetTransaction() {
        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(blockHash, 42, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes());

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getBlockHash());
        Assert.assertArrayEquals(blockHash, result.getBlockHash());
        Assert.assertEquals(42, result.getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @Test
    public void addAndGetTransactionWith128AsIndex() {
        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(blockHash, 128, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes());

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getBlockHash());
        Assert.assertArrayEquals(blockHash, result.getBlockHash());
        Assert.assertEquals(128, result.getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @Test
    public void addAndGetTransactionWith238AsIndex() {
        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(blockHash, 238, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes());

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getBlockHash());
        Assert.assertArrayEquals(blockHash, result.getBlockHash());
        Assert.assertEquals(238, result.getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @Test
    public void addTwoTransactionsAndGetLastTransaction() {
        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());

        TransactionReceipt receipt0 = createReceipt();
        byte[] blockHash0 = Hex.decode("010203040506070809");

        store.add(blockHash0, 3, receipt0);

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(blockHash, 42, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes());

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getBlockHash());
        Assert.assertArrayEquals(blockHash, result.getBlockHash());
        Assert.assertEquals(42, result.getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @Test
    public void addTwoTransactionsAndGetAllTransactions() {
        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());

        TransactionReceipt receipt0 = createReceipt();
        byte[] blockHash0 = Hex.decode("010203040506070809");

        store.add(blockHash0, 3, receipt0);

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(blockHash, 42, receipt);

        List<TransactionInfo> result = store.getAll(receipt.getTransaction().getHash().getBytes());

        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.size());

        Assert.assertNotNull(result.get(0).getBlockHash());
        Assert.assertArrayEquals(blockHash0, result.get(0).getBlockHash());
        Assert.assertEquals(3, result.get(0).getIndex());
        Assert.assertArrayEquals(receipt0.getEncoded(), result.get(0).getReceipt().getEncoded());

        Assert.assertNotNull(result.get(1).getBlockHash());
        Assert.assertArrayEquals(blockHash, result.get(1).getBlockHash());
        Assert.assertEquals(42, result.get(1).getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result.get(1).getReceipt().getEncoded());
    }

    @Test
    public void getUnknownTransactionByBlock() {
        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());
        TransactionReceipt receipt = createReceipt();

        byte[] blockHash = Hex.decode("010203040506070809");

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes(), blockHash, null);

        Assert.assertNull(result);
    }

    @Test
    public void getTransactionByUnknownBlock() {
        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());
        TransactionReceipt receipt = createReceipt();

        byte[] blockHash0 = Hex.decode("0102030405060708000000000000000000000000000000000000000000000000");
        byte[] blockHash = Hex.decode("0102030405060708090000000000000000000000000000000000000000000000");

        store.add(blockHash, 1, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes(), blockHash0, null);

        Assert.assertNull(result);
    }

    @Test
    public void addTwoTransactionsAndGetTransactionByFirstBlock() {
        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());

        TransactionReceipt receipt0 = createReceipt();
        byte[] blockHash0 = Hex.decode("0102030405060708090000000000000000000000000000000000000000000000");

        store.add(blockHash0, 3, receipt0);

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708000000000000000000000000000000000000000000000000");

        store.add(blockHash, 42, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes(), blockHash0, null);

        Assert.assertNotNull(result.getBlockHash());
        Assert.assertArrayEquals(blockHash0, result.getBlockHash());
        Assert.assertEquals(3, result.getIndex());
        Assert.assertArrayEquals(receipt0.getEncoded(), result.getReceipt().getEncoded());
    }

    @Test
    public void addTwoTransactionsAndGetTransactionBySecondBlock() {
        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());

        TransactionReceipt receipt0 = createReceipt();
        byte[] blockHash0 = Hex.decode("0102030405060708090000000000000000000000000000000000000000000000");

        store.add(blockHash0, 3, receipt0);

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708000000000000000000000000000000000000000000000000");

        store.add(blockHash, 42, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes(), blockHash, null);

        Assert.assertNotNull(result.getBlockHash());
        Assert.assertArrayEquals(blockHash, result.getBlockHash());
        Assert.assertEquals(42, result.getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @Test
    public void addTwoTransactionsAndGetTransactionByDescendantBlocks() {
        World world = new World();
        Block genesis = world.getBlockChain().getBestBlock();

        Block block1a = new BlockBuilder().difficulty(10).parent(genesis).build();
        Block block1b = new BlockBuilder().difficulty(block1a.getDifficulty().asBigInteger().longValue()-1).parent(genesis).build();

        Block block2a = new BlockBuilder().parent(block1a).build();
        Block block2b = new BlockBuilder().parent(block1b).build();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block1a));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block1b));
        Assert.assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block2a));
        Assert.assertEquals(ImportResult.IMPORTED_NOT_BEST, world.getBlockChain().tryToConnect(block2b));

        ReceiptStore store = new ReceiptStoreImpl(new HashMapDB());

        TransactionReceipt receipt0 = createReceipt();
        byte[] blockHash0 = Hex.decode("010203040506070809");

        store.add(block1a.getHash().getBytes(), 3, receipt0);

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.add(block1b.getHash().getBytes(), 42, receipt);

        TransactionInfo result = store.get(receipt.getTransaction().getHash().getBytes(), block2a.getHash().getBytes(), world.getBlockChain().getBlockStore());

        Assert.assertNotNull(result.getBlockHash());
        Assert.assertArrayEquals(block1a.getHash().getBytes(), result.getBlockHash());
        Assert.assertEquals(3, result.getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());

        result = store.get(receipt.getTransaction().getHash().getBytes(), block2b.getHash().getBytes(), world.getBlockChain().getBlockStore());

        Assert.assertNotNull(result.getBlockHash());
        Assert.assertArrayEquals(block1b.getHash().getBytes(), result.getBlockHash());
        Assert.assertEquals(42, result.getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());

        result = store.get(receipt.getTransaction().getHash().getBytes(), genesis.getHash().getBytes(), world.getBlockChain().getBlockStore());

        Assert.assertNull(result);
    }

    // from TransactionTest
    private static TransactionReceipt createReceipt() {
        byte[] stateRoot = Hex.decode("f5ff3fbd159773816a7c707a9b8cb6bb778b934a8f6466c7830ed970498f4b68");
        byte[] gasUsed = Hex.decode("01E848");
        Bloom bloom = new Bloom(Hex.decode("0000000000000000800000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));

        LogInfo logInfo1 = new LogInfo(
                Hex.decode("cd2a3d9f938e13cd947ec05abc7fe734df8dd826"),
                null,
                Hex.decode("a1a1a1")
        );

        List<LogInfo> logs = new ArrayList<>();
        logs.add(logInfo1);

        // TODO calculate cumulative gas
        TransactionReceipt receipt = new TransactionReceipt(stateRoot, gasUsed, gasUsed, bloom, logs, new byte[]{0x01});

        receipt.setTransaction(new Transaction((byte[]) null, null, null, null, null, null));

        return receipt;
    }
}
