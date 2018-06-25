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

package co.rsk.peg.whitelist;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsInAnyOrder;

public class LockWhitelistTest {
    private Map<Address, LockWhitelistEntry> addresses;
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
            .collect(Collectors.toMap(Function.identity(), i -> new OneOffWhiteListEntry(i, Coin.CENT)));
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
        for (Address address : addresses.keySet()) {
            assertExistance(address, true);
        }

        Address randomAddress = Address.fromBase58(
          NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
          "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        assertExistance(randomAddress, false);
    }

    @Test
    public void addOneOff() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assert.assertTrue(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.CENT)));

        assertExistance(randomAddress, true);

        Assert.assertFalse(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.CENT)));
    }

    @Test
    public void addUnlimited() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assert.assertTrue(whitelist.put(randomAddress, new UnlimitedWhiteListEntry(randomAddress)));

        assertExistance(randomAddress, true);

        Assert.assertFalse(whitelist.put(randomAddress, new UnlimitedWhiteListEntry(randomAddress)));
    }

    @Test
    public void addOneOffAfterUnlimited() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assert.assertTrue(whitelist.put(randomAddress, new UnlimitedWhiteListEntry(randomAddress)));

        assertExistance(randomAddress, true);

        Assert.assertFalse(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.CENT)));
    }

    @Test
    public void addUnlimitedAfterOneOff() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        Assert.assertFalse(whitelist.isWhitelisted(randomAddress));
        Assert.assertFalse(whitelist.isWhitelisted(randomAddress.getHash160()));

        Assert.assertTrue(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.CENT)));

        Assert.assertTrue(whitelist.isWhitelisted(randomAddress));
        Assert.assertTrue(whitelist.isWhitelisted(randomAddress.getHash160()));

        Assert.assertFalse(whitelist.put(randomAddress, new UnlimitedWhiteListEntry(randomAddress)));
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
    public void consume() {
        assertExistance(existingAddress, true);

        whitelist.consume(existingAddress);

        assertExistance(existingAddress, false);

        Assert.assertFalse(whitelist.remove(existingAddress));
    }

    @Test
    public void consumeUnlimited() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assert.assertTrue(whitelist.put(randomAddress, new UnlimitedWhiteListEntry(randomAddress)));

        assertExistance(randomAddress, true);

        whitelist.consume(randomAddress);

        assertExistance(randomAddress, true);
    }

    @Test
    public void canLockOneOff() {

        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assert.assertTrue(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.COIN)));

        assertExistance(randomAddress, true);

        Assert.assertTrue(whitelist.getEntry(randomAddress).canLock(Coin.COIN));
    }

    @Test
    public void cantLockOneOffMoreThanMaxValue() {

        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assert.assertTrue(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.CENT)));

        assertExistance(randomAddress, true);

        Assert.assertFalse(whitelist.getEntry(randomAddress).canLock(Coin.COIN));
    }

    @Test
    public void cantLockOneOffAfterConsume() {

        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        OneOffWhiteListEntry entry = new OneOffWhiteListEntry(randomAddress, Coin.COIN);

        Assert.assertTrue(entry.canLock(Coin.COIN));

        entry.consume();

        Assert.assertFalse(entry.canLock(Coin.COIN));
    }

    @Test
    public void canLockUnlimited() {

        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        UnlimitedWhiteListEntry entry = new UnlimitedWhiteListEntry(randomAddress);

        Assert.assertTrue(entry.canLock(Coin.COIN));
    }

    @Test
    public void canLockUnlimitedAfterConsume() {

        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        UnlimitedWhiteListEntry entry = new UnlimitedWhiteListEntry(randomAddress);

        Assert.assertTrue(entry.canLock(Coin.COIN));

        entry.consume();

        Assert.assertTrue(entry.canLock(Coin.COIN));
    }

    private void assertExistance(Address address, boolean exists) {
        Assert.assertEquals(exists, whitelist.isWhitelisted(address));
        Assert.assertEquals(exists, whitelist.isWhitelisted(address.getHash160()));
    }
}
