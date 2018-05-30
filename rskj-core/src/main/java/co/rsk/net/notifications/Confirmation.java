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

package co.rsk.net.notifications;

import co.rsk.crypto.Keccak256;
import org.ethereum.util.RLP;

import java.nio.ByteBuffer;

/***
 * A Confirmation stores for a given block its number and its hash.
 * Confirmations are broadcasted to all the peers by Federation members using
 * {@link co.rsk.net.notifications.FederationNotification}
 *
 * @author Diego Masini
 * @author Jose Orlicki
 *
 */
public class Confirmation {
    private long blockNumber;
    private Keccak256 blockHash;

    public Confirmation(long blockNumber, Keccak256 blockHash) {
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public Keccak256 getBlockHash() {
        return blockHash;
    }

    public byte[] getEncoded() {
        byte[] blockNumber = RLP.encodeElement(longToBytes(this.blockNumber));
        byte[] blockHash = RLP.encodeElement(this.blockHash.getBytes());

        return RLP.encodeList(blockNumber, blockHash);
    }

    public Confirmation copy() {
        return new Confirmation(blockNumber, blockHash.copy());
    }

    private byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }
}
