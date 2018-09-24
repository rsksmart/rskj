package co.rsk.trie;

/**
 * Created by SerAdmin on 9/24/2018.
 */
public interface ExpandedKey {
    int length();
    byte get(int i);
    void copy(int  srcPos,Object dest, int destPos,int length);
    byte[] getData();
    ExpandedKey append(ExpandedKey otherKey);
    ExpandedKey append(byte k);
}
