package co.rsk.peg;

import static co.rsk.peg.BridgeStorageIndexKey.RELEASES_OUTPOINTS_VALUES;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.trie.Trie;
import java.math.BigInteger;
import java.util.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;

public final class BridgeSupportTestUtil {
    public static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    public static PartialMerkleTree createValidPmtForTransactions(List<BtcTransaction> btcTransactions, NetworkParameters networkParameters) {
        List<Sha256Hash> hashesToAdd = new ArrayList<>();
        for (BtcTransaction transaction : btcTransactions) {
            hashesToAdd.add(getHashConsideringWitness(transaction));
        }

        return createValidPmtForTransactionsHashes(hashesToAdd, networkParameters);
    }

    private static Sha256Hash getHashConsideringWitness(BtcTransaction btcTransaction) {
        if (!btcTransaction.hasWitness()) {
            return btcTransaction.getHash();
        }

        return btcTransaction.getHash(true);
    }

    private static PartialMerkleTree createValidPmtForTransactionsHashes(List<Sha256Hash> hashesToAdd, NetworkParameters networkParameters) {
        byte[] relevantNodesBits = new byte[(int)Math.ceil(hashesToAdd.size() / 8.0)];
        for (int i = 0; i < hashesToAdd.size(); i++) {
            Utils.setBitLE(relevantNodesBits, i);
        }

        return PartialMerkleTree.buildFromLeaves(networkParameters, relevantNodesBits, hashesToAdd);
    }

    public static void recreateChainFromPmt(
        BtcBlockStoreWithCache btcBlockStoreWithCache,
        int chainHeight,
        PartialMerkleTree partialMerkleTree,
        int btcBlockWithPmtHeight,
        NetworkParameters networkParameters
    ) throws BlockStoreException {

        // first create a block that has the wanted partial merkle tree
        BtcBlock btcBlockWithPmt = createBtcBlockWithPmt(partialMerkleTree, networkParameters);
        // store it on the chain at wanted height
        StoredBlock storedBtcBlockWithPmt = new StoredBlock(btcBlockWithPmt, BigInteger.ONE, btcBlockWithPmtHeight);
        btcBlockStoreWithCache.put(storedBtcBlockWithPmt);
        btcBlockStoreWithCache.setMainChainBlock(btcBlockWithPmtHeight, btcBlockWithPmt.getHash());

        // create and store a new chainHead at wanted chain height
        Sha256Hash otherTransactionHash = Sha256Hash.of(Hex.decode("aa"));
        PartialMerkleTree pmt = createValidPmtForTransactionsHashes(List.of(otherTransactionHash), networkParameters);
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

    public static Keccak256 getFlyoverDerivationHash(
        ActivationConfig.ForBlock activations,
        Keccak256 derivationArgumentsHash,
        Address userRefundAddress,
        Address lpBtcAddress,
        RskAddress lbcAddress
    ) {
        byte[] flyoverDerivationHashData = derivationArgumentsHash.getBytes();
        byte[] userRefundAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, userRefundAddress);
        byte[] lpBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, lpBtcAddress);
        byte[] lbcAddressBytes = lbcAddress.getBytes();
        byte[] result = new byte[
            flyoverDerivationHashData.length +
                userRefundAddressBytes.length +
                lpBtcAddressBytes.length +
                lbcAddressBytes.length
            ];

        int dstPosition = 0;

        System.arraycopy(
            flyoverDerivationHashData,
            0,
            result,
            dstPosition,
            flyoverDerivationHashData.length
        );
        dstPosition += flyoverDerivationHashData.length;

        System.arraycopy(
            userRefundAddressBytes,
            0,
            result,
            dstPosition,
            userRefundAddressBytes.length
        );
        dstPosition += userRefundAddressBytes.length;

        System.arraycopy(
            lbcAddressBytes,
            0,
            result,
            dstPosition,
            lbcAddressBytes.length
        );
        dstPosition += lbcAddressBytes.length;

        System.arraycopy(
            lpBtcAddressBytes,
            0,
            result,
            dstPosition,
            lpBtcAddressBytes.length
        );

        return new Keccak256(HashUtil.keccak256(result));
    }

    public static DataWord getStorageKeyForReleaseOutpointsValues(Sha256Hash releaseTxHash) {
        return RELEASES_OUTPOINTS_VALUES.getCompoundKey("-", releaseTxHash.toString());
    }
}
