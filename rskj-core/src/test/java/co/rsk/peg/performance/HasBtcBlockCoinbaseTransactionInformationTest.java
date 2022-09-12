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
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@Disabled
class HasBtcBlockCoinbaseTransactionInformationTest extends BridgePerformanceTestCase {

    private Sha256Hash witnessRoot;

    @BeforeAll
     static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    @Test
    void hasBtcBlockCoinbaseTransactionInformation() throws VMException {
        ExecutionStats stats = new ExecutionStats("hasBtcBlockCoinbaseTransactionInformation");
        hasBtcBlockCoinbaseTransactionInformation_success(5000, stats);
        hasBtcBlockCoinbaseTransactionInformation_unsuccess(5000, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void hasBtcBlockCoinbaseTransactionInformation_success(int times, ExecutionStats stats) throws VMException {
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
                (environment, callResult) -> Assertions.assertTrue((boolean) Bridge.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.decodeResult(callResult)[0]));
    }

    private void hasBtcBlockCoinbaseTransactionInformation_unsuccess(int times, ExecutionStats stats) throws VMException {
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
                (environment, callResult) -> Assertions.assertFalse((boolean) Bridge.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.decodeResult(callResult)[0]));
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
