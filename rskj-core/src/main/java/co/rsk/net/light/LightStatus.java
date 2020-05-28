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


package co.rsk.net.light;

import co.rsk.core.BlockDifficulty;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static org.ethereum.util.ByteUtil.toHexString;

public class LightStatus {
    private final long bestNumber;
    private final byte[] bestHash;
    private final BlockDifficulty totalDifficulty;
    private final byte protocolVersion;
    private final int networkId;
    private final byte[] genesisHash;


    public LightStatus(byte protocolVersion, int networkId, BlockDifficulty totalDifficulty, byte[] bestHash, long bestNumber, byte[] genesisHash) {
        this.protocolVersion = protocolVersion;
        this.networkId = networkId;
        this.genesisHash = genesisHash.clone();
        this.bestNumber = bestNumber;
        this.bestHash = bestHash.clone();
        this.totalDifficulty = totalDifficulty;
    }

    public LightStatus(byte[] encoded) {
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpProtocolVersion = list.get(0).getRLPData();
        this.protocolVersion = rlpProtocolVersion == null ? (byte) 0 : BigIntegers.fromUnsignedByteArray(rlpProtocolVersion).byteValue();
        byte[] rlpNetworkId = list.get(1).getRLPData();
        this.networkId = rlpNetworkId == null ? (byte) 0 : BigIntegers.fromUnsignedByteArray(rlpNetworkId).intValue();
        byte[] rlpTotalDifficulty = list.get(2).getRLPData();
        this.totalDifficulty = rlpTotalDifficulty == null ? BlockDifficulty.ZERO : new BlockDifficulty(BigIntegers.fromUnsignedByteArray(rlpTotalDifficulty));
        this.bestHash = list.get(3).getRLPData();
        byte[] rlpBestNumber = list.get(4).getRLPData();
        this.bestNumber = rlpBestNumber == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpBestNumber).longValue();
        this.genesisHash = list.get(5).getRLPData();
    }

    public long getBestNumber() {
        return this.bestNumber;
    }

    public byte[] getBestHash() {
        return this.bestHash.clone();
    }

    public BlockDifficulty getTotalDifficulty() { return this.totalDifficulty; }

    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public int getNetworkId() {
        return networkId;
    }

    public byte[] getGenesisHash() {
        return genesisHash.clone();
    }

    public byte[] getEncoded() {
        byte[] rlpProtocolVersion = RLP.encodeBigInteger(BigInteger.valueOf(getProtocolVersion()));
        byte[] rlpNetworkId = RLP.encodeBigInteger(BigInteger.valueOf(getNetworkId()));
        byte[] rlpTotalDifficulty = RLP.encodeBigInteger(getTotalDifficulty().asBigInteger());
        byte[] rlpBestHash = RLP.encodeElement(getBestHash());
        byte[] rlpBestNumber = RLP.encodeBigInteger(BigInteger.valueOf(getBestNumber()));
        byte[] rlpGenesisHash = RLP.encodeElement(getGenesisHash());
        return RLP.encodeList(rlpProtocolVersion, rlpNetworkId, rlpTotalDifficulty, rlpBestHash, rlpBestNumber, rlpGenesisHash);
    }

    public String toString() {
        return "[" +
            "\nprotocolVersion= " + getProtocolVersion() +
            "\nnetworkId= " + getNetworkId() +
            "\ntotalDifficulty= " + getTotalDifficulty().toString() +
            "\nbestHash= " + toHexString(getBestHash()) +
            "\nbestNumber= " + getBestNumber() +
            "\nGenesisHash= " + toHexString(getGenesisHash()) +
        "\n]";
    }
}