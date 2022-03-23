package co.rsk.trie.serializer;

import java.nio.ByteBuffer;

public interface TrieSerializer {
    long deserializeLastRentPaidTimestamp(ByteBuffer message);
    void serializeLastRentPaidTimestamp(ByteBuffer buffer, long lastRentPaidTimestamp);
    byte trieVersion();
    int lastPaidRentTimestampSize();
}
