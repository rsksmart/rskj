package co.rsk.peg;

import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import java.math.BigInteger;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Repository;
import org.ethereum.db.MutableRepository;

public final class BridgeSupportTestUtil {
    public static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    public static void mockChainOfStoredBlocks(BtcBlockStoreWithCache btcBlockStore, BtcBlock targetHeader, int headHeight, int targetHeight) throws BlockStoreException {
        // Simulate that the block is in there by mocking the getter by height,
        // and then simulate that the txs have enough confirmations by setting a high head.
        when(btcBlockStore.getStoredBlockAtMainChainHeight(targetHeight)).thenReturn(new StoredBlock(targetHeader, BigInteger.ONE, targetHeight));
        // Mock current pointer's header
        StoredBlock currentStored = mock(StoredBlock.class);
        BtcBlock currentBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(currentBlock).getHash();
        doReturn(currentBlock).when(currentStored).getHeader();
        when(currentStored.getHeader()).thenReturn(currentBlock);
        when(btcBlockStore.getChainHead()).thenReturn(currentStored);
        when(currentStored.getHeight()).thenReturn(headHeight);
    }
}
