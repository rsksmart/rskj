package co.rsk.bahashmaps;

import co.rsk.datasources.DataSourceWithHeap;
import co.rsk.baheaps.AbstractByteArrayHeap;
import co.rsk.baheaps.AbstractByteArrayRefHeap;
import co.rsk.dbutils.ObjectIO;
import org.ethereum.db.ByteArrayWrapper;

import java.io.IOException;
import java.util.EnumSet;

public class MaxSizeByteArrayHashMap extends ByteArrayRefHashMap {

    int head =-1;
    int tail=-1;

    class Link {
        int prev;
        int next;
        byte[] metadata;

        public Link(int prev,int next,byte[] metadata) {
            this.prev = prev;
            this.next = next;
            this.metadata = metadata;
        }

        public Link(byte[] metadata) {
            loadFromMetadata(metadata);
        }

        public void loadFromMetadata(byte[] metadata) {
            this.metadata = metadata;
            prev = ObjectIO.getInt(metadata,0);
            next = ObjectIO.getInt(metadata,4);
        }

        public void store() {
            ObjectIO.putInt(metadata,0,prev);
            ObjectIO.putInt(metadata,4,next);
        }
    }

    public MaxSizeByteArrayHashMap(int initialCapacity, float loadFactor,
                                   BAKeyValueRelation BAKeyValueRelation,
                                   long newBeHeapCapacity,
                                   AbstractByteArrayHeap sharedBaHeap,
                                   int maxElements,
                                   Format format) throws IOException {
        super(initialCapacity,loadFactor,BAKeyValueRelation,newBeHeapCapacity,sharedBaHeap,maxElements,
                format         );
    }

    final int metadataSize =8;

    void itemStored(int markedHandle) {
        Link tailLink = null;
        if (tail!=-1) {
            byte[] tailMetadata = baHeap.retrieveMetadataByOfs(
                    getPureOffsetFromMarkedOffset(tail));
            tailLink = new Link(tailMetadata);
        }
        byte[] m = new byte[metadataSize];
        Link newHeadLink = new Link(tail,-1,m);
        newHeadLink.store();
        baHeap.setMetadataByOfs(getPureOffsetFromMarkedOffset(markedHandle),newHeadLink.metadata);

        if (tail!=-1) {
            tailLink.next = markedHandle;
            tailLink.store();
            baHeap.setMetadataByOfs(getPureOffsetFromMarkedOffset(tail), tailLink.metadata);
        } else {
            head = markedHandle;
            tail = markedHandle;
        }

    }

    void afterNodeInsertion(byte[] p, boolean evict) {
        if (!evict) return;
        if (size<maxElements) return;

        // Take one element from the head
        long headHandle = getPureOffsetFromMarkedOffset(head);
        byte[] headMetadata = baHeap.retrieveMetadataByOfs(headHandle);
        Link headLink = new Link(headMetadata);
        byte[] headData = baHeap.retrieveDataByOfs(headHandle);
        ByteArrayWrapper key;
        key = getWrappedKeyFromKPD(headData,head);

        if (head==tail) {
            tail =-1;
        }
        head = headLink.next;

        removeNode(hash(key),key);
    }

    void afterNodeAccess(int markedHandle, byte[] p) {
        // Unlink and relink at tail
        long handle = getPureOffsetFromMarkedOffset(markedHandle);
        byte[] metadata = baHeap.retrieveMetadataByOfs(handle);
        Link link = new Link(metadata);
        int prev = link.prev;
        int next = link.next;
        if (prev==-1)
            head = next;
        else
            setLink(prev,false,0,true,next);

        if (next==-1)
            tail = prev;
        else
            setLink(next,true,prev,false,0);

    }

    void setLink(int markedHandle,boolean setPrev,int prev,boolean setNext,int next) {
        long handle = getPureOffsetFromMarkedOffset(markedHandle);
        byte[] metadata = baHeap.retrieveMetadataByOfs(handle);
        Link link = new Link(metadata);
        if (setNext)
            link.next = next;
        if (setPrev)
            link.prev = prev;
        link.store();
        baHeap.setMetadataByOfs(handle,link.metadata);
    }
}
