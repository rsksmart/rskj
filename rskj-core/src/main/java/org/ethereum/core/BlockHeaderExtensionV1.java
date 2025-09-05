package org.ethereum.core;

import com.google.common.collect.Lists;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public class BlockHeaderExtensionV1 implements BlockHeaderExtension {
    private byte[] logsBloom;
    private short[] txExecutionSublistsEdges;
    private long lastIncreasedToBlockGasLimitBlockNumber;
    private short[] txsIndexForIncreasedBlockGasLimit;

    public BlockHeaderExtensionV1(byte[] logsBloom, short[] edges, long lastIncreasedToBlockGasLimitBlockNumber, short[] txsIndexForIncreasedBlockGasLimit) {
        this.logsBloom = logsBloom;
        this.txExecutionSublistsEdges = edges != null ? Arrays.copyOf(edges, edges.length) : null;
        this.lastIncreasedToBlockGasLimitBlockNumber = lastIncreasedToBlockGasLimitBlockNumber;
        this.txsIndexForIncreasedBlockGasLimit = txsIndexForIncreasedBlockGasLimit != null ? Arrays.copyOf(txsIndexForIncreasedBlockGasLimit, txsIndexForIncreasedBlockGasLimit.length) : null;
    }

    @Override
    public byte[] getHash() {
        return HashUtil.keccak256(this.getEncodedForHash());
    }

    @Override
    public byte[] getEncoded() {
        List<byte[]> fieldToEncodeList = Lists.newArrayList(RLP.encodeElement(this.getLogsBloom()));
        this.addEdgesEncoded(fieldToEncodeList);
        fieldToEncodeList.add(RLP.encodeBigInteger(BigInteger.valueOf(this.getLastIncreasedToBlockGasLimitBlockNumber())));
        this.addTxsIndexForIncreasedBlockGasLimit(fieldToEncodeList);
        return RLP.encodeList(fieldToEncodeList.toArray(new byte[][]{}));
    }
    public byte[] getLogsBloom() { return this.logsBloom; }
    public void setLogsBloom(byte[] logsBloom) { this.logsBloom = logsBloom; }

    public short[] getTxExecutionSublistsEdges() { return this.txExecutionSublistsEdges != null ? Arrays.copyOf(this.txExecutionSublistsEdges, this.txExecutionSublistsEdges.length) : null; }
    public void setTxExecutionSublistsEdges(short[] edges) { this.txExecutionSublistsEdges =  edges != null? Arrays.copyOf(edges, edges.length) : null; }

    public long getLastIncreasedToBlockGasLimitBlockNumber() { return this.lastIncreasedToBlockGasLimitBlockNumber; }
    public void setLastIncreasedToBlockGasLimitBlockNumber(long lastIncreasedToBlockGasLimitBlockNumber) { this.lastIncreasedToBlockGasLimitBlockNumber = lastIncreasedToBlockGasLimitBlockNumber; }

    public short[] getTxsIndexForIncreasedBlockGasLimit() { return this.txsIndexForIncreasedBlockGasLimit != null ? Arrays.copyOf(this.txsIndexForIncreasedBlockGasLimit, this.txsIndexForIncreasedBlockGasLimit.length) : null; }
    public void setTxsIndexForIncreasedBlockGasLimit(short[] txsIndexForIncreasedBlockGasLimit) { this.txsIndexForIncreasedBlockGasLimit =  txsIndexForIncreasedBlockGasLimit != null? Arrays.copyOf(txsIndexForIncreasedBlockGasLimit, txsIndexForIncreasedBlockGasLimit.length) : null; }

    public static BlockHeaderExtensionV1 fromEncoded(byte[] encoded) {
        RLPList rlpExtension = RLP.decodeList(encoded);
        return new BlockHeaderExtensionV1(
                rlpExtension.get(0).getRLPData(),
                rlpExtension.size() == 2 ? ByteUtil.rlpToShorts(rlpExtension.get(1).getRLPRawData()): null,
                ByteUtil.byteArrayToLong(rlpExtension.get(0).getRLPData()),
                rlpExtension.size() == 2 ? ByteUtil.rlpToShorts(rlpExtension.get(1).getRLPRawData()): null
        );
    }

    private void addEdgesEncoded(List<byte[]> fieldToEncodeList) {
        short[] internalExecutionSublistsEdges = this.getTxExecutionSublistsEdges();
        if (internalExecutionSublistsEdges != null) {
            fieldToEncodeList.add(ByteUtil.shortsToRLP(internalExecutionSublistsEdges));
        }
    }

    private void addTxsIndexForIncreasedBlockGasLimit(List<byte[]> fieldToEncodeList) {
        short[] internalTxsIndexForIncreasedBlockGasLimit = this.getTxsIndexForIncreasedBlockGasLimit();
        if (internalTxsIndexForIncreasedBlockGasLimit != null) {
            fieldToEncodeList.add(ByteUtil.shortsToRLP(internalTxsIndexForIncreasedBlockGasLimit));
        }
    }

    private byte[] getEncodedForHash() {
        List<byte[]> fieldToEncodeList = Lists.newArrayList(RLP.encodeElement(HashUtil.keccak256(this.getLogsBloom())));
        this.addEdgesEncoded(fieldToEncodeList);
        fieldToEncodeList.add(RLP.encodeBigInteger(BigInteger.valueOf(this.getLastIncreasedToBlockGasLimitBlockNumber())));
        this.addTxsIndexForIncreasedBlockGasLimit(fieldToEncodeList);
        return RLP.encodeList(fieldToEncodeList.toArray(new byte[][]{}));
    }
}
