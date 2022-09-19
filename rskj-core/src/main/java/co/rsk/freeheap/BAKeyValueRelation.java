package co.rsk.freeheap;


public interface BAKeyValueRelation {
    long getHashcode(byte[] var1);

    byte[] computeKey(byte[] data);

    int getKeySize();
}
