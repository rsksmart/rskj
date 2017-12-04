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

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LockWhitelistTest {
    private List<Address> addresses;
    private LockWhitelist whitelist;

    @Before
    public void createWhitelist() {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        addresses = Arrays.stream(new Integer[]{ 100, 200, 300, 400 })
            .map(i -> BtcECKey.fromPrivate(BigInteger.valueOf(i)).toAddress(params))
            .collect(Collectors.toList());
        whitelist = new LockWhitelist(addresses);
    }

    @Test
    public void getSize() {
        Assert.assertEquals(4, whitelist.getSize().intValue());
    }

    @Test
    public void getAddresses() {
        Assert.assertNotSame(whitelist.getAddresses(), addresses);
        Assert.assertEquals(whitelist.getAddresses(), addresses);
    }

    @Test
    public void isWhitelisted() {
        for (Address addr : addresses) {
            Assert.assertTrue(whitelist.isWhitelisted(addr));
            Assert.assertTrue(whitelist.isWhitelisted(addr.getHash160()));
        }

        Address randomAddress = Address.fromBase58(
          NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
          "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        Assert.assertFalse(whitelist.isWhitelisted(randomAddress));
        Assert.assertFalse(whitelist.isWhitelisted(randomAddress.getHash160()));
    }

    @Test
    public void add() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        Assert.assertFalse(whitelist.isWhitelisted(randomAddress));
        Assert.assertFalse(whitelist.isWhitelisted(randomAddress.getHash160()));

        Assert.assertTrue(whitelist.add(randomAddress));

        Assert.assertTrue(whitelist.isWhitelisted(randomAddress));
        Assert.assertTrue(whitelist.isWhitelisted(randomAddress.getHash160()));

        Assert.assertFalse(whitelist.add(randomAddress));
    }

    @Test
    public void remove() {
        Address existingAddress = addresses.get(2);

        Assert.assertTrue(whitelist.isWhitelisted(existingAddress));
        Assert.assertTrue(whitelist.isWhitelisted(existingAddress.getHash160()));

        Assert.assertTrue(whitelist.remove(existingAddress));

        Assert.assertFalse(whitelist.isWhitelisted(existingAddress));
        Assert.assertFalse(whitelist.isWhitelisted(existingAddress.getHash160()));

        Assert.assertFalse(whitelist.remove(existingAddress));
    }
}
