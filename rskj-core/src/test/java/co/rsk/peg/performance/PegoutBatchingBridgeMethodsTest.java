package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.BridgeMethods;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.ReleaseRequestQueue;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeAll;

import java.math.BigInteger;

/**
 * @author Kelvin Isievwore
 * @since 20.03.22
 */
@Disabled
class PegoutBatchingBridgeMethodsTest extends BridgePerformanceTestCase {

    @BeforeAll
     static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    void getQueuedPegoutsCountTest() throws VMException {
        ExecutionStats queuedPegoutsCountStats = new ExecutionStats("getQueuedPegoutsCount");
        getQueuedPegoutsCountTest(1000, queuedPegoutsCountStats);
        BridgePerformanceTest.addStats(queuedPegoutsCountStats);
    }

    @Test
    void getNextPegoutCreationBlockNumberTest() throws VMException {
        ExecutionStats nextPegoutCreationBlockNumberStats = new ExecutionStats("getNextPegoutCreationBlockNumber");
        getNextPegoutCreationBlockNumberTest(1000, nextPegoutCreationBlockNumberStats);
        BridgePerformanceTest.addStats(nextPegoutCreationBlockNumberStats);
    }

    @Test
    void getEstimatedFeesForNextPegOutEventTest() throws VMException {
        ExecutionStats estimatedFeesForNextPegOutEventStats = new ExecutionStats("getEstimatedFeesForNextPegOutEvent");
        getEstimatedFeesForNextPegOutEventTest(1000, estimatedFeesForNextPegOutEventStats);
        BridgePerformanceTest.addStats(estimatedFeesForNextPegOutEventStats);
    }

    private void getQueuedPegoutsCountTest(int times, ExecutionStats stats) throws VMException {

        ABIEncoder abiEncoder = (int executionIndex) -> BridgeMethods.GET_QUEUED_PEGOUTS_COUNT.getFunction().encode();

        executeAndAverage(
            "getQueuedPegoutsCount",
            times,
            abiEncoder,
            getInitializer(),
            Helper.getZeroValueValueTxBuilderFromFedMember(),
            Helper.getRandomHeightProvider(10),
            stats,
            (environment, executionResult) -> {
                long totalAmount = new BigInteger(executionResult).longValueExact();
                Assertions.assertTrue(totalAmount > 0);
            }
        );
    }

    private void getNextPegoutCreationBlockNumberTest(int times, ExecutionStats stats) throws VMException {

        ABIEncoder abiEncoder = (int executionIndex) -> BridgeMethods.GET_NEXT_PEGOUT_CREATION_BLOCK_NUMBER.getFunction().encode();

        BridgeStorageProviderInitializer storageInitializer = (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            long blockNumber = Helper.randomInRange(10, 100);
            provider.setNextPegoutHeight(blockNumber);
        };

        executeAndAverage(
            "getNextPegoutCreationBlockNumber",
            times,
            abiEncoder,
            storageInitializer,
            Helper.getZeroValueValueTxBuilderFromFedMember(),
            Helper.getRandomHeightProvider(10),
            stats,
            (environment, executionResult) -> {
                long totalAmount = new BigInteger(executionResult).longValueExact();
                Assertions.assertTrue(totalAmount > 0);
            }
        );
    }

    private void getEstimatedFeesForNextPegOutEventTest(int times, ExecutionStats stats) throws VMException {

        ABIEncoder abiEncoder = (int executionIndex) -> BridgeMethods.GET_ESTIMATED_FEES_FOR_NEXT_PEGOUT_EVENT.getFunction().encode();

        executeAndAverage(
            "getEstimatedFeesForNextPegOutEvent",
            times,
            abiEncoder,
            getInitializer(),
            Helper.getZeroValueValueTxBuilderFromFedMember(),
            Helper.getRandomHeightProvider(10),
            stats,
            (environment, executionResult) -> {
                long totalAmount = new BigInteger(executionResult).longValueExact();
                Assertions.assertTrue(totalAmount > 0);
            }
        );
    }

    private BridgeStorageProviderInitializer getInitializer() {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            final int queueSize = Helper.randomInRange(10, 100);

            ReleaseRequestQueue queue;
            try {
                queue = provider.getReleaseRequestQueue();
            } catch (Exception e) {
                throw new RuntimeException("Unable to gather release request queue");
            }

            for (int i = 0; i < queueSize; i++) {
                queue.add(new BtcECKey().toAddress(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)), Coin.COIN, null);
            }
        };
    }
}
