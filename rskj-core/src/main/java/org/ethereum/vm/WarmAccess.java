/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.vm;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import co.rsk.core.RskAddress;

/**
 * Cold/warm resource access according to
 * {@link https://eips.ethereum.org/EIPS/eip-2929}.
 *
 * Must live only withing TX execution context.
 */
public class WarmAccess {

    HashSet<RskAddress> accessedAddresses = new HashSet<>();

    HashMap<RskAddress, HashSet<DataWord>> accessedStorageKeys = new HashMap<>();

    /**
     * Should be initialized with:
     * - the tx.sender, tx.to (or the address being created if it is a contract
     * creation transaction)
     * - and the set of all precompiles.
     */
    public WarmAccess(Collection<RskAddress> warmAddresses) {
        this.accessedAddresses.addAll(warmAddresses);
    }

    /**
     * Make provided addresses warm.
     */
    public void warmAddressesDw(Collection<DataWord> addressDw) {
        var addrs = addressDw.stream().map(RskAddress::new).toList();
        this.warmAddresses(addrs);
    }

    /**
     * Make provided addresses warm.
     */
    public void warmAddresses(Collection<RskAddress> address) {
        this.accessedAddresses.addAll(address);
    }

    /**
     * Make slots warms for a particular smart contract.
     */
    public void warmSlotsDw(DataWord contractAddress, Set<DataWord> slots) {
        this.warmSlots(new RskAddress(contractAddress), slots);
    }

    /**
     * Make slots warms for a particular smart contract.
     */
    public void warmSlots(RskAddress contractAddress, Set<DataWord> slots) {
        var stored = this.accessedStorageKeys.get(contractAddress);
        if (stored == null) {
            this.accessedStorageKeys.put(contractAddress, new HashSet<>(slots));
        } else {
            stored.addAll(slots);
        }
    }

    /**
     * Check if current address warm.
     * Added address to Set at a first call.
     */
    public boolean checkWarmAddress(DataWord address) {
        return this.checkWarmAddress(new RskAddress(address));
    }

    /**
     * Check if current address warm.
     * Added address to Set at a first call.
     */
    public boolean checkWarmAddress(RskAddress address) {
        return !this.accessedAddresses.add(address);
    }

    /**
     * Check if slot was already access for a particular smart contract.
     * Add slot to storage on a first call.
     */
    public boolean checkWarmSlot(DataWord contractAddress, DataWord slot) {
        return this.checkWarmSlot(new RskAddress(contractAddress), slot);
    }

    /**
     * Check if slot was already access for a particular smart contract.
     * Add slot to storage on a first call.
     */
    public boolean checkWarmSlot(RskAddress contractAddress, DataWord slot) {
        var slots = new HashSet<DataWord>();
        slots.add(slot);

        var stored = this.accessedStorageKeys.putIfAbsent(contractAddress, slots);

        // return will be null if there was no value in a map
        if (stored != null) {
            return !stored.add(slot);
        }

        return false;
    }
}
