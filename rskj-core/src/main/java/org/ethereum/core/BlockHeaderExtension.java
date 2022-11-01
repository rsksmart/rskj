package org.ethereum.core;

import org.ethereum.util.RLPList;

public abstract class BlockHeaderExtension {
    public abstract byte getHeaderVersion();
    public abstract byte[] getEncoded();

    public static BlockHeaderExtension fromEncoded(RLPList encoded) {
        byte version = encoded.get(0).getRLPData()[0];
        if (version == 0x1) return BlockHeaderExtensionV1.fromEncoded(encoded.getRLPData());
        return null;
    }
}
