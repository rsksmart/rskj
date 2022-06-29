package co.rsk.datasources;

import co.rsk.bahashmaps.BAKeyValueRelation;
import co.rsk.bahashmaps.BAWrappedKeyValueRelation;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;

public class MyBAKeyValueRelation implements BAKeyValueRelation, BAWrappedKeyValueRelation {
    public int intFromBytes(byte b1, byte b2, byte b3, byte b4) {
        return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
    }

    public int hashCodeFromHashDigest(byte[] bytes) {
        // Use the last 4 bytes, not the first 4 which are often zeros in Bitcoin.
        return intFromBytes(bytes[28], bytes[29], bytes[30], bytes[31]);
    }

    @Override
    public int getHashcode(ByteArrayWrapper key) {
        return hashCodeFromHashDigest(key.getData());
    }

    @Override
    public int getHashcode(byte[] key) {
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
