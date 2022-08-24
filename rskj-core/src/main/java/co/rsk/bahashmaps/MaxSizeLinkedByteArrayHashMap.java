package co.rsk.bahashmaps;

import co.rsk.baheaps.AbstractByteArrayHeap;
import co.rsk.baheaps.AbstractByteArrayRefHeap;
import co.rsk.baheaps.LinkedByteArrayRefHeap;
import org.ethereum.db.ByteArrayWrapper;
import java.util.BitSet;

public class MaxSizeLinkedByteArrayHashMap extends ByteArrayRefHashMap {

    String debugKey;

    LinkedByteArrayRefHeap lba; // real type LinkedByteArrayRefHeap

    // The isNull bitset is necessary because we need to know if a heap entry
    // corresponds to a key or to a value without knowing the index of the entry
    // on table[].
    // This means that if this class were to be integrated into the ByteArrayHashMap,
    // the marking of the handle would not be necessary.
    BitSet isNull;
    boolean topPriorityOnAccess;

    public MaxSizeLinkedByteArrayHashMap(int initialCapacity, float loadFactor,
                                         BAKeyValueRelation BAKeyValueRelation,
                                   long newBeHeapCapacity,
                                         AbstractByteArrayHeap sharedBaHeap,
                                         LinkedByteArrayRefHeap lba,
                                   int maxElements,boolean topPriorityOnAccess,
                                         Format format) {
        super(initialCapacity,loadFactor,BAKeyValueRelation,newBeHeapCapacity,
                sharedBaHeap,maxElements,
                format,null);
        this.lba = lba;
        isNull = new BitSet(maxElements);
        this.topPriorityOnAccess = topPriorityOnAccess;
    }

    public void setTopPriorityOnAccess(boolean v) {
        topPriorityOnAccess = v;
    }

    void inRange(long handle) {
        // Make sure the handle has int-size
        if ((handle > Integer.MAX_VALUE) || (handle < Integer.MIN_VALUE))
            throw new RuntimeException("bad range");
    }

    void afterNodeInsertion(int markedHandle,byte[] key, byte[] data, boolean evict) {
        long handle = getPureOffsetFromMarkedOffset(markedHandle);

        inRange(handle);


        // It is automatically added to the tail.
        if (isNullMarkedOffset(markedHandle))
            isNull.set( (int) handle);
        else
            isNull.clear( (int) handle);


        if (!evict) return;
        if (size < maxElements) return;
        evictOldest();
    }

    void evictOldest() {
        int oldest = lba.getOldest();
        byte[] pdata = baHeap.retrieveDataByOfs(oldest);

        ByteArrayWrapper wkey;

        // Now there is a problem: I don't know if it is only a key/null or is a key/data
        if (!isNull.get((int)oldest)) {
            wkey = computeWrappedKey(pdata);
        } else {
            wkey = new ByteArrayWrapper(pdata);
        }
        if ((debugKey!=null) && (wkey.toString().equals(debugKey))) {
            wkey = wkey;
        }
        if (removeNode(hash(wkey),wkey)==-1) {
            removeNode(hash(wkey),wkey);
            throw new RuntimeException("could not remove item");
        }
    }

    void afterNodeAccess(int markedHandle, byte[] p) {
        if (topPriorityOnAccess) {
            // Unlink and relink at tail
            long u = getPureOffsetFromMarkedOffset(markedHandle);
            inRange(u);
            lba.setAsNew((int) u);
        }
    }

}
