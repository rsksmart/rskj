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

import static co.rsk.net.light.LightClientMessageCodes.STORAGE;
import static org.ethereum.util.ByteUtil.toHexString;

public class StorageMessage extends LightClientMessage{

    private final long id;
    private final byte[] merkleInclusionProof;
    private final byte[] storageValue;

    public StorageMessage(long id, byte[] merkleInclusionProof, byte[] storageValue) {
        this.id = id;
        this.merkleInclusionProof = merkleInclusionProof.clone();
        this.storageValue = storageValue.clone();

        this.code = STORAGE.asByte();
    }

    public StorageMessage(byte[] encoded) {
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = list.get(0).getRLPData();
        this.id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        this.merkleInclusionProof = list.get(1).getRLPData();
        this.storageValue = list.get(2).getRLPData();

        this.code = STORAGE.asByte();
    }


    public long getId() {
        return id;
    }

    public byte[] getMerkleInclusionProof() {
        return merkleInclusionProof.clone();
    }

    public byte[] getStorageValue() {
        return storageValue.clone();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(id));
        byte[] rlpMerkleInclusionProof = RLP.encodeElement(merkleInclusionProof);
        byte[] rlpStorageValue = RLP.encodeElement(storageValue);
        return RLP.encodeList(rlpId, rlpMerkleInclusionProof, rlpStorageValue);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return "StorageMessage {" +
                "\nid= " + getId() +
                "\nmerkleInclusionProof= " + toHexString(getMerkleInclusionProof()) +
                "\nstorageValue= " + toHexString(getStorageValue()) +
                "\n}";
    }

    @Override
    public void accept(LightClientMessageVisitor v) {
        v.apply(this);
    }
}
