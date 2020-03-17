package co.rsk.net.light.message;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;
import java.util.Arrays;

public class TransactionIndex extends LightClientMessage {

    private final long id;

    private final long blockNumber;

    private final byte[] blockHash;

    private final long txIndex;
    public TransactionIndex(long id, long blockNumber, byte[] blockHash, long txIndex) {
        this.id = id;
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
        this.txIndex = txIndex;
    }

    public static TransactionIndex decode(byte[] encoded) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = paramsList.get(0).getRLPData();

        long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        byte[] txIndexBytes = paramsList.get(2).getRLPData();
        byte[] blockNumberBytes = paramsList.get(3).getRLPData();
        byte[] blockHash = paramsList.get(1).getRLPData();
        long txIndex = txIndexBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(txIndexBytes).longValue();
        long blockNumber = blockNumberBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(blockNumberBytes).longValue();

        return new TransactionIndex(id, blockNumber, blockHash, txIndex);
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpBlockHash = RLP.encodeElement(this.blockHash);
        byte[] rlpBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.blockNumber));
        byte[] rlpTxIndex = RLP.encodeBigInteger((BigInteger.valueOf(this.txIndex)));

        return RLP.encodeList(rlpId, rlpBlockNumber, rlpBlockHash, rlpTxIndex);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return TransactionIndex.class;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public long getId() {
        return id;
    }

    public byte[] getBlockHash() {
        return blockHash;
    }

    public long getTxIndex() {
        return txIndex;
    }

    @Override
    public String toString() {
        return "Transaction Index: "+
                " txIndex " + txIndex +
                " blockNumber " + blockNumber +
                " blockHash: " + Arrays.toString(blockHash);
    }
}
