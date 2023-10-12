package co.rsk.bridge.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.bridge.Bridge;
import co.rsk.bridge.BridgeStorageProvider;
import co.rsk.bridge.RepositoryBtcBlockStoreWithCache;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;

@Disabled
class LockingCapTest extends BridgePerformanceTestCase {

    private static final ECKey authorizedLockingCapChanger = ECKey.fromPrivate(Hex.decode("da6a5451bfd74829307ec6d4a8c55174d4859169f162a8ed8fcba8f7636e77cc"));
    private static final ECKey unauthorizedLockingCapChanger = ECKey.fromPrivate(Hex.decode("f18ad1e830dd746ba350f4a43b3067e85634b5138a8515246441a453ec7460e9"));

    private static final Coin INITIAL_LOCKING_CAP = BridgeRegTestConstants.getInstance().getInitialLockingCap();

    private ECKey sender;

    @BeforeAll
     static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    void getLockingCap() throws VMException {
        sender = authorizedLockingCapChanger;
        ExecutionStats stats = new ExecutionStats("getLockingCap");
        executeTestCase(
                (int executionIndex) -> Bridge.GET_LOCKING_CAP.encode(),
                "getLockingCap",
                2000,
                stats,
                (environment, callResult) -> Assertions.assertEquals(INITIAL_LOCKING_CAP, getCoinFromResult(callResult))
        );
        BridgePerformanceTest.addStats(stats);
    }

    @Test
    void increaseLockingCap() throws VMException {
        sender = authorizedLockingCapChanger;
        ExecutionStats stats = new ExecutionStats("increaseLockingCap");
        AtomicReference<Long> newValue = new AtomicReference<>();
        executeTestCase(
                (int executionIndex) -> {
                    newValue.set(Helper.randomCoin(INITIAL_LOCKING_CAP, 1, 2).getValue());
                    return Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{ newValue.get().longValue() });
                },
                "increaseLockingCap",
                2000,
                stats,
                (environment, callResult) -> {
                    long currentLockingCap = ((Bridge)environment.getContract()).getLockingCap(null);
                    Assertions.assertEquals(newValue.get().longValue(), currentLockingCap);
                }
        );
        BridgePerformanceTest.addStats(stats);
    }

    @Test
    void increaseLockingCap_unauthorized() throws VMException {
        sender = unauthorizedLockingCapChanger;
        ExecutionStats stats = new ExecutionStats("increaseLockingCap_unauthorized");
        executeTestCase(
                (int executionIndex) -> {
                    long newValue = Helper.randomCoin(INITIAL_LOCKING_CAP, 1, 2).getValue();
                    return Bridge.INCREASE_LOCKING_CAP.encode(new Object[]{ newValue });
                },
                "increaseLockingCap",
                2000,
                stats,
                (environment, callResult) -> {
                    Coin currentLockingCap = Coin.valueOf(((Bridge)environment.getContract()).getLockingCap(null));
                    Assertions.assertEquals(INITIAL_LOCKING_CAP, currentLockingCap);
                }
        );
        BridgePerformanceTest.addStats(stats);
    }

    private void executeTestCase(ABIEncoder abiEncoder, String name, int times, ExecutionStats stats, ResultCallback resultCallback) throws VMException {
        executeAndAverage(
                name,
                times,
                abiEncoder,
                buildInitializer(),
                (int executionIndex) -> Helper.buildTx(sender),
                Helper.getRandomHeightProvider(10),
                stats,
                resultCallback
        );
    }

    private BridgeStorageProviderInitializer buildInitializer() {
        final int minBtcBlocks = 500;
        final int maxBtcBlocks = 1000;

        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            BtcBlockStore btcBlockStore = new RepositoryBtcBlockStoreWithCache.Factory(networkParameters).newInstance(repository.startTracking(), bridgeConstants, provider, null);
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBtcBlocks, maxBtcBlocks);
            Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);
        };
    }

    private Coin getCoinFromResult(byte[] result) {
        return Coin.valueOf(((BigInteger)Bridge.GET_LOCKING_CAP.decodeResult(result)[0]).longValueExact());
    }
}
