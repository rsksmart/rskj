/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.core;

import co.rsk.core.Coin;
import org.junit.Test;

import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class AccountStateTest {

    @Test
    public void testGetEncoded() {
        String expected = "dc809a0100000000000000000000000000000000000000000000000000";
        AccountState acct = new AccountState(BigInteger.ZERO, new Coin(BigInteger.valueOf(2).pow(200)));
        assertEquals(expected, Hex.toHexString(acct.getEncoded()));
    }

    @Test
    public void encodeDecodeStateWithZeroInStateFlags() {
        AccountState acct = new AccountState(BigInteger.ZERO, new Coin(BigInteger.valueOf(2).pow(200)));
        AccountState result = new AccountState(acct.getEncoded());

        assertEquals(BigInteger.ZERO, result.getNonce());
        assertEquals(BigInteger.valueOf(2).pow(200), result.getBalance().asBigInteger());
        assertEquals(0, result.getStateFlags());
    }

    @Test
    public void encodeDecodeStateWith128InStateFlags() {
        AccountState acct = new AccountState(BigInteger.ZERO, new Coin(BigInteger.valueOf(2).pow(200)));
        acct.setStateFlags(128);
        AccountState result = new AccountState(acct.getEncoded());

        assertEquals(BigInteger.ZERO, result.getNonce());
        assertEquals(BigInteger.valueOf(2).pow(200), result.getBalance().asBigInteger());
        assertEquals(128, result.getStateFlags());
    }

    @Test
    public void encodeDecodeStateWith238InStateFlags() {
        AccountState acct = new AccountState(BigInteger.ZERO, new Coin(BigInteger.valueOf(2).pow(200)));
        acct.setStateFlags(238);
        AccountState result = new AccountState(acct.getEncoded());

        assertEquals(BigInteger.ZERO, result.getNonce());
        assertEquals(BigInteger.valueOf(2).pow(200), result.getBalance().asBigInteger());
        assertEquals(238, result.getStateFlags());
    }
}
