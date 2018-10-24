package co.rsk.trie;

import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;

/**
 * Created by SerAdmin on 10/23/2018.
 */
public class TrieConverter {
    HashMapDB store;

    public void init() {
        store = new HashMapDB();
    }

    public byte[] getOldTrieRoot(TrieImpl src) {
        if (src==null)
            return HashUtil.EMPTY_TRIE_HASH;

        TrieImpl child0 = (TrieImpl) src.retrieveNode(0);
        byte[] child0Hash =null;;
        if (child0!=null)
            child0Hash = getOldTrieRoot(child0);

        TrieImpl child1 = (TrieImpl) src.retrieveNode(1);
        byte[] child1Hash =null;
        if (child1!=null)
            child1Hash = getOldTrieRoot(child1);


        // shared Path
        byte[] encodedSharedPath =src.getEncodedSharedPath();
        int sharedPathLength = src.getSharedPathLength();

        Keccak256[] hashes = new Keccak256[2];
        hashes[0] = child0Hash==null?null:new Keccak256(child0Hash);
        hashes[1] = child1Hash==null?null:new Keccak256(child1Hash);

        OldTrieImpl newNode = new OldTrieImpl(
                encodedSharedPath, sharedPathLength,
                src.getValue(), null, hashes,store,src.isSecure());

        return newNode.getHash().getBytes();
    }
}
