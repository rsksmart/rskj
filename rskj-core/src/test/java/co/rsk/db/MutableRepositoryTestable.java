package co.rsk.db;

import co.rsk.trie.MutableTrie;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.MutableRepositoryTracked;
import org.ethereum.db.OperationType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static org.ethereum.db.OperationType.*;

public class MutableRepositoryTestable extends MutableRepositoryTracked {
    public MutableRepositoryTestable(MutableTrie mutableTrie,
                                     MutableRepositoryTracked parentRepository){
        super(mutableTrie, parentRepository, new HashMap<>(), new HashMap<>());
    }

    public static MutableRepositoryTestable trackedRepository(MutableTrie mutableTrie) {
        return new MutableRepositoryTestable(mutableTrie, null);
    }

    public void trackNodeWriteOperation(byte[] key) {
        super.trackNode(key, WRITE_OPERATION, true);
    }

    public void trackNodeReadOperation(byte[] key, boolean result) {
        super.trackNode(key, READ_OPERATION, result);
    }

    public void trackNodeReadContractOperation(byte[] key, boolean nodeExistsInTrie) {
        super.trackNode(key, READ_CONTRACT_CODE_OPERATION, nodeExistsInTrie);
    }
}
