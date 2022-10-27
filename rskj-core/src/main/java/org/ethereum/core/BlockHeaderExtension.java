package org.ethereum.core;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import static org.ethereum.crypto.HashUtil.keccak256;

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

    public byte[] getEncodedForHeaderMessage() {
        if (this.headerVersion == 0x1) {
            byte[] logsBloomField = new byte[this.logsBloom.length];
            logsBloomField[0] = this.headerVersion;
            byte[] logsBloomHash = keccak256(this.logsBloom);
            System.arraycopy(logsBloomHash, 0, logsBloomField, 1, logsBloomHash.length);
            return RLP.encodeElement(logsBloomField);
        }
        return RLP.encodeElement(this.logsBloom);
    }

    public static BlockHeaderExtension fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        return new BlockHeaderExtension(
                rlpExtension.get(0).getRLPData()[0],
                rlpExtension.get(1).getRLPData()
        );
    }
}
