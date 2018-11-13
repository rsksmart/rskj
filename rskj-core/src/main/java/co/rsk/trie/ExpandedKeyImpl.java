
package co.rsk.trie;

import java.util.Arrays;

/**
 * Created by SerAdmin on 9/24/2018.
 */
public class ExpandedKeyImpl implements ExpandedKey {
    byte[] expandedKey;

    public ExpandedKeyImpl(byte[] aExpandedKey) {
        expandedKey = aExpandedKey;
    }

    public ExpandedKeyImpl() {
        expandedKey = new byte[] {};
    }

    public int length() {
        return expandedKey.length;
    }

    public byte get(int i) {
        return expandedKey[i];
    }

    public void copy(int srcPos, Object dest, int destPos, int length) {
        System.arraycopy(expandedKey,srcPos,dest,destPos,length);
    }

    public byte[] getData() {
        return expandedKey;
    }

    public static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
    public ExpandedKey append(byte k) {
        return new ExpandedKeyImpl(concat(expandedKey,new byte[]{k}));
    }
    public ExpandedKey append(ExpandedKey otherKey) {
        return new ExpandedKeyImpl(concat(expandedKey,otherKey.getData()));
    }

}
