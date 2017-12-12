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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a lock whitelist
 * for btc lock transactions.
 * It's basically a list of btc addresses
 * with operations to manipulate and query it.
 *
 * @author Ariel Mendelzon
 */
public class LockWhitelist {
    private List<Address> whitelistedAddresses;

    public LockWhitelist(List<Address> whitelistedAddresses) {
        // Save a copy so that this can't be modified from the outside
        this.whitelistedAddresses = new ArrayList<>(whitelistedAddresses);
    }

    public boolean isWhitelisted(Address address) {
        return whitelistedAddresses.contains(address);
    }

    public boolean isWhitelisted(byte[] address) {
        return whitelistedAddresses.stream()
                .map(key -> key.getHash160())
                .anyMatch(hash -> Arrays.equals(hash, address));
    }

    public Integer getSize() {
        return whitelistedAddresses.size();
    }

    public List<Address> getAddresses() {
        // Return a copy so that this can't be modified from the outside
        return new ArrayList<>(whitelistedAddresses);
    }

    public boolean add(Address address) {
        if (whitelistedAddresses.contains(address)) {
            return false;
        }

        whitelistedAddresses.add(address);
        return true;
    }

    public boolean remove(Address address) {
        if (!whitelistedAddresses.contains(address)) {
            return false;
        }

        whitelistedAddresses.remove(address);
        return true;
    }
}
