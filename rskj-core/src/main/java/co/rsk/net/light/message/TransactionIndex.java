package co.rsk.net.light.message;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

public class TransactionIndex extends LightClientMessage{

    private final long id;
    private final byte[] txHash;

    public TransactionIndex(long id, byte[] txHash) {
        this.id = id;
        this.txHash = txHash;
    }

    public static TransactionIndex decode(byte[] encoded) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = paramsList.get(0).getRLPData();
        byte[] txHash = paramsList.get(1).getRLPData();
        long id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        return new TransactionIndex(id, txHash);
    }

    @Override
    public byte[] getEncoded() {
        return new byte[0];
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
