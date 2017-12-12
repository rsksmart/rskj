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

import org.ethereum.core.Transaction;

import java.util.Arrays;

/**
 * Immutable representation of a tx
 * sender. It is
 * simple a byte[] wrapper.
 * The byte[] is an RSK address.
 *
 * @author Ariel Mendelzon
 */
public final class TxSender {

    public static TxSender fromTx(Transaction tx) {
        return new TxSender(tx.getSender());
    }
    
    private byte[] senderBytes;

    public TxSender(byte[] senderBytes) {
        this.senderBytes = senderBytes;
    }

    public byte[] getBytes() {
        return senderBytes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        TxSender otherSender = (TxSender) other;
        return Arrays.equals(getBytes(), otherSender.getBytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(senderBytes);
    }
}
