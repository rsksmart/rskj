package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.bouncycastle.util.BigIntegers;

import java.math.BigInteger;

public class TransactionIndexMessage extends LightClientMessage {

    private final long id;
    private final long blockNumber;
    private final byte[] blockHash;
    private final long txIndex;

    public TransactionIndexMessage(long id, long blockNumber, byte[] blockHash, long txIndex) {
        this.id = id;
        this.blockNumber = blockNumber;
        this.blockHash = blockHash.clone();
        this.txIndex = txIndex;
        this.code = LightClientMessageCodes.TRANSACTION_INDEX.asByte();
    }

    public TransactionIndexMessage(byte[] encoded) {
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);

        byte[] rlpId = list.get(0).getRLPData();
        byte[] blockNumberBytes = list.get(1).getRLPData();
        blockHash = list.get(2).getRLPData();
        byte[] txIndexBytes = list.get(3).getRLPData();

        id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        txIndex = txIndexBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(txIndexBytes).longValue();
        blockNumber = blockNumberBytes == null ? 0 : BigIntegers.fromUnsignedByteArray(blockNumberBytes).longValue();

        this.code = LightClientMessageCodes.TRANSACTION_INDEX.asByte();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(getId()));
        byte[] rlpBlockNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.blockNumber));
        byte[] rlpBlockHash = RLP.encodeElement(this.blockHash);
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
        return blockHash.clone();
    }

    public long getTransactionIndex() {
        return txIndex;
    }

    @Override
    public String toString() {
        return "";
    }
}
