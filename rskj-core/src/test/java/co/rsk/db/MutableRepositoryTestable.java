package co.rsk.db;

import co.rsk.trie.MutableTrie;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.OperationType;

import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;

public class MutableRepositoryTestable extends MutableRepository {
    private MutableRepositoryTestable(MutableTrie mutableTrie) {
        super(mutableTrie, null, true);
    }

    public static MutableRepositoryTestable trackedRepository(MutableTrie mutableTrie) {
        return new MutableRepositoryTestable(mutableTrie);
    }

    public void trackNode(byte[] key, OperationType operationType, boolean result, boolean isDelete) {
        super.trackNode(key, operationType, result, isDelete);
    }

    public void trackNodeWriteOperation(byte[] key, boolean isDelete) {
        super.trackNode(key, WRITE_OPERATION, true, isDelete);
    }

    public void trackNodeReadOperation(byte[] key, boolean result) {
        super.trackNode(key, READ_OPERATION, result, false);
    }
}
