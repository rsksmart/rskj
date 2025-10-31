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

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Objects;

public interface BlockHeaderExtension {
    static byte[] toEncoded(BlockHeaderExtension extension) {
        if (!(Objects.requireNonNull(extension) instanceof BlockHeaderExtensionV1)) {
            throw new IllegalArgumentException("Unknown extension");
        }
        if (extension instanceof BlockHeaderExtensionV2) {
            return RLP.encodeList(
                    RLP.encodeByte((byte) 0x2),
                    RLP.encodeElement(extension.getEncoded())
            );
        }
        return RLP.encodeList(
                RLP.encodeByte((byte) 0x1),
                RLP.encodeElement(extension.getEncoded())
        );
    }

    static BlockHeaderExtension fromEncoded(byte[] encoded) {
        RLPList rlpList = RLP.decodeList(encoded);
        if (rlpList.size() != 2) {
            throw new IllegalArgumentException("Invalid extension encoding");
        }
        byte[] versionData = rlpList.get(0).getRLPData();
        byte version = versionData == null || versionData.length == 0 ? 0 : versionData[0];
        if (version == 0x2) {
            return BlockHeaderExtensionV2.fromEncoded(rlpList.get(1).getRLPData());
        }
        if (version == 0x1) {
            return BlockHeaderExtensionV1.fromEncoded(rlpList.get(1).getRLPData());
        }
        throw new IllegalArgumentException("Unknown extension with version: " + version);
    }

    byte[] getEncoded();

    byte[] getHash();

    byte getVersion();
}
