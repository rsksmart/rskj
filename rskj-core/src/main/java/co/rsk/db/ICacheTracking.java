package co.rsk.db;

import org.ethereum.db.ByteArrayWrapper;

public interface ICacheTracking {

    public interface Listener {
        void onReadKey(ICacheTracking cacheTracking, ByteArrayWrapper key, String threadGroupName);
        void onWriteKey(ICacheTracking cacheTracking, ByteArrayWrapper key, String threadGroupName);
        void onDeleteAccount(ICacheTracking cacheTracking, ByteArrayWrapper account, String threadGroupName);
    }

    ByteArrayWrapper getAccountFromKey(ByteArrayWrapper key);

    void subscribe(ICacheTracking.Listener listener);

    void unsubscribe(ICacheTracking.Listener listener);
}
