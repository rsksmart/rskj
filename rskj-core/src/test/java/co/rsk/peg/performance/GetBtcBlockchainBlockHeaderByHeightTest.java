package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcBlockChain;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.*;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeAll;

import java.math.BigInteger;

@Disabled
class GetBtcBlockchainBlockHeaderByHeightTest extends BridgePerformanceTestCase {
    private BtcBlock expectedBlock;
    private int height;

    @BeforeAll
     static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    void getBtcBlockchainBlockHeaderByHeightTest() throws VMException {
        ExecutionStats stats = new ExecutionStats("getBtcBlockchainBlockHeaderByHeight");
        getBtcBlockchainBlockHeaderByHeightTest_success(1000, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void getBtcBlockchainBlockHeaderByHeightTest_success(int times, ExecutionStats stats) throws VMException {

        BridgeStorageProviderInitializer storageInitializer = generateInitializerForTest(
                1000,
                2000
        );

        executeAndAverage(
                "getBtcBlockchainBlockHeaderByHeightTest_success",
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10),
                stats,
                (environment, executionResult) -> {
                    BtcBlock btcBlock = byteArrayToBlockHeader(getByteFromResult(executionResult));
                    Assertions.assertEquals(btcBlock.getHash(), expectedBlock.getHash());
                }
        );
    }

    private ABIEncoder getABIEncoder() {
        return (int executionIndex) ->
                BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_HEADER_BY_HEIGHT.getFunction().encode(
                           BigInteger.valueOf(height)
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
            height = Helper.randomInRange(minBtcBlocks, blocksToGenerate);
            expectedBlock = Helper.generateAndAddBlocks(btcBlockChain, height);
            Helper.generateAndAddBlocks(btcBlockChain, (maxBtcBlocks - height));
            thisRepository.commit();
        };
    }

    private BtcBlock byteArrayToBlockHeader(byte[] ba) {
        return new BtcBlock(networkParameters, ba, 0, networkParameters.getSerializer(false), ba.length);
    }

    private byte[] getByteFromResult(byte[] result) {
        return (byte[]) Bridge.GET_BTC_BLOCKCHAIN_BLOCK_HEADER_BY_HEIGHT.decodeResult(result)[0];
    }
}
