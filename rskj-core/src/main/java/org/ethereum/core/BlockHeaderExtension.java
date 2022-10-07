package org.ethereum.core;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

public class BlockHeaderExtension {
    private final byte headerVersion;
    private final byte[] logsBloom;

    public byte getHeaderVersion() { return this.headerVersion; }
    public byte[] getLogsBloom() { return this.logsBloom; }

    private BlockHeaderExtension(byte headerVersion, byte[] logsBloom) {
        this.headerVersion = headerVersion;
        this.logsBloom = logsBloom;
    }

    public static BlockHeaderExtension fromHeader(BlockHeader header) {
        return new BlockHeaderExtension(
                header.getVersion(),
                header.getLogsBloom()
        );
    }

    public byte[] getEncoded() {
        return  RLP.encodeList(
                RLP.encodeByte(this.getHeaderVersion()),
                RLP.encodeElement(this.getLogsBloom())
        );
    }

    public static BlockHeaderExtension fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        return new BlockHeaderExtension(
                rlpExtension.get(0).getRLPData()[0],
                rlpExtension.get(1).getRLPData()
        );
    }
}
