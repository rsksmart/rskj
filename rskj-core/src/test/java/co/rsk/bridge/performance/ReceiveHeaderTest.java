package co.rsk.bridge.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bridge.Bridge;
import co.rsk.bridge.BridgeStorageProvider;
import co.rsk.bridge.RepositoryBtcBlockStoreWithCache;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.ethereum.vm.exception.VMException;

import java.math.BigInteger;

@Disabled
class ReceiveHeaderTest extends BridgePerformanceTestCase {
    private BtcBlock lastBlock;
    private BtcBlock expectedBlock;

    @BeforeAll
     static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    void ReceiveHeaderTest() throws VMException {
        ExecutionStats stats = new ExecutionStats("receiveHeader");
        receiveHeader_success(1000, stats);
        receiveHeader_block_already_saved(500, stats);
        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
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
                        Assertions.assertEquals(0, result);
                        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
                                (Repository) environment.getBenchmarkedRepository(),
                                PrecompiledContracts.BRIDGE_ADDR,
                                constants.getBridgeConstants(),
                                activationConfig.forBlock(0)
                        );

                        // Best Block is the new block added.
                        BtcBlockStore btcBlockStore = new RepositoryBtcBlockStoreWithCache.
                                Factory(networkParameters).
                                newInstance(
                                        (Repository) environment.getBenchmarkedRepository(),
                                        bridgeConstants,
                                        bridgeStorageProvider,
                                        null
                                );

                        Sha256Hash bestBlockHash = null;
                        try {
                            bestBlockHash = btcBlockStore.getChainHead().getHeader().getHash();
                        } catch (BlockStoreException e) {
                            Assertions.fail(e.getMessage());
                        }
                        Assertions.assertEquals(expectedBlock.getHash(), bestBlockHash);
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
                    Assertions.assertEquals(-4, result);
                }
        );
    }

    private ABIEncoder getABIEncoder(boolean testExistingBlock) {
        return (int executionIndex) -> {
            BtcBlock block = null;
            if (testExistingBlock) {
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
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, blockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBlocks, maxBlocks);
            lastBlock = Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);
            expectedBlock = Helper.generateBtcBlock(lastBlock);
        };
    }
}
