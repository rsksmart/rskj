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

/**
 * RSKIP-351 block header extension, version 1.
 *
 * <p>Immutable value object: wire layout is
 * {@code [logsBloom, edges?]}; the hash covers
 * {@code keccak256(logsBloom)} instead of the raw logsBloom, plus the
 * edges slot when present.
 */
public final class BlockHeaderExtensionV1 implements BlockHeaderExtension {
    private final byte[] logsBloom;
    private final short[] txExecutionSublistsEdges;

    public BlockHeaderExtensionV1(byte[] logsBloom, short[] edges) {
        this.logsBloom = logsBloom != null ? Arrays.copyOf(logsBloom, logsBloom.length) : null;
        this.txExecutionSublistsEdges = edges != null ? Arrays.copyOf(edges, edges.length) : null;
    }

    public static BlockHeaderExtensionV1 fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        return new BlockHeaderExtensionV1(
                rlpExtension.get(0).getRLPRawData(),
                rlpExtension.size() == 2 ? ByteUtil.rlpToShorts(rlpExtension.get(1).getRLPRawData()) : null
        );
    }

    @Override
    public byte getVersion() {
        return 0x1;
    }

    @Override
    public byte[] getHash() {
        List<byte[]> fieldsToEncode = Lists.newArrayList(RLP.encodeElement(HashUtil.keccak256(this.logsBloom)));
        if (this.txExecutionSublistsEdges != null) {
            fieldsToEncode.add(ByteUtil.shortsToRLP(this.txExecutionSublistsEdges));
        }
        return HashUtil.keccak256(RLP.encodeList(fieldsToEncode.toArray(new byte[][]{})));
    }

    @Override
    public byte[] getEncoded() {
        List<byte[]> fieldsToEncode = Lists.newArrayList(RLP.encodeElement(this.logsBloom));
        if (this.txExecutionSublistsEdges != null) {
            fieldsToEncode.add(ByteUtil.shortsToRLP(this.txExecutionSublistsEdges));
        }
        return RLP.encodeList(fieldsToEncode.toArray(new byte[][]{}));
    }

    public byte[] getLogsBloom() {
        return this.logsBloom;
    }

    @Override
    public BlockHeaderExtensionV1 withLogsBloom(byte[] logsBloom) {
        // Pinned quirk, kept from the old setter: a null logsBloom throws
        // NullPointerException (Arrays.copyOf) while the constructor accepts null.
        byte[] copy = Arrays.copyOf(logsBloom, logsBloom.length);
        return new BlockHeaderExtensionV1(copy, this.txExecutionSublistsEdges);
    }

    public short[] getTxExecutionSublistsEdges() {
        return this.txExecutionSublistsEdges != null
                ? Arrays.copyOf(this.txExecutionSublistsEdges, this.txExecutionSublistsEdges.length)
                : null;
    }

    @Override
    public BlockHeaderExtensionV1 withTxExecutionSublistsEdges(short[] edges) {
        return new BlockHeaderExtensionV1(this.logsBloom, edges);
    }
}
