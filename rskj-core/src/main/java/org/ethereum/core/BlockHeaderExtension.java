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

/**
 * RSKIP-351 block header extension.
 *
 * <p>The set of extension versions is fixed by consensus (hard forks), so the
 * hierarchy is sealed: adding a version means one final class, one
 * {@code permits} entry and one decode case in {@link BlockHeaderExtensionCodec},
 * and the compiler forces those touch points.
 *
 * <p>Instances are immutable value objects: the arrays passed to the
 * constructors are copied once and getters expose them directly, and the
 * {@code with...} methods derive a new instance with one field replaced.
 * A header pairs with an extension of the same version, enforced by
 * {@code BlockHeader.setExtension}.
 */
public sealed interface BlockHeaderExtension permits BlockHeaderExtensionV1, BlockHeaderExtensionV2 {
    byte getVersion();

    byte[] getEncoded();

    byte[] getHash();

    byte[] getLogsBloom();

    BlockHeaderExtension withLogsBloom(byte[] logsBloom);

    short[] getTxExecutionSublistsEdges();

    BlockHeaderExtension withTxExecutionSublistsEdges(short[] edges);
}
