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
import org.ethereum.core.exception.FieldMaxSizeBlockHeaderException;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.ethereum.core.BlockHeaderV2.BASE_EVENT_MAX_SIZE;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * RSKIP-351 block header extension, version 2.
 *
 * <p>Immutable value object: wire layout is
 * {@code [logsBloom, baseEvent, edges?]} (a different field order than
 * version 1, not nested); the hash covers {@code keccak256(logsBloom)},
 * then baseEvent and the edges slot when present. The baseEvent is
 * deliberately not pre-hashed even though RSKIP-351 recommends hashing
 * fields of 32 or more bytes: the format is consensus-fixed and must not
 * change.
 */
public final class BlockHeaderExtensionV2 implements BlockHeaderExtension {
    private final byte[] logsBloom;
    private final short[] txExecutionSublistsEdges;
    private final byte[] baseEvent;

    public BlockHeaderExtensionV2(byte[] logsBloom, short[] edges, byte[] baseEvent) {
        // Pinned quirk: only withBaseEvent() enforces BASE_EVENT_MAX_SIZE, the
        // constructor does not, so oversized data can still reach the value
        // object through here or through fromEncoded().
        this.logsBloom = logsBloom != null ? Arrays.copyOf(logsBloom, logsBloom.length) : null;
        this.txExecutionSublistsEdges = edges != null ? Arrays.copyOf(edges, edges.length) : null;
        this.baseEvent = baseEvent != null ? Arrays.copyOf(baseEvent, baseEvent.length) : null;
    }

    public static BlockHeaderExtensionV2 fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        // Always-present extension fields use getRLPRawData() so an empty element decodes
        // to byte[0] rather than null, consistent with the edges slot (when present) and
        // with how BlockFactory.decodeHeader reads the same fields.
        // Pinned quirk: the baseEvent slot (index 1) is read unconditionally, so a
        // one-element list fails with IndexOutOfBoundsException.
        byte[] logsBloom = rlpExtension.get(0).getRLPRawData();
        byte[] baseEvent = rlpExtension.get(1).getRLPRawData();

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

    @Override
    public byte[] getHash() {
        List<byte[]> fieldsToEncode = Lists.newArrayList(
                RLP.encodeElement(HashUtil.keccak256(this.logsBloom)),
                RLP.encodeElement(Objects.requireNonNullElseGet(this.baseEvent, () -> EMPTY_BYTE_ARRAY)));
        if (this.txExecutionSublistsEdges != null) {
            fieldsToEncode.add(ByteUtil.shortsToRLP(this.txExecutionSublistsEdges));
        }
        return HashUtil.keccak256(RLP.encodeList(fieldsToEncode.toArray(new byte[][]{})));
    }

    @Override
    public byte[] getEncoded() {
        List<byte[]> fieldsToEncode = Lists.newArrayList(
                RLP.encodeElement(this.logsBloom),
                RLP.encodeElement(Objects.requireNonNullElseGet(this.baseEvent, () -> EMPTY_BYTE_ARRAY)));
        if (this.txExecutionSublistsEdges != null) {
            fieldsToEncode.add(ByteUtil.shortsToRLP(this.txExecutionSublistsEdges));
        }
        return RLP.encodeList(fieldsToEncode.toArray(new byte[][]{}));
    }

    public byte[] getBaseEvent() {
        return this.baseEvent;
    }

    public BlockHeaderExtensionV2 withBaseEvent(byte[] baseEvent) {
        if (baseEvent != null && baseEvent.length > BASE_EVENT_MAX_SIZE) {
            throw new FieldMaxSizeBlockHeaderException("baseEvent length cannot exceed " + BASE_EVENT_MAX_SIZE + " bytes");
        }
        return new BlockHeaderExtensionV2(this.logsBloom, this.txExecutionSublistsEdges, baseEvent);
    }

    public byte[] getLogsBloom() {
        return this.logsBloom;
    }

    @Override
    public BlockHeaderExtensionV2 withLogsBloom(byte[] logsBloom) {
        // Pinned quirk, kept from the old setter: a null logsBloom throws
        // NullPointerException (Arrays.copyOf) while the constructor accepts null.
        byte[] copy = Arrays.copyOf(logsBloom, logsBloom.length);
        return new BlockHeaderExtensionV2(copy, this.txExecutionSublistsEdges, this.baseEvent);
    }

    public short[] getTxExecutionSublistsEdges() {
        return this.txExecutionSublistsEdges;
    }

    @Override
    public BlockHeaderExtensionV2 withTxExecutionSublistsEdges(short[] edges) {
        return new BlockHeaderExtensionV2(this.logsBloom, edges, this.baseEvent);
    }
}
