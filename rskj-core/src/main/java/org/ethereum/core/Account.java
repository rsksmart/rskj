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

import co.rsk.core.RskAddress;
import org.ethereum.crypto.ECKey;

/**
 * Representation of an actual account or contract.
 */
public class Account {

    private final ECKey ecKey;
    private final RskAddress addr;

    public Account(ECKey ecKey) {
        this.ecKey = ecKey;
        this.addr = new RskAddress(this.ecKey.getAddress());
    }

    public ECKey getEcKey() {
        return ecKey;
    }

    public RskAddress getAddress() {
        return addr;
    }
}
