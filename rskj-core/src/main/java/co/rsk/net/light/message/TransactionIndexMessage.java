package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;

public class TransactionIndexMessage extends LightClientMessage {

    private final long id;
    private final long blockNumber;
    private final byte[] blockHash;
    private final long txIndex;

    public TransactionIndexMessage(long id, long blockNumber, byte[] blockHash, long txIndex) {
        this.id = id;
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
        this.txIndex = txIndex;
        this.code = LightClientMessageCodes.TRANSACTION_INDEX.asByte();
    }

    public TransactionIndexMessage(byte[] encoded) {
        RLPList paramsList = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = paramsList.get(0).getRLPData();

        byte[] txIndexBytes = paramsList.get(2).getRLPData();
        byte[] blockNumberBytes = paramsList.get(3).getRLPData();

        id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        blockHash = paramsList.get(1).getRLPData();
        txIndex = txIndexBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(txIndexBytes).longValue();
        blockNumber = blockNumberBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(blockNumberBytes).longValue();

        this.code = LightClientMessageCodes.TRANSACTION_INDEX.asByte();
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
        return null;
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

    public long getTransactionIndex() {
        return txIndex;
    }

    @Override
    public String toString() {
        return "";
    }
}
