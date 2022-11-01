package org.ethereum.core;

import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

public class BlockHeaderExtensionV1 extends BlockHeaderExtension {
    @Override
    public byte getHeaderVersion() { return 0x1; }

    private byte[] logsBloom;
    public byte[] getLogsBloom() { return this.logsBloom; }
    public void setLogsBloom(byte[] logsBloom) { this.logsBloom = logsBloom; }

    public BlockHeaderExtensionV1(byte[] logsBloom) {
        this.logsBloom = logsBloom;
    }

    public byte[] getHash() {
        return HashUtil.keccak256(this.getEncoded());
    }

    @Override
    public byte[] getEncoded() {
        return  RLP.encodeList(
                RLP.encodeByte(this.getHeaderVersion()),
                RLP.encodeElement(this.getLogsBloom())
        );
    }

    public static BlockHeaderExtensionV1 fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        return new BlockHeaderExtensionV1(
                rlpExtension.get(1).getRLPData()
        );
    }
}
