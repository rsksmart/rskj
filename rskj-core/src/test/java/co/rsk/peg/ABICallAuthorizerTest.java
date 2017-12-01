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
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ABICallAuthorizerTest {
    @Test
    public void numberOfKeys() {
        ABICallAuthorizer auth = new ABICallAuthorizer(Arrays.asList(
                mock(ECKey.class),
                mock(ECKey.class),
                mock(ECKey.class),
                mock(ECKey.class)
        ));

        Assert.assertEquals(4, auth.getNumberOfAuthorizedKeys());
        Assert.assertEquals(3, auth.getRequiredAuthorizedKeys());
    }

    @Test
    public void getVoter() {
        ABICallAuthorizer auth = new ABICallAuthorizer(Collections.emptyList());

        Transaction mockedTx = mock(Transaction.class);
        when(mockedTx.getSender()).thenReturn(Hex.decode("aabb"));

        Assert.assertEquals(new ABICallVoter(Hex.decode("aabb")), auth.getVoter(mockedTx));
    }

    @Test
    public void isAuthorized() {
        ABICallAuthorizer auth = new ABICallAuthorizer(Arrays.asList(
                ECKey.fromPrivate(BigInteger.valueOf(100L)),
                ECKey.fromPrivate(BigInteger.valueOf(101L)),
                ECKey.fromPrivate(BigInteger.valueOf(102L))
        ));

        for (long n = 100L; n <= 102L; n++) {
            Transaction mockedTx = mock(Transaction.class);
            when(mockedTx.getSender()).thenReturn(ECKey.fromPrivate(BigInteger.valueOf(n)).getAddress());
            Assert.assertTrue(auth.isAuthorized(new ABICallVoter(ECKey.fromPrivate(BigInteger.valueOf(n)).getAddress())));
            Assert.assertTrue(auth.isAuthorized(mockedTx));
        }

        Assert.assertFalse(auth.isAuthorized(new ABICallVoter(ECKey.fromPrivate(BigInteger.valueOf(50L)).getAddress())));
        Transaction mockedTx = mock(Transaction.class);
        when(mockedTx.getSender()).thenReturn(ECKey.fromPrivate(BigInteger.valueOf(50L)).getAddress());
        Assert.assertFalse(auth.isAuthorized(mockedTx));
    }
}
