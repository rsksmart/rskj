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

import co.rsk.net.light.LightClientMessageCodes;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

/**
 * Created by Julian Len and Sebastian Sicardi on 01/04/20.
 */
public class AccountsMessage extends LightClientMessage {

    private final long id;
    private final byte[] merkleInclusionProof;
    private final long nonce;
    private final long balance;
    private final byte[] codeHash;
    private final byte[] storageRoot;

    public AccountsMessage(long id, byte[] merkleInclusionProof, long nonce, long balance, byte[] codeHash, byte[] storageRoot) {
        this.id = id;
        this.merkleInclusionProof = merkleInclusionProof.clone();
        this.nonce = nonce;
        this.balance = balance;
        this.codeHash = codeHash.clone();
        this.storageRoot = storageRoot.clone();

        code = LightClientMessageCodes.ACCOUNTS.asByte();
    }

    public AccountsMessage(byte[] encoded) {
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        byte[] rlpId = list.get(0).getRLPData();
        id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

        merkleInclusionProof = list.get(1).getRLPData();

        byte[] nonceBytes = list.get(2).getRLPData();
        nonce =  nonceBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(nonceBytes).longValue();

        byte[] balanceBytes = list.get(3).getRLPData();
        balance = balanceBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(balanceBytes).longValue();

        codeHash = list.get(4).getRLPData();

        storageRoot = list.get(5).getRLPData();

        code = LightClientMessageCodes.ACCOUNTS.asByte();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(id));
        byte[] rlpMerkleInclusionProof = RLP.encodeElement(merkleInclusionProof);
        byte[] rlpNonce = RLP.encodeBigInteger(BigInteger.valueOf(nonce));
        byte[] rlpBalance = RLP.encodeBigInteger(BigInteger.valueOf(balance));
        byte[] rlpCodeHash = RLP.encodeElement(codeHash);
        byte[] rlpStorageRoot = RLP.encodeElement(storageRoot);
        return RLP.encodeList(rlpId, rlpMerkleInclusionProof, rlpNonce, rlpBalance, rlpCodeHash, rlpStorageRoot);
    }

    public long getId() {
        return id;
    }

    public byte[] getMerkleInclusionProof() {
        return merkleInclusionProof.clone();
    }

    public long getNonce() {
        return nonce;
    }

    public long getBalance() {
        return balance;
    }

    public byte[] getCodeHash() {
        return codeHash.clone();
    }

    public byte[] getStorageRoot() {
        return storageRoot.clone();
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return null;
    }
}
