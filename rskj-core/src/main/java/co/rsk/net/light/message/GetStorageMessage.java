/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageVisitor;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static co.rsk.net.light.LightClientMessageCodes.GET_STORAGE;

public class GetStorageMessage extends LightClientMessage {

    private final long id;
    private final byte[] blockHash;
    private final byte[] addressHash;
    private final byte[] storageKeyHash;

    public GetStorageMessage(long id, byte[] blockHash, byte[] addressHash, byte[] storageKeyHash) {
        this.id = id;
        this.blockHash = blockHash.clone();
        this.addressHash = addressHash.clone();
        this.storageKeyHash = storageKeyHash.clone();

        this.code = GET_STORAGE.asByte();
    }

    public GetStorageMessage(byte[] encoded) {
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = list.get(0).getRLPData();
        this.id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        this.blockHash = list.get(1).getRLPData();
        this.addressHash = list.get(2).getRLPData();
        this.storageKeyHash = list.get(3).getRLPData();

        this.code = GET_STORAGE.asByte();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(id));
        byte[] rlpBlockHash = RLP.encodeElement(blockHash);
        byte[] rlpAddressHash = RLP.encodeElement(addressHash);
        byte[] rlpStorageKeyHash = RLP.encodeElement(storageKeyHash);
        return RLP.encodeList(rlpId, rlpBlockHash, rlpAddressHash, rlpStorageKeyHash);
    }

    public long getId() {
        return id;
    }

    public byte[] getBlockHash() {
        return blockHash.clone();
    }

    public byte[] getAddressHash() {
        return addressHash.clone();
    }

    public byte[] getStorageKeyHash() {
        return storageKeyHash.clone();
    }

    @Override
    public Class<?> getAnswerMessage() {
        return StorageMessage.class;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public void accept(LightClientMessageVisitor v) {
        v.apply(this);
    }
}
