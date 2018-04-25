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

import co.rsk.config.TestSystemProperties;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mario on 30/12/16.
 */
public class RemascValidationRuleTest {

    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void noTxInTheBlock() {
        Block b = Mockito.mock(Block.class);
        RemascValidationRule rule = new RemascValidationRule();

        Assert.assertFalse(rule.isValid(b));
    }

    @Test
    public void noRemascTxInTheBlock() {
        Block b = Mockito.mock(Block.class);

        List<Transaction> tx = new ArrayList<>();
        tx.add(Transaction.create(config, "0000000000000000000000000000000000000001", BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE, BigInteger.TEN));

        Mockito.when(b.getTransactionsList()).thenReturn(tx);

        RemascValidationRule rule = new RemascValidationRule();

        Assert.assertFalse(rule.isValid(b));
    }

    @Test
    public void remascTxIsNotTheLastOne() {
        Block b = Mockito.mock(Block.class);

        List<Transaction> tx = new ArrayList<>();
        tx.add(new RemascTransaction(1L));
        tx.add(Transaction.create(config, "0000000000000000000000000000000000000001", BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE, BigInteger.TEN));

        Mockito.when(b.getTransactionsList()).thenReturn(tx);

        RemascValidationRule rule = new RemascValidationRule();

        Assert.assertFalse(rule.isValid(b));
    }

    @Test
    public void remascTxInBlock() {
        Block b = Mockito.mock(Block.class);

        List<Transaction> tx = new ArrayList<>();
        tx.add(Transaction.create(config, "0000000000000000000000000000000000000001", BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE, BigInteger.TEN));
        tx.add(new RemascTransaction(1L));

        Mockito.when(b.getTransactionsList()).thenReturn(tx);

        RemascValidationRule rule = new RemascValidationRule();

        Assert.assertTrue(rule.isValid(b));
    }
}
