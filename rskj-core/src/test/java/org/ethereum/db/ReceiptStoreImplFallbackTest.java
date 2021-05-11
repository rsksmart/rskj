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

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Bloom;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.vm.LogInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ReceiptStoreImplFallbackTest {

    @Test
    public void getOlderReceiptDataViaFallback() {
        HashMapDB hashMapDB = new HashMapDB();
        ReceiptStore storeV1 = new ReceiptStoreImpl(hashMapDB);
        ReceiptStore storeV2 = new ReceiptStoreImplV2(hashMapDB);

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        storeV1.add(blockHash, 42, receipt);

        TransactionInfo result = storeV2.get(receipt.getTransaction().getHash().getBytes(), blockHash).orElse(null);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getBlockHash());
        Assert.assertArrayEquals(blockHash, result.getBlockHash());
        Assert.assertEquals(42, result.getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result.getReceipt().getEncoded());
    }

    @Test
    public void addToExistingOlderReceiptDataViaFallback() {
        HashMapDB hashMapDB = new HashMapDB();
        ReceiptStore storeV1 = new ReceiptStoreImpl(hashMapDB);
        ReceiptStore storeV2 = new ReceiptStoreImplV2(hashMapDB);

        TransactionReceipt receipt = createReceipt();

        byte[] blockHash1 = Hex.decode("0102030405060708");
        byte[] blockHash2 = Hex.decode("0102030405060709");

        storeV1.add(blockHash1, 41, receipt);
        storeV2.add(blockHash2, 42, receipt);

        TransactionInfo result1 = storeV1.get(receipt.getTransaction().getHash().getBytes(), blockHash1).orElse(null);

        Assert.assertNotNull(result1);
        Assert.assertNotNull(result1.getBlockHash());
        Assert.assertArrayEquals(blockHash1, result1.getBlockHash());
        Assert.assertEquals(41, result1.getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result1.getReceipt().getEncoded());

        TransactionInfo result2 = storeV1.get(receipt.getTransaction().getHash().getBytes(), blockHash2).orElse(null);

        Assert.assertNotNull(result2);
        Assert.assertNotNull(result2.getBlockHash());
        Assert.assertArrayEquals(blockHash2, result2.getBlockHash());
        Assert.assertEquals(42, result2.getIndex());
        Assert.assertArrayEquals(receipt.getEncoded(), result2.getReceipt().getEncoded());
    }

    @Test
    public void ignoreNewerReceiptDataByInitialImpl() {
        HashMapDB hashMapDB = new HashMapDB();
        ReceiptStore storeV1 = new ReceiptStoreImpl(hashMapDB);
        ReceiptStore storeV2 = new ReceiptStoreImplV2(hashMapDB);

        TransactionReceipt receipt = createReceipt();
        byte[] blockHash = Hex.decode("0102030405060708");

        storeV2.add(blockHash, 42, receipt);

        TransactionInfo result = storeV1.get(receipt.getTransaction().getHash().getBytes(), blockHash).orElse(null);

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
        receipt.setTransaction(Transaction.builder().build());
        return receipt;
    }
}
