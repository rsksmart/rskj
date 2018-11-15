package co.rsk.trie;

/**
 * Created by SerAdmin on 11/12/2018.
 */
public class TrieAccountScanner {
    final public int ERR_UNEXPECTED_CHILDREN =1;
    final public int ERR_UNEXPECTED_MIDNODE = 2;


    public TrieAccountScanner() {
    }

    public void init() {
    }

    public int scanTrieNodes(ExpandedKey key, TrieImpl src,NodeIteratorListener nil) {
        if (src==null)
            return 0;

        // shared Path
        byte[] encodedSharedPath =src.getEncodedSharedPath();
        int sharedPathLength = src.getSharedPathLength();
        if (encodedSharedPath != null) {
            byte[] sharedPath = PathEncoder.decode(encodedSharedPath, sharedPathLength);
            key = key.append(new ExpandedKeyImpl(sharedPath));
        }
        TrieImpl child0 = (TrieImpl) src.retrieveNode(0);
        TrieImpl child1 = (TrieImpl) src.retrieveNode(1);

        int ret = 0;
        if (nil!=null) {
            ret = nil.processNode(key, src);
            if (ret != 0) return (ret);
        }

        if (child0!=null)
            ret = scanTrieNodes(key.append( (byte)0),child0,nil);

        if (ret!=0) return ret;

        if (child1!=null)
            ret = scanTrieNodes(key.append( (byte)1),child1,nil);

        return ret;
    }

    public int scanTrieKeys(ExpandedKey key, TrieImpl src, TrieIteratorListener ite, int depth) {
        if (src==null)
            return 0;

        // shared Path
        byte[] encodedSharedPath =src.getEncodedSharedPath();
        int sharedPathLength = src.getSharedPathLength();

        if (encodedSharedPath != null) {
            //if (this.sharedPathLength+ key.length() > collectKeyLen)
            //    return;

            byte[] sharedPath = PathEncoder.decode(encodedSharedPath, sharedPathLength);
            key = key.append(new ExpandedKeyImpl(sharedPath));
        }
        TrieImpl child0 = (TrieImpl) src.retrieveNode(0);

        TrieImpl child1 = (TrieImpl) src.retrieveNode(1);

        // if value is lazy (it's not on 0.5.2 .5.3) then always retrieve the value,
        // to make sure it exists on the trie. Only in unitrie it's lazy.

        byte[] value = src.getValue();

        if (depth==-1) { // process all
            int ret = ite.processKey(key.getData(),value);
            if (ret!=0) return (ret);
        }
        else
        if (key.length()==depth) {
            // We've reached the Account level. From now on everything will be different.
            if ((child0!=null) || (child1!=null))
                return ERR_UNEXPECTED_CHILDREN;
            if (ite!=null)
                return ite.processKey(key.getData(),value);
            return 0;
        } else
            if (src.getValueLength()!=0) {
                return ERR_UNEXPECTED_MIDNODE;
            }

        int ret = 0;

        if (child0!=null)
            ret = scanTrieKeys(key.append( (byte)0),child0,ite,depth);


        if (ret!=0) return ret;

        if (child1!=null)
            ret = scanTrieKeys(key.append( (byte)1),child1,ite,depth);

        return ret;
    }

}

