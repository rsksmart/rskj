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
import co.rsk.bitcoinj.core.Coin;
import com.google.common.primitives.UnsignedBytes;

import java.util.*;

/**
 * Represents a lock whitelist
 * for btc lock transactions.
 * It's basically a list of btc addresses
 * with operations to manipulate and query it.
 *
 * @author Ariel Mendelzon
 */
public class LockWhitelist {

    private static final Comparator<Address> LEXICOGRAPHICAL_COMPARATOR = (a1, a2)
            -> UnsignedBytes.lexicographicalComparator().compare(a1.getHash160(), a2.getHash160());

    private SortedMap<Address, Coin> whitelistedAddresses;

    public LockWhitelist(Map<Address, Coin> whitelistedAddresses) {
        // Save a copy so that this can't be modified from the outside
        SortedMap<Address, Coin> sortedWhitelistedAddresses = new TreeMap<>(LEXICOGRAPHICAL_COMPARATOR);
        sortedWhitelistedAddresses.putAll(whitelistedAddresses);
        this.whitelistedAddresses = sortedWhitelistedAddresses;
    }

    public boolean isWhitelisted(Address address) {
        return whitelistedAddresses.containsKey(address);
    }

    public boolean isWhitelisted(byte[] address) {
        return whitelistedAddresses.keySet().stream()
                .map(Address::getHash160)
                .anyMatch(hash -> Arrays.equals(hash, address));
    }

    public boolean isWhitelistedFor(Address address, Coin amount) {
        Coin maxTransferValue = getMaxTransferValue(address);
        return isWhitelisted(address) && maxTransferValue !=null && (amount.isLessThan(maxTransferValue) || amount.equals(maxTransferValue));
    }

    public Integer getSize() {
        return whitelistedAddresses.size();
    }

    public List<Address> getAddresses() {
        // Return a copy so that this can't be modified from the outside
        return new ArrayList<>(whitelistedAddresses.keySet());
    }

    public Coin getMaxTransferValue(Address address) {
        return whitelistedAddresses.get(address);
    }

    public boolean put(Address address, Coin maxTransferValue) {
        if (whitelistedAddresses.containsKey(address)) {
            return false;
        }

        whitelistedAddresses.put(address, maxTransferValue);
        return true;
    }

    public boolean remove(Address address) {
        return whitelistedAddresses.remove(address) != null;
    }
}
