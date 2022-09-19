package co.rsk.freeheap;

import org.ethereum.db.ByteArrayWrapper;

public interface BAWrappedKeyValueRelation {
    long getHashcode(ByteArrayWrapper var1);

    ByteArrayWrapper computeWrappedKey(byte[] data);

    int getKeySize();
}
