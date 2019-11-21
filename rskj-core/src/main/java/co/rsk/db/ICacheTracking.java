package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import org.ethereum.core.Transaction;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.DataWord;

import java.util.Collection;

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
