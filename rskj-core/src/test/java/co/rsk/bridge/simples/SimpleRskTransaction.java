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

package co.rsk.bridge.simples;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;

/**
 * Created by ajlopez on 6/8/2016.
 */
public class SimpleRskTransaction extends Transaction {
    private final Keccak256 hash;

    public SimpleRskTransaction(byte[] hash) {
        super(null, null, null, TestUtils.generateAddress(hash != null ? new String(hash) : "address").getBytes(), null, null);
        this.hash = hash == null ? null : new Keccak256(hash);
        this.sender = new RskAddress(ECKey.fromPrivate(Keccak256Helper.keccak256("cow".getBytes())).getAddress());
    }

    @Override
    public Coin getValue() {
        return Coin.valueOf(10000000);
    }
}
