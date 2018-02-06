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
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.PendingState;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.*;

public class MinerUtilsTest {

    private final RskSystemProperties config = new RskSystemProperties();

    @Test
    public void getAllTransactionsTest() {
        PendingState pendingState = Mockito.mock(PendingState.class);

        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);

        byte[] s1 = new byte[32];
        byte[] s2 = new byte[32];

        s1[0] = 0;
        s2[0] = 1;

        Mockito.when(tx1.getHash()).thenReturn(s1);
        Mockito.when(tx2.getHash()).thenReturn(s2);

        List<Transaction> txs1 = new LinkedList<>();
        List<Transaction> txs2 = new LinkedList<>();

        txs1.add(tx1);
        txs2.add(tx2);

        Mockito.when(pendingState.getPendingTransactions()).thenReturn(txs1);
        Mockito.when(pendingState.getWireTransactions()).thenReturn(txs2);

        List<Transaction> res = new MinerUtils().getAllTransactions(pendingState);

        Assert.assertEquals(2, res.size());
    }

    @Test
    public void validTransactionRepositoryNonceTest() {
        Transaction tx = Tx.create(config, 0, 50000, 5, 0, 0, 0, new Random(0));
        //Mockito.when(tx.checkGasPrice(Mockito.any(BigInteger.class))).thenReturn(true);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Map<RskAddress, BigInteger> accountNounces = new HashMap();
        Repository repository = Mockito.mock(Repository.class);
        Mockito.when(repository.getNonce(tx.getSender())).thenReturn(BigInteger.valueOf(0));
        Coin minGasPrice = Coin.valueOf(1L);

        List<Transaction> res = new MinerUtils().filterTransactions(new LinkedList<>(), txs, accountNounces, repository, minGasPrice);
        Assert.assertEquals(1, res.size());
    }

    @Test
    public void validTransactionAccWrapNonceTest() {
        Transaction tx = Tx.create(config, 0, 50000, 5, 1, 0, 0, new Random(0));
        //Mockito.when(tx.checkGasPrice(Mockito.any(BigInteger.class))).thenReturn(true);
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Map<RskAddress, BigInteger> accountNounces = new HashMap();
        accountNounces.put(tx.getSender(), BigInteger.valueOf(0));
        Repository repository = Mockito.mock(Repository.class);
        Coin minGasPrice = Coin.valueOf(1L);

        List<Transaction> res = new MinerUtils().filterTransactions(new LinkedList<>(), txs, accountNounces, repository, minGasPrice);
        Assert.assertEquals(1, res.size());
    }

    @Test
    public void invalidNonceTransactionTest() {
        Transaction tx = Tx.create(config, 0, 50000, 2, 0, 0, 0, new Random(0));
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Map<RskAddress, BigInteger> accountNounces = new HashMap();
        accountNounces.put(tx.getSender(), BigInteger.valueOf(0));
        Repository repository = Mockito.mock(Repository.class);
        Coin minGasPrice = Coin.valueOf(1L);

        List<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = new MinerUtils().filterTransactions(txsToRemove, txs, accountNounces, repository, minGasPrice);
        Assert.assertEquals(0, res.size());
        Assert.assertEquals(0, txsToRemove.size());
    }

    @Test
    public void invalidGasPriceTransactionTest() {
        Transaction tx = Tx.create(config, 0, 50000, 1, 0, 0, 0, new Random(0));
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Map<RskAddress, BigInteger> accountNounces = new HashMap();
        byte[] addressBytes = ByteUtil.leftPadBytes(BigInteger.valueOf(new Random(0).nextLong()).toByteArray(), 20);
        accountNounces.put(new RskAddress(addressBytes), BigInteger.valueOf(0));
        Repository repository = Mockito.mock(Repository.class);
        Coin minGasPrice = Coin.valueOf(2L);

        LinkedList<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = new MinerUtils().filterTransactions(txsToRemove, txs, accountNounces, repository, minGasPrice);
        Assert.assertEquals(0, res.size());
        Assert.assertEquals(1, txsToRemove.size());
    }

    @Test
    public void harmfulTransactionTest() {
        Transaction tx = Tx.create(config, 0, 50000, 1, 0, 0, 0, new Random(0));
        List<Transaction> txs = new LinkedList<>();
        txs.add(tx);
        Mockito.when(tx.getGasPrice()).thenReturn(null);
        Map<RskAddress, BigInteger> accountNounces = new HashMap();
        byte[] addressBytes = ByteUtil.leftPadBytes(BigInteger.valueOf(new Random(0).nextLong()).toByteArray(), 20);
        accountNounces.put(new RskAddress(addressBytes), BigInteger.valueOf(0));
        Repository repository = Mockito.mock(Repository.class);
        Coin minGasPrice = Coin.valueOf(2L);

        LinkedList<Transaction> txsToRemove = new LinkedList<>();
        List<Transaction> res = new MinerUtils().filterTransactions(txsToRemove, txs, accountNounces, repository, minGasPrice);
        Assert.assertEquals(0, res.size());
        Assert.assertEquals(1, txsToRemove.size());
    }
}
