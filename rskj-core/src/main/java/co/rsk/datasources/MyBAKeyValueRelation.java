package co.rsk.datasources;

import co.rsk.freeheap.BAKeyValueRelation;
import co.rsk.freeheap.BAWrappedKeyValueRelation;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;

public class MyBAKeyValueRelation implements BAKeyValueRelation, BAWrappedKeyValueRelation {

    public long longFromBytes(byte b1, byte b2, byte b3, byte b4,
                               byte b5, byte b6, byte b7, byte b8) {
        return  ((b8 & 0xFF) << 56) | ((b7 & 0xFF) << 48) |
                ((b6 & 0xFF) << 40) | ((b5 & 0xFF) << 32) |
                ((b4 & 0xFF) << 24) | ((b3 & 0xFF) << 16)  |
                ((b2 & 0xFF) << 8)  | ((b1 & 0xFF));
    }

    public long hashCodeFromHashDigest(byte[] bytes) {
        // Use the last 4 bytes, not the first 4 which are often zeros in Bitcoin.
        return longFromBytes(bytes[24], bytes[25], bytes[26], bytes[27],
                bytes[28], bytes[29], bytes[30], bytes[31]);
    }

    @Override
    public long getHashcode(ByteArrayWrapper key) {
        return hashCodeFromHashDigest(key.getData());
    }

    @Override
    public long getHashcode(byte[] key) {
        return hashCodeFromHashDigest(key);
    }

    @Override
    public byte[] computeKey(byte[] data) {
        return Keccak256Helper.keccak256(data);
    }

    @Override
    public int getKeySize() {
        return 32;
    }

    @Override
    public ByteArrayWrapper computeWrappedKey(byte[] data) {
        return new ByteArrayWrapper(Keccak256Helper.keccak256(data));
    }

}
