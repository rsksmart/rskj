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

import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Arrays;
import java.util.List;

public class BlockHeaderExtensionV2 extends BlockHeaderExtensionV1 {

    private byte[] superChainDataHash;

    public BlockHeaderExtensionV2(byte[] logsBloom, short[] edges, byte[] superChainDataHash) {
        super(logsBloom, edges);
        this.superChainDataHash = superChainDataHash;
    }

    @Override
    public byte getVersion() {
        return 0x2;
    }

    public byte[] getSuperChainDataHash() {
        return superChainDataHash != null ? Arrays.copyOf(superChainDataHash, superChainDataHash.length) : null;
    }

    public void setSuperChainDataHash(byte[] superChainDataHash) {
        this.superChainDataHash =  superChainDataHash != null ? Arrays.copyOf(superChainDataHash, superChainDataHash.length) : null;
    }

    @Override
    protected void addElementsEncoded(List<byte[]> fieldToEncodeList) {
        super.addElementsEncoded(fieldToEncodeList);
        if (this.superChainDataHash != null) {
            fieldToEncodeList.add(RLP.encodeElement(this.superChainDataHash));
        }
    }

    public static BlockHeaderExtensionV2 fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        return new BlockHeaderExtensionV2(
                rlpExtension.get(0).getRLPData(),
                toEdges(rlpExtension.get(1).getRLPRawData()),
                rlpExtension.get(2).getRLPData()
        );
    }

    private static short[] toEdges(byte[] rlpData) {
        if (rlpData == null) {
            return null;
        }
        return ByteUtil.rlpToShorts(rlpData);
    }
}
