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

/**
 * Encodes and decodes block header extensions (RSKIP-351) to and from
 * their outer wire format: a two-element RLP list holding the version
 * byte and the encoded extension.
 *
 * <p>Decoding switches on the wire version byte, so the compiler cannot
 * check it against the sealed {@code permits} list; a new permitted version
 * needs a decode case added here by hand, and the codec test fails until
 * every permitted class round-trips.
 */
public final class BlockHeaderExtensionCodec {
    private BlockHeaderExtensionCodec() {
    }

    public static byte[] toEncoded(BlockHeaderExtension extension) {
        return RLP.encodeList(
                RLP.encodeByte(Objects.requireNonNull(extension).getVersion()),
                RLP.encodeElement(extension.getEncoded())
        );
    }

    public static BlockHeaderExtension fromEncoded(byte[] encoded) {
        RLPList rlpList = RLP.decodeList(encoded);
        if (rlpList.size() != 2) {
            throw new IllegalArgumentException("Invalid extension encoding");
        }
        byte[] versionData = rlpList.get(0).getRLPData();
        byte version = versionData == null || versionData.length == 0 ? 0 : versionData[0];
        switch (version) {
            case 0x1:
                return BlockHeaderExtensionV1.fromEncoded(rlpList.get(1).getRLPData());
            case 0x2:
                return BlockHeaderExtensionV2.fromEncoded(rlpList.get(1).getRLPData());
            default:
                throw new IllegalArgumentException("Unknown extension with version: " + version);
        }
    }
}
