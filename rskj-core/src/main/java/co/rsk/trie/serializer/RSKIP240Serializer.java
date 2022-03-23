package co.rsk.trie.serializer;

import java.nio.ByteBuffer;

import static co.rsk.trie.Trie.RSKIP240_TRIE_VERSION;
import static co.rsk.trie.Trie.TIMESTAMP_SIZE;

public class RSKIP240Serializer implements TrieSerializer {
    @Override
    public long deserializeLastRentPaidTimestamp(ByteBuffer message) {
        return message.getLong();
    }

    @Override
    public void serializeLastRentPaidTimestamp(ByteBuffer buffer, long lastRentPaidTimestamp) {
        buffer.putLong(lastRentPaidTimestamp);
    }

    @Override
    public byte trieVersion() {
        return (byte) RSKIP240_TRIE_VERSION;
    }

    @Override
    public int lastPaidRentTimestampSize() {
        return TIMESTAMP_SIZE;
    }
}
