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

import org.ethereum.core.exception.FieldMaxSizeBlockHeaderException;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Block header extension version 3: extends v2 with an additional {@code forkBalanceProof} field
 * in the internal extension RLP (after {@code baseEvent}, before optional transaction sublist edges).
 */
public class BlockHeaderExtensionV3 extends BlockHeaderExtensionV2 {

    public static final int FORK_BALANCE_PROOF_MAX_SIZE = 65_536;

    private byte[] forkBalanceProof;

    public BlockHeaderExtensionV3(byte[] logsBloom, short[] edges, byte[] baseEvent, byte[] forkBalanceProof) {
        super(logsBloom, edges, baseEvent);
        this.forkBalanceProof = copyOrNull(forkBalanceProof);
    }

    public static BlockHeaderExtensionV3 fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        byte[] logsBloom = rlpExtension.get(0).getRLPData();
        byte[] baseEvent = rlpExtension.get(1).getRLPData();
        byte[] forkBalanceProof = rlpExtension.get(2).getRLPData();
        return new BlockHeaderExtensionV3(
                logsBloom,
                rlpExtension.size() == 4 ? toEdges(rlpExtension.get(3).getRLPRawData()) : null,
                baseEvent,
                forkBalanceProof
        );
    }

    private static short[] toEdges(byte[] rlpData) {
        if (rlpData == null) {
            return null;
        }
        return ByteUtil.rlpToShorts(rlpData);
    }

    private static byte[] copyOrNull(byte[] data) {
        return data != null ? Arrays.copyOf(data, data.length) : null;
    }

    @Override
    public byte getVersion() {
        return 0x3;
    }

    public byte[] getForkBalanceProof() {
        return copyOrNull(forkBalanceProof);
    }

    public void setForkBalanceProof(byte[] forkBalanceProof) {
        if (forkBalanceProof != null && forkBalanceProof.length > FORK_BALANCE_PROOF_MAX_SIZE) {
            throw new FieldMaxSizeBlockHeaderException(
                    "forkBalanceProof length cannot exceed " + FORK_BALANCE_PROOF_MAX_SIZE + " bytes");
        }
        this.forkBalanceProof = copyOrNull(forkBalanceProof);
    }

    @Override
    protected void addElementsEncoded(List<byte[]> fieldToEncodeList) {
        byte[] internalBaseEvent = this.getBaseEvent();
        fieldToEncodeList.add(
                RLP.encodeElement(Objects.requireNonNullElseGet(internalBaseEvent, () -> EMPTY_BYTE_ARRAY)));
        fieldToEncodeList.add(
                RLP.encodeElement(Objects.requireNonNullElseGet(forkBalanceProof, () -> EMPTY_BYTE_ARRAY)));
        short[] internalExecutionSublistsEdges = this.getTxExecutionSublistsEdges();
        if (internalExecutionSublistsEdges != null) {
            fieldToEncodeList.add(ByteUtil.shortsToRLP(internalExecutionSublistsEdges));
        }
    }
}
