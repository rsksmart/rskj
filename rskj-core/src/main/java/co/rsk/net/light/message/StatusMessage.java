/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light.message;

import co.rsk.core.BlockDifficulty;
import co.rsk.net.light.LightClientMessageCodes;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static co.rsk.net.light.LightClientMessageCodes.STATUS;

public class StatusMessage extends LightClientMessage {

    private final byte protocolVersion;
    private final int networkId;
    private final BlockDifficulty totalDifficulty;
    private final byte[] bestHash;
    private final long bestNumber;
    private final byte[] genesisHash;
    private final long id;

    public StatusMessage(long id, byte protocolVersion, int networkId,
                         BlockDifficulty totalDifficulty, byte[] bestHash, long bestNumber, byte[] genesisHash) {
        this.id = id;
        this.protocolVersion = protocolVersion;
        this.networkId = networkId;
        this.totalDifficulty = totalDifficulty;
        this.bestHash = bestHash.clone();
        this.bestNumber = bestNumber;
        this.genesisHash = genesisHash.clone();
        this.code = STATUS.asByte();
    }

    public StatusMessage(byte[] encoded) {
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = list.get(0).getRLPData();
        this.id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        byte[] rlpProtocolVersion = list.get(1).getRLPData();
        this.protocolVersion = rlpProtocolVersion == null ? (byte) 0 : BigIntegers.fromUnsignedByteArray(rlpProtocolVersion).byteValue();
        byte[] rlpNetworkId = list.get(2).getRLPData();
        this.networkId = rlpNetworkId == null ? (byte) 0 : BigIntegers.fromUnsignedByteArray(rlpNetworkId).intValue();
        byte[] rlpTotalDifficulty = list.get(3).getRLPData();
        this.totalDifficulty = rlpTotalDifficulty == null ? BlockDifficulty.ZERO : new BlockDifficulty(BigIntegers.fromUnsignedByteArray(rlpTotalDifficulty));
        this.bestHash = list.get(4).getRLPData();
        byte[] rlpBestNumber = list.get(5).getRLPData();
        this.bestNumber = rlpBestNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpBestNumber).longValue();
        this.genesisHash = list.get(6).getRLPData();
        this.code = STATUS.asByte();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpProtocolVersion = RLP.encodeBigInteger(BigInteger.valueOf(getProtocolVersion()));
        byte[] rlpNetworkId = RLP.encodeBigInteger(BigInteger.valueOf(getNetworkId()));
        byte[] rlpTotalDifficulty = RLP.encodeBigInteger(getTotalDifficulty().asBigInteger());
        byte[] rlpBestHash = RLP.encodeElement(getBestHash());
        byte[] rlpBestNumber = RLP.encodeBigInteger(BigInteger.valueOf(getBestNumber()));
        byte[] rlpGenesisHash = RLP.encodeElement(getGenesisHash());
        return RLP.encodeList(rlpId, rlpProtocolVersion, rlpNetworkId, rlpTotalDifficulty, rlpBestHash, rlpBestNumber, rlpGenesisHash);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public LightClientMessageCodes getCommand() {
        return STATUS;
    }

    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public int getNetworkId() {
        return networkId;
    }

    public BlockDifficulty getTotalDifficulty() {
        return totalDifficulty;
    }

    public byte[] getBestHash() {
        return bestHash.clone();
    }

    public long getBestNumber() {
        return bestNumber;
    }

    public byte[] getGenesisHash() {
        return genesisHash.clone();
    }

    public long getId() {
        return id;
    }
}
