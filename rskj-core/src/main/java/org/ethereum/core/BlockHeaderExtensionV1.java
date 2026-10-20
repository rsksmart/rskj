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
import java.util.Objects;

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
        appendEdgesIfPresent(fieldsToEncode);
        return HashUtil.keccak256(RLP.encodeList(fieldsToEncode.toArray(new byte[][]{})));
    }

    @Override
    public byte[] getEncoded() {
        List<byte[]> fieldsToEncode = Lists.newArrayList(RLP.encodeElement(this.logsBloom));
        appendEdgesIfPresent(fieldsToEncode);
        return RLP.encodeList(fieldsToEncode.toArray(new byte[][]{}));
    }

    private void appendEdgesIfPresent(List<byte[]> fieldsToEncode) {
        if (this.txExecutionSublistsEdges != null) {
            fieldsToEncode.add(ByteUtil.shortsToRLP(this.txExecutionSublistsEdges));
        }
    }

    @Override
    public byte[] getLogsBloom() {
        return this.logsBloom != null ? Arrays.copyOf(this.logsBloom, this.logsBloom.length) : null;
    }

    @Override
    public BlockHeaderExtensionV1 withLogsBloom(byte[] logsBloom) {
        // Rejects a null logsBloom with NullPointerException; the constructor accepts null.
        return new BlockHeaderExtensionV1(
                Objects.requireNonNull(logsBloom, "logsBloom"), this.txExecutionSublistsEdges);
    }

    @Override
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
