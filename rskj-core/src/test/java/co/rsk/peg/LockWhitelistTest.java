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
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsInAnyOrder;

public class LockWhitelistTest {
    private Map<Address, Coin> addresses;
    private LockWhitelist whitelist;
    private Address existingAddress;

    @Before
    public void createWhitelist() {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        int existingPrivate = 300;
        addresses = Arrays.stream(new Integer[]{ 100, 200, existingPrivate, 400 })
            .map(i -> {
                Address address = BtcECKey.fromPrivate(BigInteger.valueOf(i)).toAddress(params);
                if (i == existingPrivate) {
                    existingAddress = address;
                }
                return address;
            })
            .collect(Collectors.toMap(Function.identity(), i -> Coin.CENT));
        whitelist = new LockWhitelist(addresses, 0);
    }

    @Test
    public void getSize() {
        Assert.assertEquals(4, whitelist.getSize().intValue());
    }

    @Test
    public void getAddresses() {
        Assert.assertNotSame(whitelist.getAddresses(), addresses);
        Assert.assertThat(whitelist.getAddresses(), containsInAnyOrder(addresses.keySet().toArray()));
    }

    @Test
    public void isWhitelisted() {
        for (Address addr : addresses.keySet()) {
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

        Assert.assertTrue(whitelist.put(randomAddress, Coin.CENT));

        Assert.assertTrue(whitelist.isWhitelisted(randomAddress));
        Assert.assertTrue(whitelist.isWhitelisted(randomAddress.getHash160()));

        Assert.assertFalse(whitelist.put(randomAddress, Coin.CENT));
    }

    @Test
    public void remove() {
        Assert.assertTrue(whitelist.isWhitelisted(existingAddress));
        Assert.assertTrue(whitelist.isWhitelisted(existingAddress.getHash160()));

        Assert.assertTrue(whitelist.remove(existingAddress));

        Assert.assertFalse(whitelist.isWhitelisted(existingAddress));
        Assert.assertFalse(whitelist.isWhitelisted(existingAddress.getHash160()));

        Assert.assertFalse(whitelist.remove(existingAddress));
    }

    @Test
    public void isWhitelistedTestnetP2SH() {
        NetworkParameters testnetParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        Address a1 = Address.fromBase58(testnetParams, "n2A2NYcg1Ff8j7gkj9EjBoxZ3RZqQ4Jgi5");
        Address a2 = Address.fromBase58(testnetParams, "2NDtJ4mxAMb3cExY7ooYpQU6RYJU1jyhmNu");
        Address a3 = Address.fromBase58(testnetParams, "mfn7tDVKbZ1iQ4VyXqfGvsJUR2cVGVVa34");

        Assert.assertTrue(Arrays.equals(a1.getHash160(), a2.getHash160()));
        Assert.assertFalse(Arrays.equals(a1.getHash160(), a3.getHash160()));
        Assert.assertFalse(Arrays.equals(a2.getHash160(), a3.getHash160()));

//        Stream<String> whitelistBase58Addresses = Stream.of(
//                "mfn7tDVKbZ1iQ4VyXqfGvsJUR2cVGVVa34",
//                "mzFW1hHTJ5Vm3YG4VewKVdzmm5Vna873Ct",
//                "n2A2NYcg1Ff8j7gkj9EjBoxZ3RZqQ4Jgi5",
//                "n34jKU87CVGgeHgquhpASntARjp9pasPBu"
//        );
//
//        Address nonWhitelistedAddress = Address.fromBase58(testnetParams, "2NDtJ4mxAMb3cExY7ooYpQU6RYJU1jyhmNu");
//
//        LockWhitelist whitelist = new LockWhitelist(
//                whitelistBase58Addresses
//                        .map(addressBase58 -> Address.fromBase58(testnetParams, addressBase58))
//                        .collect(Collectors.toMap(Function.identity(), a -> Coin.CENT))
//        );
//
//        Assert.assertFalse(whitelist.isWhitelisted(nonWhitelistedAddress));
    }
}
