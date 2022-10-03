package org.ethereum.core;

import co.rsk.crypto.Keccak256;
import com.google.common.collect.Lists;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import static org.ethereum.crypto.HashUtil.keccak256;

public class BlockHeaderExtension {
    private final int headerVersion;
    private final byte[] logsBloom;

    private Keccak256 hash;

    public int getHeaderVersion() { return headerVersion; }
    public byte[] getLogsBloom() { return logsBloom; }

    public BlockHeaderExtension(int headerVersion, byte[] logsBloom) {
        this.headerVersion = headerVersion;
        this.logsBloom = logsBloom.clone();
    }

    public static BlockHeaderExtension fromRLP(RLPList rlpHeaderExtension) {
        return BlockHeaderExtension.decodeHeaderExtension(rlpHeaderExtension);
    }

    public Keccak256 getHash() {
        if (this.hash == null) {
            this.hash = new Keccak256(HashUtil.keccak256(getEncoded()));
        }

        return this.hash;
    }

    public byte[] getEncoded() {
        byte[] headerVersion = RLP.encodeElement(new byte[]{ (byte) this.headerVersion });
        byte[] logsBloom = RLP.encodeElement(this.logsBloom);

        return RLP.encodeList(Lists.newArrayList(headerVersion, logsBloom).toArray(new byte[][]{}));
    }

    private static BlockHeaderExtension decodeHeaderExtension(RLPList rlpHeaderExtension) {
        int version = rlpHeaderExtension.get(0).getRLPData()[0];
        byte[] logsBloom = rlpHeaderExtension.get(1).getRLPData();

        return new BlockHeaderExtension(version, logsBloom);
    }
}
