package co.rsk.net.light.message;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;

public class GetTransactionIndex extends LightClientMessage{

    private final long id;
    private final long blockNumber;
    private final byte[] blockHash;
    private final long txIndex;

    public GetTransactionIndex(long id, long blockNumber, byte[] blockHash, long txIndex) {
        this.id = id;
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
        this.txIndex = txIndex;
    }

    public static GetTransactionIndex decode(long id, byte[] encoded) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] txIndexBytes = paramsList.get(2).getRLPData();
        byte[] blockNumberBytes = paramsList.get(3).getRLPData();
        byte[] blockHash = paramsList.get(1).getRLPData();
        long txIndex = txIndexBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(txIndexBytes).longValue();
        long blockNumber = blockNumberBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(blockNumberBytes).longValue();
        return new GetTransactionIndex(id, blockNumber, blockHash, txIndex);
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
        return null;
    }

    @Override
    public String toString() {
        return null;
    }
}
