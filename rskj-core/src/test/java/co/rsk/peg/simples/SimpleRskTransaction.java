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

package co.rsk.peg.simples;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;

import java.math.BigInteger;

/**
 * Created by ajlopez on 6/8/2016.
 */
public class SimpleRskTransaction extends Transaction {
    private Keccak256 hash;

    public SimpleRskTransaction(Keccak256 hash) {
        super(null);
        this.hash = hash;
        this.sender = new RskAddress(ECKey.fromPrivate(HashUtil.keccak256("cow".getBytes())).getAddress());
    }

    @Override
    public Keccak256 getHash() { return hash; }

    @Override
    public byte[] getValue() { return BigInteger.valueOf(10000000).toByteArray(); }

    @Override
    public String toString() { return "Tx " + this.getHash().toString(); }
}
