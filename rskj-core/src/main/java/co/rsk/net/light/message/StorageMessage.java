package co.rsk.net.light.message;

import org.bouncycastle.util.BigIntegers;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static co.rsk.net.light.LightClientMessageCodes.STORAGE;

public class StorageMessage extends LightClientMessage{

    private final long id;
    private final byte[] merkleInclusionProof;
    private final byte[] storageValue;

    public StorageMessage(long id, byte[] merkleInclusionProof, byte[] storageValue) {
        this.id = id;
        this.merkleInclusionProof = merkleInclusionProof.clone();
        this.storageValue = storageValue.clone();

        this.code = STORAGE.asByte();
    }

    public StorageMessage(byte[] encoded) {
        RLPList list = (RLPList) RLP.decode2(encoded).get(0);
        byte[] rlpId = list.get(0).getRLPData();
        this.id = rlpId == null ? 0 : BigIntegers.fromUnsignedByteArray(rlpId).longValue();
        this.merkleInclusionProof = list.get(1).getRLPData();
        this.storageValue = list.get(2).getRLPData();

        this.code = STORAGE.asByte();
    }


    public long getId() {
        return id;
    }

    public byte[] getMerkleInclusionProof() {
        return merkleInclusionProof.clone();
    }

    public byte[] getStorageValue() {
        return storageValue.clone();
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpId = RLP.encodeBigInteger(BigInteger.valueOf(id));
        byte[] rlpMerkleInclusionProof = RLP.encodeElement(merkleInclusionProof);
        byte[] rlpStorageValue = RLP.encodeElement(storageValue);
        return RLP.encodeList(rlpId, rlpMerkleInclusionProof, rlpStorageValue);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return "";
    }
}
