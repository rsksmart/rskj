package co.rsk.net.light.message;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;

public class GetTransactionIndex extends LightClientMessage{

    private final long id;
    private final byte[] txHash;

    public GetTransactionIndex(long id, byte[] txHash) {
        this.id = id;
        this.txHash = txHash;
    }

    public static GetTransactionIndex decode(byte[] encoded) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = paramsList.get(0).getRLPData();
        byte[] txHash = paramsList.get(1).getRLPData();
        long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        return new GetTransactionIndex(id, txHash);
    }

    public long getId() {
        return this.id;
    }

    public byte[] getTxHash() {
        return this.txHash;
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpHash = RLP.encodeElement(this.txHash);
        return RLP.encodeList(rlpId, rlpHash);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return "Transaction";
    }
}
