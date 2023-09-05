/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.LegacyAddress;

public class OneOffWhiteListEntry implements LockWhitelistEntry {
    private final LegacyAddress address;
    private final Coin maxTransferValueField;

    private boolean consumed = false;

    public OneOffWhiteListEntry(LegacyAddress address, Coin maxTransferValue) {
        this.address = address;
        this.maxTransferValueField = maxTransferValue;
    }

    public LegacyAddress address() {
        return this.address;
    }

    public Coin maxTransferValue() {
        return this.maxTransferValueField;
    }

    public void consume() {
        this.consumed = true;
    }

    public boolean isConsumed() {
        return this.consumed;
    }

    public boolean canLock(Coin value) {
        return !this.consumed && (this.maxTransferValueField.compareTo(value) >= 0);
    }
}
