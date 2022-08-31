package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.core.RskAddress;
import co.rsk.peg.*;
import co.rsk.peg.bitcoin.MerkleBranch;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.solidity.SolidityType;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.*;
import org.mockito.invocation.InvocationOnMock;

import java.math.BigInteger;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

@Ignore
public class GetBtcTransactionConfirmationsTest extends BridgePerformanceTestCase {
    private Sha256Hash blockHash;
    private Sha256Hash txHash;
    private int merkleBranchPath;
    private List<Sha256Hash> merkleBranchHashes;
    private int expectedConfirmations;


    @BeforeClass
    public static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }


    private class DiskAccessRepositoryBlockStore extends RepositoryBtcBlockStoreWithCache {
        public DiskAccessRepositoryBlockStore(Repository repository, RskAddress contractAddress) {
            super(
                bridgeConstants.getBtcParams(),
                repository,
                null,
                contractAddress,
                null,
                null,
                null
            );
        }

        @Override
        public synchronized StoredBlock getFromCache(Sha256Hash hash) {
            return this.get(hash);
        }
    }

    @Before
    public void setRskipToTrue() throws VMException {
        warmUp();
    }

    private void warmUp() throws VMException {
        // Doing an initial estimation gets some things cached and speeds up the rest,
        // so that we get even numbers at the end
        System.out.print("Doing an initial pass... ");
        setQuietMode(true);
        estimateGetBtcTransactionConfirmations("foo", 10, 4000, 750, 1000, true);
        estimateGetBtcTransactionConfirmations("foo", 10, 4000, 750, 1000, false);
        setQuietMode(false);
        System.out.print("Done!\n");
    }

    @Test
    public void getBtcTransactionConfirmations_Weighed_Cache() throws VMException {
        final String CASE_NAME = "getBtcTransactionConfirmations-weighed";
        CombinedExecutionStats stats = new CombinedExecutionStats(CASE_NAME);

        // We always consider the average BTC block case from https://www.blockchain.com/charts/n-transactions-per-block

        // One day of BTC blocks
        stats.add(estimateGetBtcTransactionConfirmations(CASE_NAME, 300, 144, 750, 3000, true));
        // Maximum number of confirmations
        stats.add(estimateGetBtcTransactionConfirmations(CASE_NAME, 10, BridgeSupport.BTC_TRANSACTION_CONFIRMATION_MAX_DEPTH, 750, 3000, true));
        // Single confirmation
        stats.add(estimateGetBtcTransactionConfirmations(CASE_NAME, 10, 0,750,3000, true));

        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void getBtcTransactionConfirmations_Weighed_Disk() throws VMException {
        final String CASE_NAME = "getBtcTransactionConfirmations-weighed";
        CombinedExecutionStats stats = new CombinedExecutionStats(CASE_NAME);

        // We always consider the average BTC block case from https://www.blockchain.com/charts/n-transactions-per-block

        // One day of BTC blocks
        stats.add(estimateGetBtcTransactionConfirmations(CASE_NAME, 300, 144, 750, 3000, false));
        // Maximum number of confirmations
        stats.add(estimateGetBtcTransactionConfirmations(CASE_NAME, 10, BridgeSupport.BTC_TRANSACTION_CONFIRMATION_MAX_DEPTH, 750, 3000, false));
        // Single confirmation
        stats.add(estimateGetBtcTransactionConfirmations(CASE_NAME, 10, 0,750,3000, false));

        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void getBtcTransactionConfirmations_Even_Cache() throws VMException {
        final String CASE_NAME = "getBtcTransactionConfirmations-even";
        CombinedExecutionStats stats = new CombinedExecutionStats(CASE_NAME);

        // Up to two days of confirmations (average of 6 blocks per hour)
        final int MAX_CONFIRMATIONS = 288;//6*24*2

        for (int numConfirmations = 0; numConfirmations <= MAX_CONFIRMATIONS; numConfirmations++) {
            stats.add(estimateGetBtcTransactionConfirmations(CASE_NAME, 100, numConfirmations, 750, 3000, true));
        }

        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void getBtcTransactionConfirmations_Even_Disk() throws VMException {
        final String CASE_NAME = "getBtcTransactionConfirmations-even";
        CombinedExecutionStats stats = new CombinedExecutionStats(CASE_NAME);

        // Up to two days of confirmations (average of 6 blocks per hour)
        final int MAX_CONFIRMATIONS = 288;//6*24*2

        for (int numConfirmations = 0; numConfirmations <= MAX_CONFIRMATIONS; numConfirmations++) {
            stats.add(estimateGetBtcTransactionConfirmations(CASE_NAME, 100, numConfirmations, 750, 3000, false));
        }

        BridgePerformanceTest.addStats(stats);
    }

    @Test
    public void getBtcTransactionConfirmations_Zero() throws VMException {
        BridgePerformanceTest.addStats(estimateGetBtcTransactionConfirmations(
                "getBtcTransactionConfirmations-zero",
                2000, 0, 750, 3000, true
        ));

        BridgePerformanceTest.addStats(estimateGetBtcTransactionConfirmations(
                "getBtcTransactionConfirmations-zero",
                2000, 0, 750, 3000, false
        ));
    }

    @Test
    public void getBtcTransactionConfirmations_Hundred() throws VMException {
        BridgePerformanceTest.addStats(estimateGetBtcTransactionConfirmations(
                "getBtcTransactionConfirmations-hundred",
                2000, 100, 750, 3000, true
        ));

        BridgePerformanceTest.addStats(estimateGetBtcTransactionConfirmations(
                "getBtcTransactionConfirmations-hundred",
                2000, 100, 750, 3000, false
        ));
    }

    private ExecutionStats estimateGetBtcTransactionConfirmations(
            String caseName,
            int times, int confirmations, int  minTransactions,
            int maxTransactions, boolean useCache) throws VMException {

        BridgeStorageProviderInitializer storageInitializer = generateBlockChainInitializer(
                1000,
                2000,
                confirmations,
                minTransactions,
                maxTransactions
        );

        String name = String.format("%s-%d-%s", caseName, confirmations, useCache ? "cache" : "disk");
        ExecutionStats stats = new ExecutionStats(name);

        executeAndAverage(
                name,
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10),
                stats,
                (environment, executionResult) -> {
                    int numberOfConfirmations = new BigInteger(executionResult).intValueExact();
                    Assert.assertEquals(expectedConfirmations, numberOfConfirmations);
                },
                (EnvironmentBuilder.Environment environment) -> {
                    if (!useCache) {
                        Bridge bridge = (Bridge) environment.getContract();
                        BridgeSupport bridgeSupport = TestUtils.getInternalState(bridge, "bridgeSupport");
                        Repository repository = TestUtils.getInternalState(bridgeSupport, "rskRepository");
                        BtcBlockStore diskAccessRepositoryBlockStore = new DiskAccessRepositoryBlockStore(repository, PrecompiledContracts.BRIDGE_ADDR);
                        TestUtils.setInternalState(bridgeSupport, "btcBlockStore", diskAccessRepositoryBlockStore);
                    }
                }
        );

        return stats;
    }

    private byte[] encodeBytes32(Object value) {
        final int Int32Size = 32;

        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            byte[] ret = new byte[Int32Size];
            System.arraycopy(bytes, 0, ret, Int32Size - bytes.length, bytes.length);
            return ret;
        }

        throw new RuntimeException("Can't encode java type " + value.getClass() + " to bytes32");
    }

    private byte[] encodeBytes32OnMock(InvocationOnMock m) {
        return encodeBytes32(m.getArgument(0));
    }

    private ABIEncoder getABIEncoder() {
        // Hack to bypass buggy Bytes32Type::encode method (currently only supporting Number or String inputs!)
        // This has been fixed in EthereumJ, we should probably pull that fix at some point.
        CallTransaction.Function getBtcTransactionConfirmationsFn = CallTransaction.Function.fromSignature(
                "getBtcTransactionConfirmations",
                new String[]{"bytes32", "bytes32", "uint256", "bytes32[]"},
                new String[]{"int256"}
        );

        SolidityType input0Type = spy(getBtcTransactionConfirmationsFn.inputs[0].type);
        SolidityType input1Type = spy(getBtcTransactionConfirmationsFn.inputs[1].type);
        SolidityType input3ElementType = spy((SolidityType) TestUtils.getInternalState(getBtcTransactionConfirmationsFn.inputs[3].type, "elementType"));
        doAnswer(this::encodeBytes32OnMock).when(input0Type).encode(any());
        doAnswer(this::encodeBytes32OnMock).when(input1Type).encode(any());
        doAnswer(this::encodeBytes32OnMock).when(input3ElementType).encode(any());
        getBtcTransactionConfirmationsFn.inputs[0].type = input0Type;
        getBtcTransactionConfirmationsFn.inputs[1].type = input1Type;
        TestUtils.setInternalState(getBtcTransactionConfirmationsFn.inputs[3].type, "elementType", input3ElementType);

        return (int executionIndex) ->
                getBtcTransactionConfirmationsFn.encode(new Object[]{
                        txHash.getBytes(),
                        blockHash.getBytes(),
                        merkleBranchPath,
                        merkleBranchHashes.stream().map(h -> h.getBytes()).toArray()
                });
    }

    private BridgeStorageProviderInitializer generateBlockChainInitializer(int minBtcBlocks, int maxBtcBlocks, int numberOfConfirmations, int minNumberOfTransactions, int maxNumberOfTransactions) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, blockStore);
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

            // Calculate the merkle root by computing the merkle tree
            List<Sha256Hash> merkleTree = buildMerkleTree(allLeafHashes);
            Sha256Hash merkleRoot = merkleTree.get(merkleTree.size() - 1);

            BtcBlock blockWithTx = Helper.generateBtcBlock(lastBlock, Collections.emptyList(), merkleRoot);
            btcBlockChain.add(blockWithTx);

            // Add BTC confirmations on top
            Helper.generateAndAddBlocks(btcBlockChain, numberOfConfirmations);

            // Build the merkle branch, and make sure calculations are sound
            MerkleBranch merkleBranch = buildMerkleBranch(merkleTree, numberOfTransactions, targetTxPosition);
            Assert.assertEquals(merkleRoot, merkleBranch.reduceFrom(targetTx.getHash()));

            // Parameters to the actual bridge method
            blockHash = blockWithTx.getHash();
            txHash = targetTx.getHash();
            merkleBranchPath = merkleBranch.getPath();
            merkleBranchHashes = merkleBranch.getHashes();
            expectedConfirmations = numberOfConfirmations + 1;

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
