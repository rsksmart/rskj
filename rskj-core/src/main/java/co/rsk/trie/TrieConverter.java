package co.rsk.trie;

import co.rsk.crypto.Keccak256;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;

import java.util.Arrays;

/**
 * Created by SerAdmin on 10/23/2018.
 */
public class TrieConverter {
    //HashMapDB store;
    TrieStoreImpl store;

    // This method converts and new trie into an old trie hash, but it works for tx tries
    // or receipt tries. It dosn't work for the account trie because it doesn't translate
    // new AccountStates into old AccountStates. For that, use computeOldAccountTrieRoot()
    public static byte[] computeOldTrieRoot(TrieImpl src,boolean removeFirstNodePrefix) {
        TrieConverter tc = new TrieConverter();
        tc.init();
        return tc.getOldTrieRoot(src,removeFirstNodePrefix);
    }

    public static byte[] computeOldAccountTrieRoot(TrieImpl src) {
        TrieConverter tc = new TrieConverter();
        tc.init();
        return tc.getOldAccountTrieRoot(new byte[]{}, src, true);
    }


    public void init() {
        store = new TrieStoreImpl(new HashMapDB());
    }

    public byte[] getOldTrieRoot(TrieImpl src,boolean removeFirstNodePrefix) {
        if (src==null)
            return HashUtil.EMPTY_TRIE_HASH;

        TrieImpl child0 = (TrieImpl) src.retrieveNode(0);
        byte[] child0Hash =null;;
        if (child0!=null)
            child0Hash = getOldTrieRoot(child0,false);

        TrieImpl child1 = (TrieImpl) src.retrieveNode(1);
        byte[] child1Hash =null;
        if (child1!=null)
            child1Hash = getOldTrieRoot(child1,false);


        // shared Path
        byte[] encodedSharedPath =src.getEncodedSharedPath();
        int sharedPathLength = src.getSharedPathLength();

        Keccak256[] hashes = new Keccak256[2];
        hashes[0] = child0Hash==null?null:new Keccak256(child0Hash);
        hashes[1] = child1Hash==null?null:new Keccak256(child1Hash);

        byte[] value =src.getValue();
        int valueLength = src.valueLength;
        byte[] valueHash = src.getValueHash();

        if (removeFirstNodePrefix) {
            encodedSharedPath =null;
            sharedPathLength =0;
            value = null; // also remove value
            valueLength = 0;
            valueHash = null;
        }

        TrieImpl newNode = new TrieImpl(
                encodedSharedPath, sharedPathLength,
                value, null, hashes,store,
                valueLength,valueHash).withSecure(src.isSecure());

        return newNode.getHash().getBytes();
    }

    private byte[] getOldAccountTrieRoot(byte[] key, TrieImpl src, boolean removeFirst8bits) {
        if (src==null)
            return HashUtil.EMPTY_TRIE_HASH;

        // shared Path
        byte[] encodedSharedPath =src.getEncodedSharedPath();
        int sharedPathLength = src.getSharedPathLength();
        if (encodedSharedPath != null) {
            //if (this.sharedPathLength+ key.length() > collectKeyLen)
            //    return;

            byte[] sharedPath = PathEncoder.decode(encodedSharedPath, sharedPathLength);
            key = concat(key, sharedPath);
        }
        if (removeFirst8bits) {
            assert (sharedPathLength >=8);
            sharedPathLength -=8;
            encodedSharedPath = Arrays.copyOfRange(encodedSharedPath,1,encodedSharedPath.length);
        }
        TrieImpl child0 = (TrieImpl) src.retrieveNode(0);
        byte[] child0Hash =null;
        TrieImpl child1 = (TrieImpl) src.retrieveNode(1);
        byte[] child1Hash =null;

        if (key.length == 8 * 32 + 8) {
            // We've reached the Account level. From now on everything will be different.
            AccountState astate = new AccountState(src.getValue());
            OldAccountState oldState = new OldAccountState(astate.getNonce(),astate.getBalance());
            // child1 (0x80) will be the code
            if (child1!=null) {
                oldState.setCodeHash(child1.getValueHash());
            }
            // the child0 side will be the storage. The first child corresponds to the
            // 8-bit zero prefix. 1 bit is consumed by the branch. 7-bits left. We check that
            if (child0!=null) {
                assert (child0.getSharedPathLength()==7);
                // We'll create an ad-hoc trie for storage cells, the first
                // child0's child is the root of this try. What if it has two children?
                // This can happen if there are two hashed storage keys, one begining with
                // 0 and another with 1.
                byte[] stateRoot = computeOldTrieRoot(child0,true);
                oldState.setStateRoot(stateRoot);
            }

            byte[] avalue =oldState.getEncoded();
            TrieImpl newNode = new TrieImpl(
                    encodedSharedPath, sharedPathLength,
                    avalue, null, null,store,
                    avalue.length,null).withSecure(src.isSecure());///src.isSecure()

            return newNode.getHash().getBytes();
        }

        if (child0 != null) {
            child0Hash = getOldAccountTrieRoot(concat(key, (byte) 0), child0, false);
        }

        if (child1 != null) {
            child1Hash = getOldAccountTrieRoot(concat(key, (byte) 1), child1, false);
        }

        Keccak256[] hashes = new Keccak256[2];
        hashes[0] = child0Hash==null?null:new Keccak256(child0Hash);
        hashes[1] = child1Hash==null?null:new Keccak256(child1Hash);

        TrieImpl newNode = new TrieImpl(
                encodedSharedPath, sharedPathLength,
                src.getValue(), null, hashes,store,src.valueLength,
                src.getValueHash()).withSecure(src.isSecure());

        return newNode.getHash().getBytes();
    }

    private static byte[] concat(byte[] first, byte b) {
        return concat(first, new byte[]{b});
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
