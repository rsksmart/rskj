package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import org.ethereum.vm.exception.VMException;

import java.math.BigInteger;
import java.util.HashMap;

@Ignore
public class ReceiveHeaderTest extends BridgePerformanceTestCase {
    private BtcBlockStore btcBlockStore;
    private BtcBlock lastBlock;
    private BtcBlock expectedBlock;

    @BeforeClass
    public static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    public void ReceiveHeaderTest() throws VMException {
        ExecutionStats stats = new ExecutionStats("receiveHeader");
        receiveHeader_success(1000, stats);
        receiveHeader_block_already_saved(500, stats);
        Assert.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void receiveHeader_success(int times, ExecutionStats stats) throws VMException {
        int minBlocks = 10;
        int maxBlocks = 100;

        BridgeStorageProviderInitializer storageInitializer = generateInitializer(minBlocks, maxBlocks);

        executeAndAverage(
                    "receiveHeader_success",
                    times,
                    getABIEncoder(false), // false to use an existed block (create new block)
                    storageInitializer,
                    Helper.getZeroValueRandomSenderTxBuilder(),
                    Helper.getRandomHeightProvider(10),
                    stats,
                    (environment, executionResult) -> {

                        // Working fine.
                        int result = new BigInteger(executionResult).intValue();
                        Assert.assertEquals(0, result);

                        // Best Block is the new block added.
                        BtcBlockStore btcBlockStore = new RepositoryBtcBlockStoreWithCache.
                                Factory(networkParameters).
                                newInstance(
                                        (Repository) environment.getBenchmarkedRepository(),
                                        bridgeConstants,
                                        (BridgeStorageProvider) environment.getStorageProvider(),
                                        null
                                );

                        Sha256Hash bestBlockHash = null;
                        try {
                            bestBlockHash = btcBlockStore.getChainHead().getHeader().getHash();
                        } catch (BlockStoreException e) {
                            Assert.fail(e.getMessage());
                        }
                        Assert.assertEquals(expectedBlock.getHash(), bestBlockHash);
                    }
            );
        }

    private void receiveHeader_block_already_saved(int times, ExecutionStats stats) throws VMException {
        int minBlocks = 10;
        int maxBlocks = 100;

        BridgeStorageProviderInitializer storageInitializer = generateInitializer(minBlocks, maxBlocks);

        executeAndAverage(
                "receiveHeader_block_already_saved",
                times,
                getABIEncoder(true), // true to use an existing block
                storageInitializer,
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10),
                stats,
                (environment, executionResult) -> {
                    int result = new BigInteger(executionResult).intValue();
                    Assert.assertEquals(-4, result);
                }
        );
    }

    private ABIEncoder getABIEncoder(boolean testExistedBlock) {
        return (int executionIndex) -> {
            BtcBlock block = null;
            if (testExistedBlock) {
                // lastBlock is an already existing block.
                block = lastBlock.cloneAsHeader();
            } else {
                // uses a new block created
                block = expectedBlock.cloneAsHeader();
            }

            Object[] parameter = new Object[]{block.bitcoinSerialize()};
            return Bridge.RECEIVE_HEADER.encode(parameter);
        };
    }

    private BridgeStorageProviderInitializer generateInitializer(int minBlocks, int maxBlocks) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            btcBlockStore = blockStore;
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBlocks, maxBlocks);
            lastBlock = Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);
            expectedBlock = Helper.generateBtcBlock(lastBlock);
        };
    }
}
