package co.rsk.trie.serializer;

import java.nio.ByteBuffer;

import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;
import static co.rsk.trie.Trie.RSKIP107_TRIE_VERSION;

public class RSKIP107Serializer implements TrieSerializer {
    @Override
    public long deserializeLastRentPaidTimestamp(ByteBuffer message) {
        return NO_RENT_TIMESTAMP;
    }

    @Override
    public void serializeLastRentPaidTimestamp(ByteBuffer buffer, long lastRentPaidTimestamp) {
        // no need to serialize
    }

    @Override
    public byte trieVersion() {
        return RSKIP107_TRIE_VERSION;
    }

    @Override
    public int lastPaidRentTimestampSize() {
        return 0; // since there are no timestamps on rskip107
    }
}
