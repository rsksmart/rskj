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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.AccountState;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;

class TxValidatorAccountBalanceValidatorTest {

    private Constants constants;
    private SignatureCache signatureCache;

    @BeforeEach
    void setUp() {
        constants = Constants.regtest();
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    }

    @Test
    void validAccountBalance() {
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

        TxValidatorAccountBalanceValidator tvabv = new TxValidatorAccountBalanceValidator(constants, signatureCache);

        Assertions.assertTrue(tvabv.validate(tx1, as, null, null, Long.MAX_VALUE, false, null).transactionIsValid());
        Assertions.assertTrue(tvabv.validate(tx2, as, null, null, Long.MAX_VALUE, false, null).transactionIsValid());
        Assertions.assertTrue(tvabv.validate(tx3, as, null, null, Long.MAX_VALUE, false, null).transactionIsValid());
    }

    @Test
    void invalidAccountBalance() {
        Transaction tx1 = Mockito.mock(Transaction.class);
        Transaction tx2 = Mockito.mock(Transaction.class);
        AccountState as = Mockito.mock(AccountState.class);
        RepositorySnapshot rs = Mockito.mock(RepositorySnapshot.class);


        Mockito.when(tx1.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(tx2.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(2));
        Mockito.when(tx1.getGasPrice()).thenReturn(Coin.valueOf(20));
        Mockito.when(tx2.getGasPrice()).thenReturn(Coin.valueOf(10));
        Mockito.when(as.getBalance()).thenReturn(Coin.valueOf(19));
        Mockito.when(rs.getStorageValue(Mockito.any(RskAddress.class), Mockito.any(DataWord.class))).thenReturn(null);
        Mockito.when(rs.getBalance(Mockito.any(RskAddress.class))).thenReturn(Coin.valueOf(19));

        TxValidatorAccountBalanceValidator tvabv = new TxValidatorAccountBalanceValidator(constants, signatureCache);

        Assertions.assertFalse(tvabv.validate(tx1, as, null, null, Long.MAX_VALUE, false, rs).transactionIsValid());
        Assertions.assertFalse(tvabv.validate(tx2, as, null, null, Long.MAX_VALUE, false, rs).transactionIsValid());
    }

    @Test
    void balanceIsNotValidatedIfFreeTx() {
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21071))
                .destination(new ECKey().getAddress())
                .data(Hex.decode("0001"))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(BigInteger.ZERO)
                .build();

        tx.sign(new ECKey().getPrivKeyBytes());

        TxValidatorAccountBalanceValidator tv = new TxValidatorAccountBalanceValidator(constants, signatureCache);

        Assertions.assertTrue(tv.validate(tx, new AccountState(), BigInteger.ONE, Coin.valueOf(1L), Long.MAX_VALUE, true, null).transactionIsValid());
    }

}
