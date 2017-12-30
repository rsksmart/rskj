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

import co.rsk.peg.TxSender;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.SHA3Helper;

import java.math.BigInteger;

/**
 * Created by ajlopez on 6/8/2016.
 */
public class SimpleRskTransaction extends Transaction {
    private byte[] hash;

    public SimpleRskTransaction(byte[] hash) {
        super(null);
        this.hash = hash;
        this.sender = new TxSender(ECKey.fromPrivate(SHA3Helper.sha3("cow".getBytes())).getAddress());
    }

    @Override
    public byte[] getHash() { return hash; }

    @Override
    public byte[] getValue() { return BigInteger.valueOf(10000000).toByteArray(); }

    @Override
    public String toString() { return "Tx " + this.getHash().toString(); }
}
