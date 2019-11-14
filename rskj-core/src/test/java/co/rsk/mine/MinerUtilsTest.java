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

package co.rsk.mine;

import co.rsk.TestHelpers.Tx;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.SenderResolverVisitor;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;

public class MinerUtilsTest {

    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void getAllTransactionsTest() {
        TransactionPool transactionPool = Mockito.mock(TransactionPool.class);

        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);

        byte[] s1 = new byte[32];
        byte[] s2 = new byte[32];

        s1[0] = 0;
        s2[0] = 1;

        byte[] addressBytes = ByteUtil.leftPadBytes(BigInteger.valueOf(new Random(0).nextLong()).toByteArray(), 20);

        Mockito.when(tx1.getHash()).thenReturn(new Keccak256(s1));
        Mockito.when(tx2.getHash()).thenReturn(new Keccak256(s2));
        Mockito.when(tx1.getNonce()).thenReturn(ByteUtil.cloneBytes( BigInteger.ZERO.toByteArray()));
        Mockito.when(tx2.getNonce()).thenReturn(ByteUtil.cloneBytes( BigInteger.TEN.toByteArray()));
        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx1.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes));
        Mockito.when(tx2.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes));

        List<Transaction> txs = new LinkedList<>();

        txs.add(tx1);
        txs.add(tx2);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        List<Transaction> res = new MinerUtils(new SenderResolverVisitor()).getAllTransactions(transactionPool);

        Assert.assertEquals(2, res.size());
    }

    @Test
    public void validTransactionRepositoryNonceTest() {
        Transaction tx = Tx.create(config, 0, 50000, 5, 0, 0, 0);
        //Mockito.when(tx.checkGasPrice(Mockito.any(BigInteger.class))).thenReturn(true);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Map<RskAddress, BigInteger> accountNounces = new HashMap();
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(tx.accept(new SenderResolverVisitor()))).thenReturn(BigInteger.valueOf(0));
        Coin minGasPrice = Coin.valueOf(1L);

        List<Transaction> res = new MinerUtils(new SenderResolverVisitor()).filterTransactions(new LinkedList<>(), txs, accountNounces, repository, minGasPrice);
        Assert.assertEquals(1, res.size());
    }

    @Test
    public void validTransactionAccWrapNonceTest() {
        Transaction tx = Tx.create(config, 0, 50000, 5, 1, 0, 0);
        //Mockito.when(tx.checkGasPrice(Mockito.any(BigInteger.class))).thenReturn(true);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Map<RskAddress, BigInteger> accountNounces = new HashMap();
        accountNounces.put(tx.accept(new SenderResolverVisitor()), BigInteger.valueOf(0));
        Repository repository = Mockito.mock(Repository.class);
        Coin minGasPrice = Coin.valueOf(1L);

        List<Transaction> res = new MinerUtils(new SenderResolverVisitor()).filterTransactions(new LinkedList<>(), txs, accountNounces, repository, minGasPrice);
        Assert.assertEquals(1, res.size());
    }

    @Test
    public void invalidNonceTransactionTest() {
        Transaction tx = Tx.create(config, 0, 50000, 2, 0, 0, 0);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Map<RskAddress, BigInteger> accountNounces = new HashMap();
        accountNounces.put(tx.accept(new SenderResolverVisitor()), BigInteger.valueOf(0));
        Repository repository = Mockito.mock(Repository.class);
        Coin minGasPrice = Coin.valueOf(1L);

        List<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = new MinerUtils(new SenderResolverVisitor()).filterTransactions(txsToRemove, txs, accountNounces, repository, minGasPrice);
        Assert.assertEquals(0, res.size());
        Assert.assertEquals(0, txsToRemove.size());
    }

    @Test
    public void invalidGasPriceTransactionTest() {
        Transaction tx = Tx.create(config, 0, 50000, 1, 0, 0, 0);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Map<RskAddress, BigInteger> accountNounces = new HashMap();
        byte[] addressBytes = ByteUtil.leftPadBytes(BigInteger.valueOf(new Random(0).nextLong()).toByteArray(), 20);
        accountNounces.put(new RskAddress(addressBytes), BigInteger.valueOf(0));
        Repository repository = Mockito.mock(Repository.class);
        Coin minGasPrice = Coin.valueOf(2L);

        LinkedList<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = new MinerUtils(new SenderResolverVisitor()).filterTransactions(txsToRemove, txs, accountNounces, repository, minGasPrice);
        Assert.assertEquals(0, res.size());
        Assert.assertEquals(1, txsToRemove.size());
    }

    @Test
    public void harmfulTransactionTest() {
        Transaction tx = Tx.create(config, 0, 50000, 1, 0, 0, 0);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Mockito.when(tx.getGasPrice()).thenReturn(null);
        Map<RskAddress, BigInteger> accountNounces = new HashMap();
        byte[] addressBytes = ByteUtil.leftPadBytes(BigInteger.valueOf(new Random(0).nextLong()).toByteArray(), 20);
        accountNounces.put(new RskAddress(addressBytes), BigInteger.valueOf(0));
        Repository repository = Mockito.mock(Repository.class);
        Coin minGasPrice = Coin.valueOf(2L);

        LinkedList<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = new MinerUtils(new SenderResolverVisitor()).filterTransactions(txsToRemove, txs, accountNounces, repository, minGasPrice);
        Assert.assertEquals(0, res.size());
        Assert.assertEquals(1, txsToRemove.size());
    }

    @Test
    public void getAllTransactionsCheckOrderTest() {
        TransactionPool transactionPool = Mockito.mock(TransactionPool.class);

        Transaction tx0 = Mockito.mock(Transaction.class);
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        Transaction tx3 = Mockito.mock(Transaction.class);

        byte[] nonce0 = ByteUtil.cloneBytes( BigInteger.valueOf(0).toByteArray());
        byte[] nonce1 = ByteUtil.cloneBytes( BigInteger.valueOf(1).toByteArray());
        byte[] nonce2 = ByteUtil.cloneBytes( BigInteger.valueOf(2).toByteArray());

        byte[] addressBytes = ByteUtil.leftPadBytes(BigInteger.valueOf(new Random(0).nextLong()).toByteArray(), 20);
        Mockito.when(tx0.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes));
        Mockito.when(tx0.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce0));
        Mockito.when(tx0.getGasPrice()).thenReturn(Coin.valueOf(10));

        Mockito.when(tx1.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes));
        Mockito.when(tx1.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce0));
        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(1));

        Mockito.when(tx2.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes));
        Mockito.when(tx2.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce1));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(10));

        Mockito.when(tx3.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes));
        Mockito.when(tx3.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce2));
        Mockito.when(tx3.getGasPrice()).thenReturn(Coin.valueOf(100));

        List<Transaction> txs = new LinkedList<>();

        //Test order with same nonce different price
        txs.add(tx0);
        txs.add(tx1);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        List<Transaction> res = new MinerUtils(new SenderResolverVisitor()).getAllTransactions(transactionPool);

        Assert.assertEquals(2, res.size());
        Assert.assertEquals(res.get(0).getGasPrice(), Coin.valueOf(10));
        Assert.assertEquals(res.get(1).getGasPrice(), Coin.valueOf(1));

        //Test order with same sender, different nonce, different price
        txs = new LinkedList<>();
        txs.add(tx3);
        txs.add(tx1);
        txs.add(tx2);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        res = new MinerUtils(new SenderResolverVisitor()).getAllTransactions(transactionPool);

        Assert.assertEquals(3, res.size());
        Assert.assertEquals(res.get(0).getNonce(), tx1.getNonce());
        Assert.assertEquals(res.get(1).getNonce(), tx2.getNonce());
        Assert.assertEquals(res.get(2).getNonce(), tx3.getNonce());
        Assert.assertEquals(res.get(0).getGasPrice(), Coin.valueOf(1));
        Assert.assertEquals(res.get(1).getGasPrice(), Coin.valueOf(10));
        Assert.assertEquals(res.get(2).getGasPrice(), Coin.valueOf(100));

        // Test order with different sender, nonce and price
        Transaction tx4 = Mockito.mock(Transaction.class);
        Transaction tx5 = Mockito.mock(Transaction.class);
        Transaction tx6 = Mockito.mock(Transaction.class);

        byte[] addressBytes2 = ByteUtil.leftPadBytes(BigInteger.valueOf(new Random(100).nextLong()).toByteArray(), 20);
        Mockito.when(tx4.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes2));
        Mockito.when(tx4.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce0));
        Mockito.when(tx4.getGasPrice()).thenReturn(Coin.valueOf(50));

        Mockito.when(tx5.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes2));
        Mockito.when(tx5.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce1));
        Mockito.when(tx5.getGasPrice()).thenReturn(Coin.valueOf(1000));

        Mockito.when(tx6.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes2));
        Mockito.when(tx6.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce2));
        Mockito.when(tx6.getGasPrice()).thenReturn(Coin.valueOf(1));

        // Test another sender.
        txs.add(tx6);
        txs.add(tx5);
        txs.add(tx4);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        res = new MinerUtils(new SenderResolverVisitor()).getAllTransactions(transactionPool);

        Assert.assertEquals(6, res.size());
        Assert.assertEquals(res.get(0).getGasPrice(), Coin.valueOf(50));
        Assert.assertEquals(res.get(1).getGasPrice(), Coin.valueOf(1000));
        Assert.assertEquals(res.get(2).getGasPrice(), Coin.valueOf(1));
        Assert.assertEquals(res.get(3).getGasPrice(), Coin.valueOf(10));
        Assert.assertEquals(res.get(4).getGasPrice(), Coin.valueOf(100));
        Assert.assertEquals(res.get(5).getGasPrice(), Coin.valueOf(1));

        Transaction tx7 = Mockito.mock(Transaction.class);
        Transaction tx8 = Mockito.mock(Transaction.class);
        Transaction tx9 = Mockito.mock(Transaction.class);

        byte[] addressBytes3 = ByteUtil.leftPadBytes(BigInteger.valueOf(new Random(1000).nextLong()).toByteArray(), 20);
        Mockito.when(tx7.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes3));
        Mockito.when(tx7.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce0));
        Mockito.when(tx7.getGasPrice()).thenReturn(Coin.valueOf(500));

        Mockito.when(tx8.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes3));
        Mockito.when(tx8.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce1));
        Mockito.when(tx8.getGasPrice()).thenReturn(Coin.valueOf(500));

        Mockito.when(tx9.accept(any(SenderResolverVisitor.class))).thenReturn(new RskAddress(addressBytes3));
        Mockito.when(tx9.getNonce()).thenReturn(ByteUtil.cloneBytes(nonce2));
        Mockito.when(tx9.getGasPrice()).thenReturn(Coin.valueOf(2000));

        txs.add(tx7);
        txs.add(tx8);
        txs.add(tx9);

        Mockito.when(transactionPool.getPendingTransactions()).thenReturn(txs);

        res = new MinerUtils(new SenderResolverVisitor()).getAllTransactions(transactionPool);

        Assert.assertEquals(9, res.size());
        Assert.assertEquals(res.get(0).getGasPrice(), Coin.valueOf(500));
        Assert.assertEquals(res.get(1).getGasPrice(), Coin.valueOf(500));
        Assert.assertEquals(res.get(2).getGasPrice(), Coin.valueOf(2000));
        Assert.assertEquals(res.get(3).getGasPrice(), Coin.valueOf(50));
        Assert.assertEquals(res.get(4).getGasPrice(), Coin.valueOf(1000));
        Assert.assertEquals(res.get(5).getGasPrice(), Coin.valueOf(1));
        Assert.assertEquals(res.get(6).getGasPrice(), Coin.valueOf(10));
        Assert.assertEquals(res.get(7).getGasPrice(), Coin.valueOf(100));
        Assert.assertEquals(res.get(8).getGasPrice(), Coin.valueOf(1));
    }
}
