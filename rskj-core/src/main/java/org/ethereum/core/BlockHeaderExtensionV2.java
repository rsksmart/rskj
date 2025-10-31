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

    private byte[] baseEvent;

    public BlockHeaderExtensionV2(byte[] logsBloom, short[] edges, byte[] baseEvent) {
        super(logsBloom, edges);
        this.baseEvent = baseEvent;
    }

    public static BlockHeaderExtensionV2 fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        byte[] logsBloom = rlpExtension.get(0).getRLPData();
        byte[] baseEvent = rlpExtension.get(1).getRLPData();

        return new BlockHeaderExtensionV2(
                logsBloom,
                rlpExtension.size() == 3 ? toEdges(rlpExtension.get(2).getRLPRawData()) : null,
                baseEvent
        );
    }

    private static short[] toEdges(byte[] rlpData) {
        if (rlpData == null) {
            return null;
        }
        return ByteUtil.rlpToShorts(rlpData);
    }

    @Override
    public byte getVersion() {
        return 0x2;
    }

    public byte[] getBridgeEvent() {
        return baseEvent != null ? Arrays.copyOf(baseEvent, baseEvent.length) : null;
    }

    public void setBridgeEvent(byte[] baseEvent) {
        this.baseEvent = baseEvent != null ? Arrays.copyOf(baseEvent, baseEvent.length) : null;
    }

    @Override
    protected void addElementsEncoded(List<byte[]> fieldToEncodeList) {
        byte[] internalBridgeEvent = this.getBridgeEvent();
        if (internalBridgeEvent != null) {
            fieldToEncodeList.add(RLP.encodeElement(internalBridgeEvent));
        } else {
            fieldToEncodeList.add(RLP.encodeElement(new byte[0]));
        }
        super.addElementsEncoded(fieldToEncodeList);
    }
}