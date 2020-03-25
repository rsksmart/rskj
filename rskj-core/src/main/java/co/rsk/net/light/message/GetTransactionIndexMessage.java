package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;

public class GetTransactionIndexMessage extends LightClientMessage {

    private final long id;
    private final byte[] txHash;

    public GetTransactionIndexMessage(long id, byte[] txHash) {
        this.id = id;
        this.txHash = txHash.clone();
        this.code = LightClientMessageCodes.GET_TRANSACTION_INDEX.asByte();
    }

    public GetTransactionIndexMessage(byte[] encoded) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = paramsList.get(0).getRLPData();
        txHash = paramsList.get(1).getRLPData();
        id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        this.code = LightClientMessageCodes.GET_TRANSACTION_INDEX.asByte();
    }

    public long getId() {
        return this.id;
    }

    public byte[] getTxHash() {
        return this.txHash.clone();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpHash = RLP.encodeElement(this.txHash);
        return RLP.encodeList(rlpId, rlpHash);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return GetTransactionIndexMessage.class;
    }


    @Override
    public String toString() {
        return "";
    }
}
