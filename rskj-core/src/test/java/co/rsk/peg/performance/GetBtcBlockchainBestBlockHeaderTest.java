package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.*;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class GetBtcBlockchainBestBlockHeaderTest extends BridgePerformanceTestCase {
    private BtcBlock lastBlock;

    @BeforeAll
    public static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    public void getBtcBlockchainBestBlockHeaderTest() throws VMException {
        ExecutionStats stats = new ExecutionStats("getBtcBlockchainBestBlockHeader");
        getBtcBlockchainBestBlockHeaderTest_success(1000, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void getBtcBlockchainBestBlockHeaderTest_success(int times, ExecutionStats stats) throws VMException {

        BridgeStorageProviderInitializer storageInitializer = generateInitializerForTest(
                1000,
                2000
        );

        executeAndAverage(
                "getBestBlockHeaderTest_success",
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10),
                stats,
                (environment, executionResult) -> {
                    BtcBlock btcBlock = byteArrayToBlockHeader(getByteFromResult(executionResult));
                    Assertions.assertEquals(btcBlock.getHash(), lastBlock.getHash());
                }
        );
    }

    private ABIEncoder getABIEncoder() {
        return (int executionIndex) ->
                BridgeMethods.GET_BTC_BLOCKCHAIN_BEST_BLOCK_HEADER.getFunction().encode();
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
            lastBlock = Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);

            thisRepository.commit();
        };
    }

    private BtcBlock byteArrayToBlockHeader(byte[] ba) {
        return new BtcBlock(networkParameters, ba, 0, networkParameters.getSerializer(false), ba.length);
    }

    private byte[] getByteFromResult(byte[] result) {
        return (byte[])Bridge.GET_BTC_BLOCKCHAIN_BEST_BLOCK_HEADER.decodeResult(result)[0];
    }
}
