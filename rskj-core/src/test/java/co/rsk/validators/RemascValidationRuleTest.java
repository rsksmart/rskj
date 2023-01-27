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

package co.rsk.validators;

import co.rsk.remasc.RemascTransaction;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mario on 30/12/16.
 */
class RemascValidationRuleTest {

    @Test
    void noTxInTheBlock() {
        Block b = Mockito.mock(Block.class);
        RemascValidationRule rule = new RemascValidationRule();

        Assertions.assertFalse(rule.isValid(b, null));
    }

    @Test
    void noRemascTxInTheBlock() {
        Block b = Mockito.mock(Block.class);

        List<Transaction> tx = new ArrayList<>();
        tx.add(
                Transaction
                        .builder()
                        .nonce(BigInteger.ZERO)
                        .gasPrice(BigInteger.ONE)
                        .gasLimit(BigInteger.TEN)
                        .destination(Hex.decode("0000000000000000000000000000000000000001"))
                        .chainId(Constants.REGTEST_CHAIN_ID)
                        .value(BigInteger.ZERO)
                        .build()
        );

        Mockito.when(b.getTransactionsList()).thenReturn(tx);

        RemascValidationRule rule = new RemascValidationRule();

        Assertions.assertFalse(rule.isValid(b, null));
    }

    @Test
    void remascTxIsNotTheLastOne() {
        Block b = Mockito.mock(Block.class);

        List<Transaction> tx = new ArrayList<>();
        tx.add(new RemascTransaction(1L));
        tx.add(
                Transaction
                        .builder()
                        .nonce(BigInteger.ZERO)
                        .gasPrice(BigInteger.ONE)
                        .gasLimit(BigInteger.TEN)
                        .destination(Hex.decode("0000000000000000000000000000000000000001"))
                        .chainId(Constants.REGTEST_CHAIN_ID)
                        .value(BigInteger.ZERO)
                        .build()
        );

        Mockito.when(b.getTransactionsList()).thenReturn(tx);

        RemascValidationRule rule = new RemascValidationRule();

        Assertions.assertFalse(rule.isValid(b, null));
    }

    @Test
    void remascTxInBlock() {
        Block b = Mockito.mock(Block.class);

        List<Transaction> tx = new ArrayList<>();
        tx.add(
                Transaction
                        .builder()
                        .nonce(BigInteger.ZERO)
                        .gasPrice(BigInteger.ONE)
                        .gasLimit(BigInteger.TEN)
                        .destination(Hex.decode("0000000000000000000000000000000000000001"))
                        .chainId(Constants.REGTEST_CHAIN_ID)
                        .value(BigInteger.ZERO)
                        .build()
        );
        tx.add(new RemascTransaction(1L));

        Mockito.when(b.getTransactionsList()).thenReturn(tx);

        RemascValidationRule rule = new RemascValidationRule();

        Assertions.assertTrue(rule.isValid(b, null));
    }
}
