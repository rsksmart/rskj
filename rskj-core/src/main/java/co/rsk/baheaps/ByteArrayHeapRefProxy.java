package co.rsk.baheaps;

import java.io.IOException;
import java.util.List;

// This class wraps a RefHeap with the interface of a normal heap, to be
// able to use a ref heap as a normal heap.
public class ByteArrayHeapRefProxy implements AbstractByteArrayHeap {
    AbstractByteArrayRefHeap refHeap;

    public ByteArrayHeapRefProxy(AbstractByteArrayRefHeap refHeap) {
        this.refHeap = refHeap;
    }

    @Override
    public List<String> getStats() {
        return refHeap.getStats();
    }

    @Override
    public long addObjectReturnOfs(byte[] encoded, byte[] metadata) {
        return refHeap.addAndReturnHandle(encoded,metadata);
    }

    public int getInt(long encodedOfs) {
        if ((encodedOfs<Integer.MIN_VALUE) || (encodedOfs>Integer.MAX_VALUE))
            throw new RuntimeException("Invalid offset to be converted to handle");
        return (int) encodedOfs;
    }

    @Override
    public byte[] retrieveDataByOfs(long encodedOfs) {
        return refHeap.retrieveDataByHandle(getInt(encodedOfs));
    }

    @Override
    public byte[] retrieveMetadataByOfs(long encodedOfs) {
        return refHeap.retrieveMetadataByHandle(getInt(encodedOfs));
    }

    @Override
    public void setMetadataByOfs(long encodedOfs, byte[] metadata) {
        refHeap.setMetadataByHandle(getInt(encodedOfs),metadata);
    }

    @Override
    public void checkObjectByOfs(long encodedOfs) {
        refHeap.checkHandle(getInt(encodedOfs));
    }

    @Override
    public void removeObjectByOfs(long encodedOfs) {
        refHeap.removeObjectByHandle(getInt(encodedOfs));
    }

    @Override
    public boolean isRemapping() {
        return refHeap.isRemapping();
    }

    @Override
    public void beginRemap() {
        refHeap.beginRemap();
    }

    @Override
    public void endRemap() {
        refHeap.endRemap();
    }

    @Override
    public void remapByOfs(long encodedOfs) {
        refHeap.remapByHandle(getInt(encodedOfs));
    }

    @Override
    public int getUsagePercent() {
        return refHeap.getUsagePercent();
    }

    @Override
    public long load() throws IOException {
        return 0;
    }

    @Override
    public void save(long rootOfs) throws IOException {

    }
}
