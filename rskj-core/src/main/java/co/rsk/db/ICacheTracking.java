package co.rsk.db;

import org.ethereum.db.ByteArrayWrapper;

public interface ICacheTracking {

    public interface Listener {
        void onReadKey(ICacheTracking cacheTracking, ByteArrayWrapper key, int partitionId);
        void onWriteKey(ICacheTracking cacheTracking, ByteArrayWrapper key, int partitionId);
        void onDeleteAccount(ICacheTracking cacheTracking, ByteArrayWrapper account, int partitionId);
    }

    ByteArrayWrapper getAccountFromKey(ByteArrayWrapper key);

    void subscribe(ICacheTracking.Listener listener);

    void unsubscribe(ICacheTracking.Listener listener);
}
