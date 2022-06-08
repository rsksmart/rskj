package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.*;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.exception.VMException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Ignore
public class RegisterFastBridgeBtcTransactionTest extends BridgePerformanceTestCase {
    private BtcTransaction btcTx;
    private int blockWithTxHeight;
    private boolean shouldTransferToContract =  true;
    private BtcBlock blockWithTx;
    private PartialMerkleTree pmt;
    private Coin totalAmount;

    @BeforeClass
    public static void setupA() {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();
    }

    private static class AddressesBuilder {
        protected static Address userRefundAddress;
        protected static RskAddress lbcAddress;
        protected static Address lpBtcAddress;

        protected static void build() {
            BtcECKey btcECKeyUserRefundAddress = new BtcECKey();
            userRefundAddress = btcECKeyUserRefundAddress.toAddress(networkParameters);

            ECKey ecKey = new ECKey();
            lbcAddress = new RskAddress(ecKey.getAddress());

            BtcECKey btcECKeyLpBtcAddress = new BtcECKey();
            lpBtcAddress = btcECKeyLpBtcAddress.toAddress(networkParameters);
        }
    }

    @Test
    public void registerFastBridgeBtcTransaction() throws VMException {
        ExecutionStats stats = new ExecutionStats("registerFastBridgeBtcTransaction");
        registerFastBridgeBtcTransaction_success(5000, stats);
        registerFastBridgeBtcTransaction_surpasses_locking_cap(1000, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void registerFastBridgeBtcTransaction_success(int times, ExecutionStats stats) throws VMException {
        AddressesBuilder.build();

        totalAmount = Coin.COIN.multiply(Helper.randomInRange(1, 5));

        BridgeStorageProviderInitializer storageInitializer = generateInitializerForTest(
                1000,
                2000
        );

        executeAndAverage(
                "registerFastBridgeBtcTransaction_success",
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getTxBuilderWithInternalTransaction(AddressesBuilder.lbcAddress),
                Helper.getRandomHeightProvider(10),
                stats,
                (environment, executionResult) -> {
                    long totalAmount = new BigInteger(executionResult).longValueExact();
                    Assert.assertTrue(totalAmount > 0);
                }
        );
    }

    private void registerFastBridgeBtcTransaction_surpasses_locking_cap(int times, ExecutionStats stats) throws VMException {
        AddressesBuilder.build();

        totalAmount = Coin.CENT.multiply(Helper.randomInRange(10000000, 50000000));
        int surpassesLockinCapError = -200;

        BridgeStorageProviderInitializer storageInitializer = generateInitializerForTest(
                1000,
                2000
        );

        executeAndAverage(
                "registerFastBridgeBtcTransaction_success",
                times,
                getABIEncoder(),
                storageInitializer,
                Helper.getTxBuilderWithInternalTransaction(AddressesBuilder.lbcAddress),
                Helper.getRandomHeightProvider(10),
                stats,
                (environment, executionResult) -> {
                    long errorResult = new BigInteger(executionResult).longValueExact();
                    Assert.assertEquals(surpassesLockinCapError, errorResult);
                }
        );
    }

    private ABIEncoder getABIEncoder() {
        return (int executionIndex) ->
                BridgeMethods.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.getFunction().encode(new Object[]{
                        btcTx.bitcoinSerialize(),
                        blockWithTxHeight,
                        pmt.bitcoinSerialize(),
                        PegTestUtils.createHash3(1).getBytes(),
                        getBytesFromBtcAddress(AddressesBuilder.userRefundAddress), AddressesBuilder.lbcAddress.toHexString(),
                        getBytesFromBtcAddress(AddressesBuilder.lpBtcAddress),
                        shouldTransferToContract
                });
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
                BtcBlock lastBlock = Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);

                Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
                        bridgeConstants.getGenesisFederation().getRedeemScript(),
                        Sha256Hash.wrap(
                                getFastBridgeDerivationHash(
                                        PegTestUtils.createHash3(1),
                                        AddressesBuilder.userRefundAddress,
                                        AddressesBuilder.lpBtcAddress,
                                        AddressesBuilder.lbcAddress
                                ).getBytes()
                        )
                );

                Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);
                Address fastBridgeFederationAddress = Address.fromP2SHScript(bridgeConstants.getBtcParams(), fastBridgeP2SH);

                btcTx = createBtcTransactionWithOutputToAddress(totalAmount, fastBridgeFederationAddress);

                pmt = PartialMerkleTree.buildFromLeaves(networkParameters, new byte[]{(byte) 0xff}, Arrays.asList(btcTx.getHash()));
                List<Sha256Hash> hashes = new ArrayList<>();
                Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashes);

                blockWithTx = Helper.generateBtcBlock(lastBlock, Arrays.asList(btcTx), merkleRoot);
                btcBlockChain.add(blockWithTx);
                blockWithTxHeight = btcBlockChain.getBestChainHeight();

                Helper.generateAndAddBlocks(btcBlockChain, 10);
                thisRepository.commit();
        };
    }

    private BtcTransaction createBtcTransactionWithOutputToAddress(Coin amount, Address btcAddress) {
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(amount, btcAddress);
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1),
                0, ScriptBuilder.createInputScript(null, srcKey));
        return tx;
    }

    private Keccak256 getFastBridgeDerivationHash(
            Keccak256 derivationArgumentsHash,
            Address userRefundAddress,
            Address lpBtcAddress,
            RskAddress lbcAddress
    ) {
        byte[] fastBridgeDerivationHashData = derivationArgumentsHash.getBytes();
        byte[] userRefundAddressBytes = getBytesFromBtcAddress(userRefundAddress);
        byte[] lpBtcAddressBytes = getBytesFromBtcAddress(lpBtcAddress);
        byte[] lbcAddressBytes = lbcAddress.getBytes();
        byte[] result = new byte[fastBridgeDerivationHashData.length +
                userRefundAddressBytes.length + lpBtcAddressBytes.length + lbcAddressBytes.length];

        int dstPosition = 0;

        System.arraycopy(
                fastBridgeDerivationHashData,
                0,
                result,
                dstPosition,
                fastBridgeDerivationHashData.length
        );

        dstPosition += fastBridgeDerivationHashData.length;

        System.arraycopy(
                userRefundAddressBytes,
                0,
                result,
                dstPosition,
                userRefundAddressBytes.length
        );

        dstPosition += userRefundAddressBytes.length;

        System.arraycopy(
                lbcAddressBytes,
                0,
                result,
                dstPosition,
                lbcAddressBytes.length
        );

        dstPosition += lbcAddressBytes.length;

        System.arraycopy(
                lpBtcAddressBytes,
                0,
                result,
                dstPosition,
                lpBtcAddressBytes.length
        );
        return new Keccak256(HashUtil.keccak256(result));
    }

    private byte[] getBytesFromBtcAddress(Address btcAddress) {
        byte[] hash160 = btcAddress.getHash160();
        byte[] version = BigInteger.valueOf(btcAddress.getVersion()).toByteArray();
        byte[] btcAddressBytes = new byte[hash160.length + version.length];
        System.arraycopy(version, 0, btcAddressBytes, 0, version.length);
        System.arraycopy(hash160, 0, btcAddressBytes, version.length, hash160.length);

        return btcAddressBytes;
    }
}
