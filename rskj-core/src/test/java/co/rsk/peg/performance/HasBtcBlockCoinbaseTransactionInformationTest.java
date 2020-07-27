package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeMethods;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

@Ignore
public class HasBtcBlockCoinbaseTransactionInformationTest extends BridgePerformanceTestCase {

    private Sha256Hash witnessRoot;

    @BeforeClass
    public static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    public void hasBtcBlockCoinbaseTransactionInformation() {
        ExecutionStats stats = new ExecutionStats("hasBtcBlockCoinbaseTransactionInformation");
        hasBtcBlockCoinbaseTransactionInformation_success(5000, stats);
        hasBtcBlockCoinbaseTransactionInformation_unsuccess(5000, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void hasBtcBlockCoinbaseTransactionInformation_success(int times, ExecutionStats stats) {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForTest(
            Sha256Hash.ZERO_HASH
        );

        executeAndAverage("hasBtcBlockCoinbaseTransactionInformation-sucess",
                times,
                getABIEncoder(Sha256Hash.ZERO_HASH),
                storageInitializer,
                Helper.getZeroValueValueTxBuilderFromFedMember(),
                Helper.getRandomHeightProvider(10),
                stats,
                (environment, callResult) -> Assert.assertTrue((boolean) Bridge.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.decodeResult(callResult)[0]));
    }

    private void hasBtcBlockCoinbaseTransactionInformation_unsuccess(int times, ExecutionStats stats) {
        BridgeStorageProviderInitializer storageInitializer = generateInitializerForTest(
                Sha256Hash.ZERO_HASH
        );

        executeAndAverage("hasBtcBlockCoinbaseTransactionInformation-unsucess",
                times,
                getABIEncoder(Sha256Hash.of(new byte[]{1})),
                storageInitializer,
                Helper.getZeroValueValueTxBuilderFromFedMember(),
                Helper.getRandomHeightProvider(10),
                stats,
                (environment, callResult) -> Assert.assertFalse((boolean) Bridge.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.decodeResult(callResult)[0]));
    }

    private ABIEncoder getABIEncoder(Sha256Hash blockHash) {

        return (int executionIndex) ->
                BridgeMethods.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.getFunction().encode(new Object[]{
                    blockHash.toBigInteger()
                });
    }

    private BridgeStorageProviderInitializer generateInitializerForTest(Sha256Hash blockHash){
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            witnessRoot = Sha256Hash.ZERO_HASH;
            CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessRoot);
            provider.setCoinbaseInformation(blockHash, coinbaseInformation);

            try{
                provider.save();
            } catch (IOException e) {
                throw new RuntimeException();
            }
        };
    }
}