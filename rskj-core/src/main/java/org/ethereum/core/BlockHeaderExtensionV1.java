package org.ethereum.core;

import com.google.common.collect.Lists;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Arrays;
import java.util.List;

public class BlockHeaderExtensionV1 implements BlockHeaderExtension {
    private byte[] logsBloom;
    private short[] txExecutionSublistsEdges;

    public byte[] getLogsBloom() { return this.logsBloom; }
    public void setLogsBloom(byte[] logsBloom) { this.logsBloom = logsBloom; }

    public short[] getTxExecutionSublistsEdges() { return this.txExecutionSublistsEdges != null ? Arrays.copyOf(this.txExecutionSublistsEdges, this.txExecutionSublistsEdges.length) : null; }
    public void setTxExecutionSublistsEdges(short[] edges) { this.txExecutionSublistsEdges =  edges != null? Arrays.copyOf(edges, edges.length) : null; }

    public BlockHeaderExtensionV1(byte[] logsBloom, short[] edges) {
        this.logsBloom = logsBloom;
        this.txExecutionSublistsEdges = edges != null ? Arrays.copyOf(edges, edges.length) : null;
    }

    @Override
    public byte[] getHash() {
        return HashUtil.keccak256(this.getEncodedForHash());
    }

    private void addEdgesEncoded(List<byte[]> fieldToEncodeList) {
        short[] txExecutionSublistsEdges = this.getTxExecutionSublistsEdges();
        if (txExecutionSublistsEdges != null) {
            fieldToEncodeList.add(ByteUtil.shortsToRLP(this.getTxExecutionSublistsEdges()));
        }
    }

    private byte[] getEncodedForHash() {
        List<byte[]> fieldToEncodeList = Lists.newArrayList(RLP.encodeElement(HashUtil.keccak256(this.getLogsBloom())));
        this.addEdgesEncoded(fieldToEncodeList);
        return RLP.encodeList(fieldToEncodeList.toArray(new byte[][]{}));
    }

    @Override
    public byte[] getEncoded() {
        List<byte[]> fieldToEncodeList = Lists.newArrayList(RLP.encodeElement(this.getLogsBloom()));
        this.addEdgesEncoded(fieldToEncodeList);
        return RLP.encodeList(fieldToEncodeList.toArray(new byte[][]{}));
    }

    public static BlockHeaderExtensionV1 fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        return new BlockHeaderExtensionV1(
                rlpExtension.get(0).getRLPData(),
                rlpExtension.size() == 2 ? ByteUtil.rlpToShorts(rlpExtension.get(1).getRLPData()): null
        );
    }
}
