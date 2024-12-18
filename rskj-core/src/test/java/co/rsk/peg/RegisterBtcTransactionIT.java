package co.rsk.peg;

import static co.rsk.peg.BridgeSupportTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbStorageProvider;
import co.rsk.peg.feeperkb.FeePerKbStorageProviderImpl;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;

class RegisterBtcTransactionIT {
    public static final long RSK_EXECUTION_BLOCK_NUMBER = 1000L;
    public static final long RSK_EXECUTION_BLOCK_TIMESTAMP = 10L;
    private final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters btcNetworkParams = bridgeConstants.getBtcParams();
    private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
    private final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
    private final Transaction rskTx = TransactionUtils.createTransaction();
    private final Coin minimumPeginValue =  bridgeConstants.getMinimumPeginTxValue(activations);
    private final Block rskExecutionBlock = getRskExecutionBlock(RSK_EXECUTION_BLOCK_NUMBER, RSK_EXECUTION_BLOCK_TIMESTAMP);
    private Repository repository;
    private FederationSupport federationSupport;
    private BridgeStorageProvider bridgeStorageProvider;
    private BtcTransaction bitcoinTransaction;
    private PartialMerkleTree pmtWithTransactions;
    private int btcBlockWithPmtHeight;
    private RskAddress rskReceiver;
    private BridgeSupport bridgeSupport;
    private ArrayList<LogInfo> logs;


    @BeforeEach
    void setUp() throws Exception{
        repository = BridgeSupportTestUtil.createRepository().startTracking();

        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);

        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(bridgeStorageAccessor);
        FeePerKbSupport feePerKbSupport = new FeePerKbSupportImpl(bridgeConstants.getFeePerKbConstants(), feePerKbStorageProvider);

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        Federation federation = P2shErpFederationBuilder.builder().build();
        federationStorageProvider.setNewFederation(federation);
        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        bridgeStorageProvider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, btcNetworkParams, activations);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(btcNetworkParams, 100, 100);
        BtcBlockStoreWithCache btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(repository, bridgeConstants, bridgeStorageProvider, activations);

        BtcECKey btcPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed("seed");
        ECKey ecKey = ECKey.fromPublicOnly(btcPublicKey.getPubKey());
        rskReceiver = new RskAddress(ecKey.getAddress());
        bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue, btcPublicKey);

        pmtWithTransactions = createValidPmtForTransactions(List.of(bitcoinTransaction.getHash()), btcNetworkParams);
        btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();

        logs = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = new BridgeEventLoggerImpl(bridgeConstants, activations, logs);

        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeStorageProvider.save();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(bridgeStorageProvider)
            .withActivations(activations)
            .withEventLogger(bridgeEventLogger)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withExecutionBlock(rskExecutionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .build();
    }

    @Test
    void registerBtcTransaction_forALegacyBtcTransaction_shouldRegisterTheNewUtxoAndTransferTheRbtcBalance() throws Exception {
        // Act
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        Optional<Long> heightIfBtcTxHashIsAlreadyProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(bitcoinTransaction.getHash());
        assertTrue(heightIfBtcTxHashIsAlreadyProcessed.isPresent());
        assertEquals(RSK_EXECUTION_BLOCK_NUMBER, heightIfBtcTxHashIsAlreadyProcessed.get());

        int outputIndex = 0;
        TransactionOutput output = bitcoinTransaction.getOutput(outputIndex);
        List<UTXO> expectedFederationUtxos = Collections.singletonList(utxoOf(bitcoinTransaction, output));
        assertEquals(expectedFederationUtxos, federationSupport.getActiveFederationBtcUTXOs());

        co.rsk.core.Coin expectedReceiverBalance = co.rsk.core.Coin.fromBitcoin(output.getValue());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        assertLogPegInBtc();
    }

    @Test
    void registerBtcTransaction_forARepeatedLegacyBtcTransaction_shouldNotPerformAnyChange() throws Exception {
        // Arrange
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = federationSupport.getActiveFederationBtcUTXOs();

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));
    }

    private static UTXO utxoOf(BtcTransaction bitcoinTransaction, TransactionOutput output) {
        return new UTXO(
            bitcoinTransaction.getHash(),
            output.getIndex(),
            output.getValue(),
            0,
            bitcoinTransaction.isCoinBase(),
            output.getScriptPubKey()
        );
    }

    private BtcTransaction createPegInTransaction(Address federationAddress, Coin coin, BtcECKey pubKey) {
        BtcTransaction btcTx = new BtcTransaction(btcNetworkParams);
        btcTx.addInput(BitcoinTestUtils.createHash(0), 0, ScriptBuilder.createInputScript(null, pubKey));
        btcTx.addOutput(new TransactionOutput(btcNetworkParams, btcTx, coin, federationAddress));

        return btcTx;
    }

    private void assertLogPegInBtc() {
        Sha256Hash peginTransactionHash = bitcoinTransaction.getHash();
        List<DataWord> encodedTopics = getEncodedTopics(BridgeEvents.PEGIN_BTC.getEvent(), rskReceiver.toString(), peginTransactionHash.getBytes());
        byte[] encodedData = getEncodedData(BridgeEvents.PEGIN_BTC.getEvent(), minimumPeginValue.getValue(), 0);

        assertEventWasEmittedWithExpectedTopics(encodedTopics, logs);
        assertEventWasEmittedWithExpectedData(encodedData, logs);
    }
}
