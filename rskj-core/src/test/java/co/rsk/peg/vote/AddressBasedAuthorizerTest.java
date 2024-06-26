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

package co.rsk.peg.vote;

import co.rsk.core.RskAddress;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AddressBasedAuthorizerTest {
    @Test
    void numberOfKeys_one() {
        AddressBasedAuthorizer auth = new AddressBasedAuthorizer(Arrays.asList(
            mock(ECKey.class),
            mock(ECKey.class),
            mock(ECKey.class),
            mock(ECKey.class)
        ), AddressBasedAuthorizer.MinimumRequiredCalculation.ONE);

        Assertions.assertEquals(4, auth.getNumberOfAuthorizedKeys());
        Assertions.assertEquals(1, auth.getRequiredAuthorizedKeys());
    }

    @Test
    void numberOfKeys_majority() {
        AddressBasedAuthorizer auth = new AddressBasedAuthorizer(Arrays.asList(
            mock(ECKey.class),
            mock(ECKey.class),
            mock(ECKey.class),
            mock(ECKey.class)
        ), AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        Assertions.assertEquals(4, auth.getNumberOfAuthorizedKeys());
        Assertions.assertEquals(3, auth.getRequiredAuthorizedKeys());
    }

    @Test
    void numberOfKeys_all() {
        AddressBasedAuthorizer auth = new AddressBasedAuthorizer(Arrays.asList(
            mock(ECKey.class),
            mock(ECKey.class),
            mock(ECKey.class),
            mock(ECKey.class)
        ), AddressBasedAuthorizer.MinimumRequiredCalculation.ALL);

        Assertions.assertEquals(4, auth.getNumberOfAuthorizedKeys());
        Assertions.assertEquals(4, auth.getRequiredAuthorizedKeys());
    }

    @Test
    void isAuthorized() {
        AddressBasedAuthorizer auth = new AddressBasedAuthorizer(Arrays.asList(
            ECKey.fromPrivate(BigInteger.valueOf(100L)),
            ECKey.fromPrivate(BigInteger.valueOf(101L)),
            ECKey.fromPrivate(BigInteger.valueOf(102L))
        ), AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        for (long n = 100L; n <= 102L; n++) {
            Transaction mockedTx = mock(Transaction.class);
            when(mockedTx.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(n)).getAddress()));
            Assertions.assertTrue(auth.isAuthorized(new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(n)).getAddress())));
            Assertions.assertTrue(auth.isAuthorized(mockedTx, new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
        }

        Assertions.assertFalse(auth.isAuthorized(new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(50L)).getAddress())));
        Transaction mockedTx = mock(Transaction.class);
        when(mockedTx.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(ECKey.fromPrivate(BigInteger.valueOf(50L)).getAddress()));
        Assertions.assertFalse(auth.isAuthorized(mockedTx, new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
    }
}
