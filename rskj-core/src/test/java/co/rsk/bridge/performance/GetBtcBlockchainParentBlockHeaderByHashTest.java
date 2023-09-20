package co.rsk.bridge.performance;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcBlockChain;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bridge.*;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
class GetBtcBlockchainParentBlockHeaderByHashTest extends BridgePerformanceTestCase {
    private BtcBlock expectedBlock;
    private Sha256Hash originalBlockHash;

    @BeforeAll
     static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    void getBtcBlockchainParentBlockHeaderByHashTest() throws VMException {
        ExecutionStats stats = new ExecutionStats("getBtcBlockchainParentBlockHeaderByHash");
        getBtcBlockchainParentBlockHeaderByHashTest_success(1000, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void getBtcBlockchainParentBlockHeaderByHashTest_success(int times, ExecutionStats stats) throws VMException {

        BridgeStorageProviderInitializer storageInitializer = generateInitializerForTest(
                1000,
                2000
        );

        executeAndAverage(
                "getBtcBlockchainParentBlockHeaderByHashTest_success",
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10),
                stats,
                (environment, executionResult) -> {
                    BtcBlock btcBlock = byteArrayToBlockHeader(getByteFromResult(executionResult));
                    Assertions.assertEquals(expectedBlock.getHash(), btcBlock.getHash());
                }
        );
    }

    private ABIEncoder getABIEncoder() {
        return (int executionIndex) ->
                BridgeMethods.GET_BTC_BLOCKCHAIN_PARENT_BLOCK_HEADER_BY_HASH.getFunction().encode(
                        new Object[]{
                                originalBlockHash.toBigInteger()
                        }
                );
    }

    private BridgeStorageProviderInitializer generateInitializerForTest(
            int minBtcBlocks,
            int maxBtcBlocks
    ) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams());
            Repository thisRepository = repository.startTracking();
            BtcBlockStore btcBlockStore = btcBlockStoreFactory
                    .newInstance(thisRepository, bridgeConstants, provider, activationConfig.forBlock(0));
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBtcBlocks, maxBtcBlocks);
            expectedBlock = Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate-1);
            originalBlockHash = Helper.generateAndAddBlocks(btcBlockChain, 1).getHash();
            thisRepository.commit();
        };
    }

    private BtcBlock byteArrayToBlockHeader(byte[] ba) {
        return new BtcBlock(networkParameters, ba, 0, networkParameters.getSerializer(false), ba.length);
    }

    private byte[] getByteFromResult(byte[] result) {
        return (byte[]) Bridge.GET_BTC_BLOCKCHAIN_PARENT_BLOCK_HEADER_BY_HASH.decodeResult(result)[0];
    }
}
