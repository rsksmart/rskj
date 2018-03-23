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

package co.rsk.net.handler;

import co.rsk.TestHelpers.Tx;
import co.rsk.config.TestSystemProperties;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Random;

public class TxPendingValidatorTest {


    Random hashes = new Random(0);

    @Test
    public void nullTx() {
        TxPendingValidator validator = new TxPendingValidator();
        Assert.assertFalse(validator.isValid(null, null));
    }

    @Test
    public void remascTx() {
        TxPendingValidator validator = new TxPendingValidator();
        Assert.assertFalse(validator.isValid(new RemascTransaction(0), null));
    }

    @Test
    public void invalidGas() {
        TxPendingValidator validator = new TxPendingValidator();
        Transaction tx = createTransaction(0, 1000, 0, 0, 0, 0);
        BigInteger gasLimit = BigInteger.valueOf(999);
        Assert.assertFalse(validator.isValid(tx, gasLimit));
    }

    @Test
    public void validGas() {
        TxPendingValidator validator = new TxPendingValidator();
        Transaction tx = createTransaction(0, 1000, 0, 0, 0, 0);
        BigInteger gasLimit = BigInteger.valueOf(1000);
        Assert.assertTrue(validator.isValid(tx, gasLimit));
    }

    private Transaction createTransaction(long value, long gaslimit, long gasprice, long nonce, long data, long sender) {
        return Tx.create(new TestSystemProperties(), value, gaslimit, gasprice, nonce, data, sender);
    }

}
