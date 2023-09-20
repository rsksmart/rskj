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

package co.rsk.bridge.whitelist;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsInAnyOrder;

class LockWhitelistTest {
    private Map<Address, LockWhitelistEntry> addresses;
    private LockWhitelist whitelist;
    private Address existingAddress;

    @BeforeEach
    void createWhitelist() {
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
    void getSize() {
        Assertions.assertEquals(4, whitelist.getSize().intValue());
    }

    @Test
    void getAddresses() {
        Assertions.assertNotSame(whitelist.getAddresses(), addresses);
        MatcherAssert.assertThat(whitelist.getAddresses(), containsInAnyOrder(addresses.keySet().toArray()));
    }

    @Test
    void isWhitelisted() {
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
    void addOneOff() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assertions.assertTrue(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.CENT)));

        assertExistance(randomAddress, true);

        Assertions.assertFalse(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.CENT)));
    }

    @Test
    void addUnlimited() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assertions.assertTrue(whitelist.put(randomAddress, new UnlimitedWhiteListEntry(randomAddress)));

        assertExistance(randomAddress, true);

        Assertions.assertFalse(whitelist.put(randomAddress, new UnlimitedWhiteListEntry(randomAddress)));
    }

    @Test
    void addOneOffAfterUnlimited() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assertions.assertTrue(whitelist.put(randomAddress, new UnlimitedWhiteListEntry(randomAddress)));

        assertExistance(randomAddress, true);

        Assertions.assertFalse(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.CENT)));
    }

    @Test
    void addUnlimitedAfterOneOff() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        Assertions.assertFalse(whitelist.isWhitelisted(randomAddress));
        Assertions.assertFalse(whitelist.isWhitelisted(randomAddress.getHash160()));

        Assertions.assertTrue(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.CENT)));

        Assertions.assertTrue(whitelist.isWhitelisted(randomAddress));
        Assertions.assertTrue(whitelist.isWhitelisted(randomAddress.getHash160()));

        Assertions.assertFalse(whitelist.put(randomAddress, new UnlimitedWhiteListEntry(randomAddress)));
    }

    @Test
    void remove() {
        Assertions.assertTrue(whitelist.isWhitelisted(existingAddress));
        Assertions.assertTrue(whitelist.isWhitelisted(existingAddress.getHash160()));

        Assertions.assertTrue(whitelist.remove(existingAddress));

        Assertions.assertFalse(whitelist.isWhitelisted(existingAddress));
        Assertions.assertFalse(whitelist.isWhitelisted(existingAddress.getHash160()));

        Assertions.assertFalse(whitelist.remove(existingAddress));
    }

    @Test
    void consume() {
        assertExistance(existingAddress, true);

        whitelist.consume(existingAddress);

        assertExistance(existingAddress, false);

        Assertions.assertFalse(whitelist.remove(existingAddress));
    }

    @Test
    void consumeUnlimited() {
        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assertions.assertTrue(whitelist.put(randomAddress, new UnlimitedWhiteListEntry(randomAddress)));

        assertExistance(randomAddress, true);

        whitelist.consume(randomAddress);

        assertExistance(randomAddress, true);
    }

    @Test
    void canLockOneOff() {

        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assertions.assertTrue(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.COIN)));

        assertExistance(randomAddress, true);

        Assertions.assertTrue(whitelist.get(randomAddress).canLock(Coin.COIN));
    }

    @Test
    void cantLockOneOffMoreThanMaxValue() {

        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        assertExistance(randomAddress, false);

        Assertions.assertTrue(whitelist.put(randomAddress, new OneOffWhiteListEntry(randomAddress, Coin.CENT)));

        assertExistance(randomAddress, true);

        Assertions.assertFalse(whitelist.get(randomAddress).canLock(Coin.COIN));
    }

    @Test
    void cantLockOneOffAfterConsume() {

        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        OneOffWhiteListEntry entry = new OneOffWhiteListEntry(randomAddress, Coin.COIN);

        Assertions.assertTrue(entry.canLock(Coin.COIN));

        entry.consume();

        Assertions.assertFalse(entry.canLock(Coin.COIN));
    }

    @Test
    void canLockUnlimited() {

        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        UnlimitedWhiteListEntry entry = new UnlimitedWhiteListEntry(randomAddress);

        Assertions.assertTrue(entry.canLock(Coin.COIN));
    }

    @Test
    void canLockUnlimitedAfterConsume() {

        Address randomAddress = Address.fromBase58(
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                "n3WzdjG7S2GjDbY1pJYxsY1VSQDkm4KDcm"
        );

        UnlimitedWhiteListEntry entry = new UnlimitedWhiteListEntry(randomAddress);

        Assertions.assertTrue(entry.canLock(Coin.COIN));

        entry.consume();

        Assertions.assertTrue(entry.canLock(Coin.COIN));
    }

    @Test
    void getAllByType() {
        Assertions.assertArrayEquals(
                addresses.values().stream().filter(e -> e.getClass() == OneOffWhiteListEntry.class).map(e-> e.address()).sorted().toArray(),
                whitelist.getAll(OneOffWhiteListEntry.class).stream().map(e-> e.address()).toArray()
        );
        Assertions.assertArrayEquals(
                addresses.values().stream().filter(e -> e.getClass() == UnlimitedWhiteListEntry.class).map(e-> e.address()).sorted().toArray(),
                whitelist.getAll(UnlimitedWhiteListEntry.class).stream().map(e-> e.address()).toArray()
        );
    }

    @Test
    void getAll() {
        Assertions.assertEquals(addresses.size(), whitelist.getAll().size());
    }

    private void assertExistance(Address address, boolean exists) {
        Assertions.assertEquals(exists, whitelist.isWhitelisted(address));
        Assertions.assertEquals(exists, whitelist.isWhitelisted(address.getHash160()));
    }
}
