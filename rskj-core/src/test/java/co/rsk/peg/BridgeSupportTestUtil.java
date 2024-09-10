package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.trie.Trie;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Repository;
import org.ethereum.db.MutableRepository;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class BridgeSupportTestUtil {
    public static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    public static PartialMerkleTree createValidPmtWithTransactions(List<Sha256Hash> hashesToAdd, NetworkParameters networkParameters) {
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        return new PartialMerkleTree(networkParameters, bits, hashesToAdd, 1);
    }

    public static void recreateChainAtHeightWithStoredBlockWithPmtAtHeight(
        BtcBlockStoreWithCache btcBlockStoreWithCache,
        int chainHeight,
        PartialMerkleTree partialMerkleTree,
        int btcBlockWithPmtHeight,
        NetworkParameters networkParameters
    ) throws BlockStoreException {

        // first create the block to have the wanted partial merkle tree
        BtcBlock btcBlockWithPmt = createBtcBlockWithPmt(partialMerkleTree, networkParameters);
        // stored it on the chain at wanted height
        StoredBlock storedBtcBlockThatHasTransactions = new StoredBlock(btcBlockWithPmt, BigInteger.ONE, btcBlockWithPmtHeight);
        btcBlockStoreWithCache.put(storedBtcBlockThatHasTransactions);
        btcBlockStoreWithCache.setMainChainBlock(btcBlockWithPmtHeight, btcBlockWithPmt.getHash());

        // create and store a new chainHead at wanted chain height
        Sha256Hash randomTransactionHash = Sha256Hash.of(Hex.decode("aa"));
        PartialMerkleTree pmt = createValidPmtWithTransactions(Collections.singletonList(randomTransactionHash), networkParameters);
        BtcBlock chainHeadBlock = createBtcBlockWithPmt(pmt, networkParameters);
        StoredBlock storedChainHeadBlock = new StoredBlock(chainHeadBlock, BigInteger.TEN, chainHeight);
        btcBlockStoreWithCache.put(storedChainHeadBlock);
        btcBlockStoreWithCache.setChainHead(storedChainHeadBlock);
    }

    private static BtcBlock createBtcBlockWithPmt(PartialMerkleTree pmt, NetworkParameters networkParameters) {
        Sha256Hash prevBlockHash = BitcoinTestUtils.createHash(1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());

        return new co.rsk.bitcoinj.core.BtcBlock(
            networkParameters,
            1,
            prevBlockHash,
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
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
