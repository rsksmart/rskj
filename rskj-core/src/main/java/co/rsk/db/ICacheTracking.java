package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import org.ethereum.core.Transaction;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.DataWord;

import java.util.Collection;

public interface ICacheTracking {

    public interface Listener {
        void onReadKey(ByteArrayWrapper key, String threadGroupName);
        void onWriteKey(ByteArrayWrapper key, String threadGroupName);
        void onDeleteKey(ByteArrayWrapper key, String threadGroupName);
    }

    void subscribe(ICacheTracking.Listener listener);

    void unsubscribe(ICacheTracking.Listener listener);
}
