package co.rsk.net.messages;

public class SnapStatus {
    private final long trieSize;
    private final byte[] rootHash;

    public SnapStatus(long trieSize, byte[] rootHash) {
        this.trieSize = trieSize;
        this.rootHash = rootHash;
    }

    public long getTrieSize() {
        return this.trieSize;
    }

    public byte[] getRootHash() {
        return this.rootHash;
    }
}
