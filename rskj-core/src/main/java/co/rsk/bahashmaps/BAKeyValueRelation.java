package co.rsk.bahashmaps;


public interface BAKeyValueRelation {
    int getHashcode(byte[] var1);

    byte[] computeKey(byte[] data);

    int getKeySize();
}
