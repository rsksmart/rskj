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
import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

public class TxValidatorAccountBalanceValidatorTest {

    @Test
    public void validAccountBalance() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        Transaction tx3 = Mockito.mock(Transaction.class);
        AccountState as = Mockito.mock(AccountState.class);

        Mockito.when(tx1.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(tx2.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(tx3.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(2));
        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(1));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(10000));
        Mockito.when(tx3.getGasPrice()).thenReturn(Coin.valueOf(5000));
        Mockito.when(as.getBalance()).thenReturn(Coin.valueOf(10000));

        TxValidatorAccountBalanceValidator tvabv = new TxValidatorAccountBalanceValidator();

        Assert.assertTrue(tvabv.validate(tx1, as, null, null, Long.MAX_VALUE, false));
        Assert.assertTrue(tvabv.validate(tx2, as, null, null, Long.MAX_VALUE, false));
        Assert.assertTrue(tvabv.validate(tx3, as, null, null, Long.MAX_VALUE, false));
    }

    @Test
    public void invalidAccountBalance() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        AccountState as = Mockito.mock(AccountState.class);

        Mockito.when(tx1.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(tx2.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(2));
        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(20));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(10));
        Mockito.when(as.getBalance()).thenReturn(Coin.valueOf(19));

        TxValidatorAccountBalanceValidator tvabv = new TxValidatorAccountBalanceValidator();

        Assert.assertFalse(tvabv.validate(tx1, as, null, null, Long.MAX_VALUE, false));
        Assert.assertFalse(tvabv.validate(tx2, as, null, null, Long.MAX_VALUE, false));
    }

    @Test
    public void balanceIsNotValidatedIfFreeTx() {
        Transaction tx = new Transaction(BigInteger.ZERO.toByteArray(),
                BigInteger.ONE.toByteArray(),
                BigInteger.valueOf(21071).toByteArray(),
                new ECKey().getAddress(),
                BigInteger.ZERO.toByteArray(),
                Hex.decode("0001"),
                new RskSystemProperties().getBlockchainConfig().getCommonConstants().getChainId());

        tx.sign(new ECKey().getPrivKeyBytes());

        TxValidatorAccountBalanceValidator tv = new TxValidatorAccountBalanceValidator();

        Assert.assertTrue(tv.validate(tx, new AccountState(), BigInteger.ONE, BigInteger.ONE, Long.MAX_VALUE, true));
    }
}
