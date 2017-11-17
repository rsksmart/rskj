package co.rsk.net.sync;

public class ChunkDescriptor {
    private final byte[] hash;
    private final int count;

    public ChunkDescriptor(byte[] hash, int count) {
        this.hash = hash;
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public byte[] getHash() {
        return hash;
    }
}
