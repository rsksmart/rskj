/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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
package org.ethereum.core;

import com.google.common.collect.Lists;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Arrays;
import java.util.List;

public class BlockHeaderExtensionV1 implements BlockHeaderExtension {
    private byte[] logsBloom;
    private short[] txExecutionSublistsEdges;

    public BlockHeaderExtensionV1(byte[] logsBloom, short[] edges) {
        this.logsBloom = logsBloom;
        this.txExecutionSublistsEdges = edges != null ? Arrays.copyOf(edges, edges.length) : null;
    }

    public static BlockHeaderExtensionV1 fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        return new BlockHeaderExtensionV1(
                rlpExtension.get(0).getRLPData(),
                rlpExtension.size() == 2 ? ByteUtil.rlpToShorts(rlpExtension.get(1).getRLPRawData()) : null
        );
    }

    @Override
    public byte getVersion() {
        return 0x1;
    }

    @Override
    public byte[] getHash() {
        return HashUtil.keccak256(this.getEncodedForHash());
    }

    @Override
    public byte[] getEncoded() {
        List<byte[]> fieldToEncodeList = Lists.newArrayList(RLP.encodeElement(this.getLogsBloom()));
        this.addElementsEncoded(fieldToEncodeList);
        return RLP.encodeList(fieldToEncodeList.toArray(new byte[][]{}));
    }

    public byte[] getLogsBloom() {
        return this.logsBloom;
    }

    public void setLogsBloom(byte[] logsBloom) {
        this.logsBloom = Arrays.copyOf(logsBloom, logsBloom.length);
    }

    public short[] getTxExecutionSublistsEdges() {
        return this.txExecutionSublistsEdges != null ? Arrays.copyOf(this.txExecutionSublistsEdges, this.txExecutionSublistsEdges.length) : null;
    }

    public void setTxExecutionSublistsEdges(short[] edges) {
        this.txExecutionSublistsEdges = edges != null ? Arrays.copyOf(edges, edges.length) : null;
    }

    protected void addElementsEncoded(List<byte[]> fieldToEncodeList) {
        short[] internalExecutionSublistsEdges = this.getTxExecutionSublistsEdges();
        if (internalExecutionSublistsEdges != null) {
            fieldToEncodeList.add(ByteUtil.shortsToRLP(internalExecutionSublistsEdges));
        }
    }

    private byte[] getEncodedForHash() {
        List<byte[]> fieldToEncodeList = Lists.newArrayList(RLP.encodeElement(HashUtil.keccak256(this.getLogsBloom())));
        this.addElementsEncoded(fieldToEncodeList);
        return RLP.encodeList(fieldToEncodeList.toArray(new byte[][]{}));
    }
}
