package co.rsk.baheaps;


import java.util.List;

public class LinkedByteArrayRefHeap implements AbstractByteArrayRefHeap {

    byte[][] data;
    byte[] metadata;
    int[] prev;
    int[] next;

    // pointers
    int head;
    int tail;
    int unused;

    int unusedHandlesCount;
    int highestHandle;
    int maxReferences;
    int referenceCount;
    int metadataSize;

    public LinkedByteArrayRefHeap(int maxReferences,int metadataSize) {
        this.maxReferences = maxReferences;
        this.metadataSize = metadataSize;
        reset();
    }

    public int getOldest() {
        return head;
    }

    public void setAsNew(int handle) {
        unlink(handle);
        addToTail(handle);
    }

    protected void addToTail(int handle) {
        next[handle] = -1;
        if (tail!=-1) {
            next[tail] = handle;
            prev[handle] = tail;
        } else {
            if (head!=-1)
                throw new RuntimeException("bad state");
            head = handle;
            prev[handle] = -1;
        }
        tail = handle;
        next[handle] = -1;
    }

    public void reset() {
        head = -1;
        tail = -1;


        if (maxReferences > Integer.MAX_VALUE)
            maxReferences = Integer.MAX_VALUE;

        data = new byte[maxReferences][];
        metadata = null;
        if (metadataSize!=0)
           metadata = new byte[maxReferences*metadataSize];

        prev = new int[maxReferences];
        next = new int[maxReferences];

        for (int i = 0; i < maxReferences; i++) {
            prev[i]  = i-1;
            next[i] = (i + 1);
        }
        next[maxReferences-1] = -1;
        unused = 0;
        referenceCount = 0;
        unusedHandlesCount = maxReferences;
    }

    protected int getNewHandle(byte[] d) {
        int newHandle = unused;
        if (unused==-1) {
            unused = unused;
            throw new RuntimeException("no more handles");
        }
        int n = next[unused];
        if (n==-1)
            unused = unused;


        prev[unused] = -1; // make sure it is unlinked
        next[unused] = -1;

        unused = n;

        if (tail!=-1) {
            next[tail] = newHandle;
            prev[newHandle] = tail;
        } else {
            if (head!=-1)
                throw new RuntimeException("bad state");
            head = newHandle;
        }

        tail = newHandle;
        next[newHandle] = -1;

        unusedHandlesCount--;
        data[newHandle] = d;
        if (newHandle > highestHandle)
            highestHandle = newHandle;
        referenceCount++;
        return newHandle;
    }

    public void removeObjectByHandle(int handle) {
        removeHandle(handle);
    }

    int debugHandle = 954396;
    protected void removeHandle(int i) {
        if (i==debugHandle)
            i = i;
        data[i] = null;
        unlink(i);
        addToUnused(i);
        unusedHandlesCount++;
    }

    protected void addToUnused(int i) {
        if (unused!=-1) {
            prev[unused] = i;
        }
        next[i] = unused;
        prev[i] = -1;
        unused = i;
    }

    protected void unlink(int i) {
        if (prev[i]!=-1) {
            next[prev[i]] = next[i];
        } else
            head = next[i];

        if (next[i]!=-1) {
            prev[next[i]] = prev[i];
        } else
            tail = prev[i];
    }

    @Override
    public boolean heapIsAlmostFull() {
        return false;
    }

    @Override
    public boolean isRemapping() {
        return false;
    }

    @Override
    public void beginRemap() {

    }

    @Override
    public void endRemap() {

    }

    @Override
    public int getUsagePercent() {
        return 0;
    }

    @Override
    public byte[] retrieveDataByHandle(int handle) {
        return data[handle];
    }

    @Override
    public void setMetadataByHandle(int handle, byte[] ametadata) {
        if (ametadata==null)
            return;
        System.arraycopy(ametadata,0,metadata,handle*metadataSize,metadataSize);
    }

    @Override
    public byte[] retrieveMetadataByHandle(int handle) {
        byte[] r = new byte[metadataSize];
        System.arraycopy(metadata,handle*metadataSize,r,0,metadataSize);
        return r;
    }

    @Override
    public int addAndReturnHandle(byte[] encoded, byte[] metadata) {
        return getNewHandle(encoded);
    }

    @Override
    public void checkHandle(int handle) {
        if (data[handle]==null) {
            throw new RuntimeException("invalid handle");
        }
    }

    @Override
    public void remapByHandle(int handle) {

    }

    @Override
    public List<String> getStats() {
        return null;
    }
}
