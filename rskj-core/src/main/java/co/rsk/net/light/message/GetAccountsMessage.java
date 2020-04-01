package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;

public class GetAccountsMessage extends LightClientMessage {

    private final long id;
    private final byte[] blockHash;
    private final byte[] addressHash;

    public GetAccountsMessage(long id, byte[] blockHash, byte[] addressHash) {
        this.id = id;
        this.blockHash = blockHash.clone();
        this.addressHash = addressHash.clone();

        code = LightClientMessageCodes.GET_ACCOUNTS.asByte();
    }

    public GetAccountsMessage(byte[] encoded) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);

        byte[] rlpId = paramsList.get(0).getRLPData();
        id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();

        blockHash = paramsList.get(1).getRLPData();
        addressHash = paramsList.get(2).getRLPData();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(id));
        byte[] rlpBlockHash = RLP.encodeElement(blockHash);
        byte[] rlpAddressHash = RLP.encodeElement(addressHash);

        return RLP.encodeList(rlpId, rlpBlockHash, rlpAddressHash);
    }

    public long getId() {
        return id;
    }

    public byte[] getBlockHash() {
        return blockHash.clone();
    }

    public byte[] getAddressHash() {
        return addressHash.clone();
    }

    @Override
    public Class<?> getAnswerMessage() {
        return AccountsMessage.class;
    }

    @Override
    public String toString() {
        return "";
    }
}
