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

package co.rsk.net.handler.txvalidator;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.net.handler.TxsPerAccount;
import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.LinkedList;

public class TxFilterAccumCostFilterTest {

    private final RskSystemProperties config = new RskSystemProperties();

    @Test
    public void twoTxsValidAccumGasPrice() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        AccountState as1 = Mockito.mock(AccountState.class);
        AccountState as2 = Mockito.mock(AccountState.class);
        AccountState as3 = Mockito.mock(AccountState.class);

        TxsPerAccount tpa1 = new TxsPerAccount();
        TxsPerAccount tpa2 = new TxsPerAccount();


        Mockito.when(tx1.getGasLimit()).thenReturn(BigInteger.valueOf(1).toByteArray());
        Mockito.when(tx2.getGasLimit()).thenReturn(BigInteger.valueOf(1).toByteArray());
        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx1.getValue()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx2.getValue()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx1.getNonce()).thenReturn(BigInteger.valueOf(0).toByteArray());
        Mockito.when(tx2.getNonce()).thenReturn(BigInteger.valueOf(1).toByteArray());

        Mockito.when(as1.getBalance()).thenReturn(Coin.valueOf(1000));
        Mockito.when(as2.getBalance()).thenReturn(Coin.valueOf(4));
        Mockito.when(as3.getBalance()).thenReturn(Coin.valueOf(3));
        Mockito.when(as1.getNonce()).thenReturn(BigInteger.valueOf(0));
        Mockito.when(as2.getNonce()).thenReturn(BigInteger.valueOf(0));
        Mockito.when(as3.getNonce()).thenReturn(BigInteger.valueOf(0));

        TxFilterAccumCostFilter tfacf = new TxFilterAccumCostFilter(config);

        tpa1.setTransactions(new LinkedList<>());
        tpa1.getTransactions().add(tx1);
        tpa1.getTransactions().add(tx2);
        Assert.assertEquals(2, tfacf.filter(as1, tpa1, null).size());
        tpa1.setTransactions(new LinkedList<>());
        tpa1.getTransactions().add(tx1);
        tpa1.getTransactions().add(tx2);
        Assert.assertEquals(2, tfacf.filter(as2, tpa1, null).size());
        tpa1.setTransactions(new LinkedList<>());
        tpa1.getTransactions().add(tx1);
        tpa1.getTransactions().add(tx2);
        Assert.assertEquals(2, tfacf.filter(as3, tpa1, null).size());

        tpa2.setTransactions(new LinkedList<>());
        tpa2.getTransactions().add(tx1);
        tpa2.getTransactions().add(tx2);
        Assert.assertEquals(2, tfacf.filter(as1, tpa2, null).size());
        tpa2.setTransactions(new LinkedList<>());
        tpa2.getTransactions().add(tx1);
        tpa2.getTransactions().add(tx2);
        Assert.assertEquals(2, tfacf.filter(as2, tpa2, null).size());
        tpa2.setTransactions(new LinkedList<>());
        tpa2.getTransactions().add(tx1);
        tpa2.getTransactions().add(tx2);
        Assert.assertEquals(2, tfacf.filter(as3, tpa2, null).size());
    }

    @Test
    public void secondTxInvalidAccumGasPrice() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        AccountState as1 = Mockito.mock(AccountState.class);
        AccountState as2 = Mockito.mock(AccountState.class);
        AccountState as3 = Mockito.mock(AccountState.class);

        TxsPerAccount tpa1 = new TxsPerAccount();
        TxsPerAccount tpa2 = new TxsPerAccount();

        Mockito.when(tx1.getGasLimit()).thenReturn(BigInteger.valueOf(1).toByteArray());
        Mockito.when(tx2.getGasLimit()).thenReturn(BigInteger.valueOf(1).toByteArray());
        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx1.getValue()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx2.getValue()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx1.getNonce()).thenReturn(BigInteger.valueOf(0).toByteArray());
        Mockito.when(tx2.getNonce()).thenReturn(BigInteger.valueOf(1).toByteArray());

        Mockito.when(as1.getBalance()).thenReturn(Coin.valueOf(0));
        Mockito.when(as2.getBalance()).thenReturn(Coin.valueOf(1));
        Mockito.when(as3.getBalance()).thenReturn(Coin.valueOf(2));
        Mockito.when(as1.getNonce()).thenReturn(BigInteger.valueOf(0));
        Mockito.when(as2.getNonce()).thenReturn(BigInteger.valueOf(0));
        Mockito.when(as3.getNonce()).thenReturn(BigInteger.valueOf(0));

        TxFilterAccumCostFilter tfacf = new TxFilterAccumCostFilter(config);

        tpa1.setTransactions(new LinkedList<>());
        tpa1.getTransactions().add(tx1);
        tpa1.getTransactions().add(tx2);
        Assert.assertEquals(0, tfacf.filter(as1, tpa1, null).size());
        tpa1.setTransactions(new LinkedList<>());
        tpa1.getTransactions().add(tx1);
        tpa1.getTransactions().add(tx2);
        Assert.assertEquals(1, tfacf.filter(as2, tpa1, null).size());
        tpa1.setTransactions(new LinkedList<>());
        tpa1.getTransactions().add(tx1);
        tpa1.getTransactions().add(tx2);
        Assert.assertEquals(1, tfacf.filter(as3, tpa1, null).size());

        tpa2.setTransactions(new LinkedList<>());
        tpa2.getTransactions().add(tx1);
        tpa2.getTransactions().add(tx2);
        Assert.assertEquals(0, tfacf.filter(as1, tpa2, null).size());
        tpa2.setTransactions(new LinkedList<>());
        tpa2.getTransactions().add(tx1);
        tpa2.getTransactions().add(tx2);
        Assert.assertEquals(1, tfacf.filter(as2, tpa2, null).size());
        tpa2.setTransactions(new LinkedList<>());
        tpa2.getTransactions().add(tx1);
        tpa2.getTransactions().add(tx2);
        Assert.assertEquals(1, tfacf.filter(as3, tpa2, null).size());
    }

}
