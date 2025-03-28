package org.ethereum.core;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Objects;

public interface BlockHeaderExtension {
    byte[] getEncoded();
    byte[] getHash();

    static byte[] toEncoded(BlockHeaderExtension extension) {
        if (!(Objects.requireNonNull(extension) instanceof BlockHeaderExtensionV1)) {
            throw new IllegalArgumentException("Unknown extension");
        }
        return RLP.encodeList(
                RLP.encodeByte((byte) 0x1),
                RLP.encodeElement(extension.getEncoded())
        );
    }

    static BlockHeaderExtension fromEncoded(byte[] encoded) {
        RLPList rlpList = RLP.decodeList(encoded);
        byte[] versionData = rlpList.get(0).getRLPData();
        byte version = versionData == null || versionData.length == 0 ? 0 : versionData[0];
        if (version == 0x1) {
            return BlockHeaderExtensionV1.fromEncoded(rlpList.get(1).getRLPData());
        }
        throw new IllegalArgumentException("Unknown extension with version: " + version);
    }
}
