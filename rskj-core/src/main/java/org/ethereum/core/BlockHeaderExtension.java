package org.ethereum.core;

import org.ethereum.util.RLPList;

public interface BlockHeaderExtension {
    byte[] getEncoded();
    byte[] getHash();

    static BlockHeaderExtension fromEncoded(RLPList encoded) {
        byte version = encoded.get(0).getRLPData()[0];
        if (version == 0x1) {
            return BlockHeaderExtensionV1.fromEncoded(encoded.getRLPData());
        }
        return null;
    }
}
