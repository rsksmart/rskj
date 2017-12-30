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

package co.rsk.peg;

import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AddressBasedAuthorizerTest {
    @Test
    public void numberOfKeys_one() {
        AddressBasedAuthorizer auth = new AddressBasedAuthorizer(Arrays.asList(
                mock(ECKey.class),
                mock(ECKey.class),
                mock(ECKey.class),
                mock(ECKey.class)
        ), AddressBasedAuthorizer.MinimumRequiredCalculation.ONE);

        Assert.assertEquals(4, auth.getNumberOfAuthorizedKeys());
        Assert.assertEquals(1, auth.getRequiredAuthorizedKeys());
    }

    @Test
    public void numberOfKeys_majority() {
        AddressBasedAuthorizer auth = new AddressBasedAuthorizer(Arrays.asList(
                mock(ECKey.class),
                mock(ECKey.class),
                mock(ECKey.class),
                mock(ECKey.class)
        ), AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        Assert.assertEquals(4, auth.getNumberOfAuthorizedKeys());
        Assert.assertEquals(3, auth.getRequiredAuthorizedKeys());
    }

    @Test
    public void numberOfKeys_all() {
        AddressBasedAuthorizer auth = new AddressBasedAuthorizer(Arrays.asList(
                mock(ECKey.class),
                mock(ECKey.class),
                mock(ECKey.class),
                mock(ECKey.class)
        ), AddressBasedAuthorizer.MinimumRequiredCalculation.ALL);

        Assert.assertEquals(4, auth.getNumberOfAuthorizedKeys());
        Assert.assertEquals(4, auth.getRequiredAuthorizedKeys());
    }

    @Test
    public void isAuthorized() {
        AddressBasedAuthorizer auth = new AddressBasedAuthorizer(Arrays.asList(
                ECKey.fromPrivate(BigInteger.valueOf(100L)),
                ECKey.fromPrivate(BigInteger.valueOf(101L)),
                ECKey.fromPrivate(BigInteger.valueOf(102L))
        ), AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        for (long n = 100L; n <= 102L; n++) {
            Transaction mockedTx = mock(Transaction.class);
            when(mockedTx.getSender()).thenReturn(new TxSender(ECKey.fromPrivate(BigInteger.valueOf(n)).getAddress()));
            Assert.assertTrue(auth.isAuthorized(new TxSender(ECKey.fromPrivate(BigInteger.valueOf(n)).getAddress())));
            Assert.assertTrue(auth.isAuthorized(mockedTx));
        }

        Assert.assertFalse(auth.isAuthorized(new TxSender(ECKey.fromPrivate(BigInteger.valueOf(50L)).getAddress())));
        Transaction mockedTx = mock(Transaction.class);
        when(mockedTx.getSender()).thenReturn(new TxSender(ECKey.fromPrivate(BigInteger.valueOf(50L)).getAddress()));
        Assert.assertFalse(auth.isAuthorized(mockedTx));
    }
}
