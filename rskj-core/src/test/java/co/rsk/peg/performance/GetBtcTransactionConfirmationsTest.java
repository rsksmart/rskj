package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.TestSystemProperties;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.RepositoryBlockStore;
import co.rsk.peg.bitcoin.MerkleBranch;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import java.math.BigInteger;
import java.util.*;

@Ignore
public class GetBtcTransactionConfirmationsTest extends BridgePerformanceTestCase {
    private Sha256Hash blockHash;
    private Sha256Hash txHash;
    private BigInteger merkleBranchPath;
    private List<Sha256Hash> merkleBranchHashes;
    private int expectedConfirmations;

    @Test
    public void getBtcTransactionConfirmations() {
        ExecutionStats stats = new ExecutionStats("getBtcTransactionConfirmations");
        //Minimum value
//        getBtcTransactionConfirmations_success(10, stats, 0,1,1);
        //One day in BTC Blocks
        //Avarage Btc block from https://www.blockchain.com/charts/n-transactions-per-block
        getBtcTransactionConfirmations_success(300, stats, 144, 750, 3000);
        //Maximum value
        //6000 transactions in a block. This value comes from considering the smallest transactions  giving an output of 10 tx/s
//        getBtcTransactionConfirmations_success(10, stats, RepositoryBlockStore.MAX_SIZE_MAP_STORED_BLOCKS, 6000, 6000);

        BridgePerformanceTest.addStats(stats);
    }

    private void getBtcTransactionConfirmations_success(int times, ExecutionStats stats, int confirmations, int  minTransactions, int maxTransactions) {
        BridgeStorageProviderInitializer storageInitializer = generateBlockChainInitializer(
                1000,
                2000,
                confirmations,
                minTransactions,
                maxTransactions
        );

        executeAndAverage(
                String.format("getBtcTransactionConfirmations-success-confirmations-%d", confirmations),
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10), stats,
                (executionResult) -> {
                    byte[] res = executionResult;
//                    Assert.assertTrue(Arrays.equals(executionResult, expectedConfirmations));
                }
        );
    }

    private ABIEncoder getABIEncoder() {
        return (int executionIndex) ->
                Bridge.GET_BTC_TRANSACTION_CONFIRMATIONS.encode(new Object[]{
                        txHash.getBytes(),
                        blockHash.getBytes(),
                        merkleBranchPath,
                        merkleBranchHashes.stream().map(h -> h.getBytes()).toArray()
                });
    }

    private BridgeStorageProviderInitializer generateBlockChainInitializer(int minBtcBlocks, int maxBtcBlocks, int numberOfConfirmations, int minNumberOfTransactions, int maxNumberOfTransactions) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {
            BtcBlockStore btcBlockStore = new RepositoryBlockStore(new TestSystemProperties(), repository, PrecompiledContracts.BRIDGE_ADDR);
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBtcBlocks, maxBtcBlocks);
            BtcBlock lastBlock = Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);

            // Build target transaction
            BtcTransaction targetTx = new BtcTransaction(networkParameters);
            BtcECKey to = new BtcECKey();
            Address toAddress = to.toAddress(networkParameters);
            Coin investAmount = Coin.CENT.multiply(Helper.randomInRange(10, 100)); //fromAmount.divide(Helper.randomInRange(2, 10));
            targetTx.addOutput(investAmount, toAddress);

            // Populate the block with a random number of simulated random transactions
            List<Sha256Hash> allLeafHashes = new ArrayList<>();
            int numberOfTransactions = Helper.randomInRange(minNumberOfTransactions, maxNumberOfTransactions);
            int targetTxPosition = Helper.randomInRange(0, numberOfTransactions-1);

            Random rnd = new Random();
            for(int i=0; i < numberOfTransactions ; i++) {
                if (i == targetTxPosition) {
                    allLeafHashes.add(targetTx.getHash());
                } else {
                    allLeafHashes.add(Sha256Hash.of(BigInteger.valueOf(rnd.nextLong()).toByteArray()));
                }
            }

            // Add BTC confirmations on top
            Helper.generateAndAddBlocks(btcBlockChain, numberOfConfirmations);

            // Calculate the merkle root by computing the merkle tree
            List<Sha256Hash> merkleTree = buildMerkleTree(allLeafHashes);
            Sha256Hash merkleRoot = merkleTree.get(merkleTree.size() - 1);

            BtcBlock blockWithTx = Helper.generateBtcBlock(lastBlock, Collections.emptyList(), merkleRoot);
            btcBlockChain.add(blockWithTx);

            MerkleBranch merkleBranch = buildMerkleBranch(merkleTree, numberOfTransactions, targetTxPosition);

            // Make sure calculations are sound
            Assert.assertEquals(merkleRoot, merkleBranch.reduceFrom(targetTx.getHash()));

            // Parameters to the actual bridge method
            blockHash = blockWithTx.getHash();
            txHash = targetTx.getHash();
            merkleBranchPath = BigInteger.valueOf(merkleBranch.getPath());
            merkleBranchHashes = merkleBranch.getHashes();
            expectedConfirmations = numberOfConfirmations;
        };
    }

    // Taken from bitcoinj
    private List<Sha256Hash> buildMerkleTree(List<Sha256Hash> transactionHashes) {
        ArrayList<Sha256Hash> tree = new ArrayList();
        Iterator var2 = transactionHashes.iterator();

        while(var2.hasNext()) {
            byte[] txHashBytes = ((Sha256Hash) var2.next()).getBytes();
            Sha256Hash txHashCopy = Sha256Hash.wrap(Arrays.copyOf(txHashBytes, txHashBytes.length));
            tree.add(txHashCopy);
        }

        int levelOffset = 0;

        for (int levelSize = transactionHashes.size(); levelSize > 1; levelSize = (levelSize + 1) / 2) {
            for (int left = 0; left < levelSize; left += 2) {
                int right = Math.min(left + 1, levelSize - 1);
                byte[] leftBytes = Utils.reverseBytes(tree.get(levelOffset + left).getBytes());
                byte[] rightBytes = Utils.reverseBytes(tree.get(levelOffset + right).getBytes());
                tree.add(Sha256Hash.wrap(Utils.reverseBytes(Sha256Hash.hashTwice(leftBytes, 0, 32, rightBytes, 0, 32))));
            }

            levelOffset += levelSize;
        }

        return tree;
    }

    private MerkleBranch buildMerkleBranch(List<Sha256Hash> merkleTree, int txCount, int txIndex) {
        List<Sha256Hash> hashes = new ArrayList<>();
        int path = 0;
        int pathIndex = 0;
        int levelOffset = 0;
        int currentNodeOffset = txIndex;

        for (int levelSize = txCount; levelSize > 1; levelSize = (levelSize + 1) / 2) {
            int targetOffset;
            if (currentNodeOffset % 2 == 0) {
                // Target is left hand side, use right hand side
                targetOffset = Math.min(currentNodeOffset + 1, levelSize - 1);
            } else {
                // Target is right hand side, use left hand side
                targetOffset = currentNodeOffset - 1;
                path = path + (1 << pathIndex);
            }
            hashes.add(merkleTree.get(levelOffset + targetOffset));

            levelOffset += levelSize;
            currentNodeOffset = currentNodeOffset / 2;
            pathIndex++;
        }

        return new MerkleBranch(hashes, path);
    }
}
