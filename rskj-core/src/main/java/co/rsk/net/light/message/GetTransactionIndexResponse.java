package co.rsk.net.light.message;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;
import java.util.Arrays;

public class GetTransactionIndexResponse extends LightClientMessage{

    private final long blockNumber;
    private final byte[] blockHash;
    private final long txIndex;

    public GetTransactionIndexResponse(long blockNumber, byte[] blockHash, long txIndex) {
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
        this.txIndex = txIndex;
    }

    public static GetTransactionIndexResponse decode(byte[] encoded) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] txIndexBytes = paramsList.get(2).getRLPData();
        byte[] blockNumberBytes = paramsList.get(3).getRLPData();
        byte[] blockHash = paramsList.get(1).getRLPData();
        long txIndex = txIndexBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(txIndexBytes).longValue();
        long blockNumber = blockNumberBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(blockNumberBytes).longValue();
        return new GetTransactionIndexResponse(blockNumber, blockHash, txIndex);
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpBlockHash = RLP.encodeElement(this.blockHash);
        byte[] rlpBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.blockNumber));
        byte[] rlpTxIndex = RLP.encodeBigInteger((BigInteger.valueOf(this.txIndex)));

        return RLP.encodeList(rlpBlockNumber, rlpBlockHash, rlpTxIndex);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return TransactionIndex.class;
    }

    @Override
    public String toString() {
        return "Transaction Index: "+
                " txIndex " + txIndex +
                " blockNumber " + blockNumber +
                " blockHash: " + Arrays.toString(blockHash);
    }
}
