package co.rsk.peg;

import static co.rsk.peg.BridgeSupportTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbStorageProvider;
import co.rsk.peg.feeperkb.FeePerKbStorageProviderImpl;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.pegin.RejectedPeginReason;
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
    private PartialMerkleTree pmtWithTransactions;
    private int btcBlockWithPmtHeight;
    private RskAddress rskReceiver;
    private BridgeSupport bridgeSupport;
    private ArrayList<LogInfo> logs;
    private BtcBlockStoreWithCache btcBlockStoreWithCache;
    private BtcECKey btcPublicKey;
    private BtcLockSenderProvider btcLockSenderProvider;


    @BeforeEach
    void setUp() throws Exception{
        repository = BridgeSupportTestUtil.createRepository().startTracking();

        btcLockSenderProvider = new BtcLockSenderProvider();
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
        btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(repository, bridgeConstants, bridgeStorageProvider, activations);

        btcPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed("seed");
        ECKey ecKey = ECKey.fromPublicOnly(btcPublicKey.getPubKey());
        rskReceiver = new RskAddress(ecKey.getAddress());

        logs = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = new BridgeEventLoggerImpl(bridgeConstants, activations, logs);

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
        bridgeStorageProvider.save();
    }

    @Test
    void registerBtcTransaction_forALegacyBtcTransaction_shouldRegisterTheNewUtxoAndTransferTheRbtcBalance() throws Exception {
        // Act
        BtcTransaction bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue, btcPublicKey);
        setupChainWithBtcTransaction(bitcoinTransaction);
        bridgeStorageProvider.save();
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        Optional<Long> heightIfBtcTxHashIsAlreadyProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(bitcoinTransaction.getHash());
        assertTrue(heightIfBtcTxHashIsAlreadyProcessed.isPresent());
        assertEquals(RSK_EXECUTION_BLOCK_NUMBER, heightIfBtcTxHashIsAlreadyProcessed.get());

        int outputIndex = 0;
        TransactionOutput output = bitcoinTransaction.getOutput(outputIndex);
        List<UTXO> expectedFederationUtxos = List.of(utxoOf(bitcoinTransaction, output));
        assertEquals(expectedFederationUtxos, federationSupport.getActiveFederationBtcUTXOs());

        co.rsk.core.Coin expectedReceiverBalance = co.rsk.core.Coin.fromBitcoin(output.getValue());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        assertLogPegInBtc(bitcoinTransaction);
    }

    @Test
    void registerBtcTransaction_forARepeatedLegacyBtcTransaction_shouldNotPerformAnyChange() throws Exception {
        // Arrange
        BtcTransaction bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue, btcPublicKey);
        setupChainWithBtcTransaction(bitcoinTransaction);

        bridgeStorageProvider.save();
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = List.copyOf(federationSupport.getActiveFederationBtcUTXOs());

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));
    }

    @Test
    void registerBtcTransaction_whenLegacyBtcTransactionWithNegativeHeight_shouldNotPerformAnyChange() throws Exception {
        // Arrange
        BtcTransaction bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue, btcPublicKey);
        setupChainWithBtcTransaction(bitcoinTransaction);
        bridgeStorageProvider.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = List.copyOf(federationSupport.getActiveFederationBtcUTXOs());

        // Act
        int height = -1;
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), height, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));
    }

    @Test
    void registerBtcTransaction_whenLegacyBtcTransactionFromAMultiSig_shouldRefundTheFunds() throws Exception {
        // Arrange
        BtcTransaction bitcoinTransaction = createMultiSigPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue);
        setupChainWithBtcTransaction(bitcoinTransaction);
        bridgeSupport.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = List.copyOf(federationSupport.getActiveFederationBtcUTXOs());

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        Set<PegoutsWaitingForConfirmations.Entry> pegoutEntries = bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries();
        int expectedPegoutsWaitingForConfirmations = 1;
        assertEquals(expectedPegoutsWaitingForConfirmations, pegoutEntries.size());

        Optional<PegoutsWaitingForConfirmations.Entry> pegOutWaitingForConfirmationOptional = pegoutEntries.stream().findFirst();
        assertTrue(pegOutWaitingForConfirmationOptional.isPresent());

        PegoutsWaitingForConfirmations.Entry pegOutWaitingForConfirmationEntry = pegOutWaitingForConfirmationOptional.get();
        assertEquals(rskTx.getHash() ,pegOutWaitingForConfirmationEntry.getPegoutCreationRskTxHash());
        assertEquals(rskExecutionBlock.getNumber() ,pegOutWaitingForConfirmationEntry.getPegoutCreationRskBlockNumber());

        // Pegout value + fee == Pegin value
        BtcTransaction pegOut = pegOutWaitingForConfirmationEntry.getBtcTransaction();
        int outputIndex = 0;
        TransactionOutput pegOutOutput = pegOut.getOutput(outputIndex);
        Coin pegOutTotalValue = pegOutOutput.getValue().add(pegOut.getFee());
        assertEquals(minimumPeginValue, pegOutTotalValue);

        Address pegInTxSender = btcLockSenderProvider.tryGetBtcLockSender(bitcoinTransaction).get().getBTCAddress();
        Address pegoutReceiver = pegOutOutput.getAddressFromP2SH(btcNetworkParams);
        assertEquals(pegInTxSender, pegoutReceiver);

        assertRejectedPeginTransaction(bitcoinTransaction);
        assertReleaseBtcRequested(rskTx.getHash().getBytes(), pegOut, minimumPeginValue);
        assertPegoutTransactionCreated(pegOut.getHash(), UtxoUtils.extractOutpointValues(pegOut));

        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        Optional<Long> heightIfBtcTxHashIsAlreadyProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(bitcoinTransaction.getHash());
        assertTrue(heightIfBtcTxHashIsAlreadyProcessed.isPresent());
        assertEquals(RSK_EXECUTION_BLOCK_NUMBER, heightIfBtcTxHashIsAlreadyProcessed.get());
    }

    private static UTXO utxoOf(BtcTransaction bitcoinTransaction, TransactionOutput output) {
        int height = 0;
        return new UTXO(
            bitcoinTransaction.getHash(),
            output.getIndex(),
            output.getValue(),
            height,
            bitcoinTransaction.isCoinBase(),
            output.getScriptPubKey()
        );
    }

    private void setupChainWithBtcTransaction(BtcTransaction bitcoinTransaction) throws BlockStoreException {
        pmtWithTransactions = createValidPmtForTransactions(List.of(bitcoinTransaction.getHash()), btcNetworkParams);
        btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
    }

    private BtcTransaction createPegInTransaction(Address federationAddress, Coin coin, BtcECKey pubKey) {
        BtcTransaction btcTx = new BtcTransaction(btcNetworkParams);
        int outputIndex = 0;
        int nHash = 0;
        btcTx.addInput(BitcoinTestUtils.createHash(nHash), outputIndex, ScriptBuilder.createInputScript(null, pubKey));
        btcTx.addOutput(new TransactionOutput(btcNetworkParams, btcTx, coin, federationAddress));

        return btcTx;
    }

    private BtcTransaction createMultiSigPegInTransaction(Address federationAddress, Coin coin) {
        BtcTransaction btcTx = new BtcTransaction(btcNetworkParams);
        btcTx.addInput(
            BitcoinTestUtils.createHash(1),
            0,
            ScriptBuilder.createP2SHMultiSigInputScript(null, federationSupport.getActiveFederation().getRedeemScript())
        );
        btcTx.addOutput(new TransactionOutput(btcNetworkParams, btcTx, coin, federationAddress));

        return btcTx;
    }

    private void assertLogPegInBtc(BtcTransaction bitcoinTransaction) {
        CallTransaction.Function pegInBtcEvent = BridgeEvents.PEGIN_BTC.getEvent();
        Sha256Hash peginTransactionHash = bitcoinTransaction.getHash();

        List<DataWord> encodedTopics = getEncodedTopics(pegInBtcEvent, rskReceiver.toString(), peginTransactionHash.getBytes());

        int protocolVersion = 0;
        byte[] encodedData = getEncodedData(pegInBtcEvent, minimumPeginValue.getValue(), protocolVersion);

        assertEventWasEmittedWithExpectedTopics(encodedTopics, logs);
        assertEventWasEmittedWithExpectedData(encodedData, logs);
    }

    private void assertRejectedPeginTransaction(BtcTransaction bitcoinTransaction) {
        CallTransaction.Function rejectedPeginEvent = BridgeEvents.REJECTED_PEGIN.getEvent();
        Sha256Hash peginTransactionHash = bitcoinTransaction.getHash();
        List<DataWord> encodedTopics = getEncodedTopics(rejectedPeginEvent, peginTransactionHash.getBytes());
        byte[] encodedData = getEncodedData(rejectedPeginEvent, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER.getValue());

        assertEventWasEmittedWithExpectedTopics(encodedTopics, logs);
        assertEventWasEmittedWithExpectedData(encodedData, logs);
    }

    private void assertReleaseBtcRequested(byte[] rskTransactionHash, BtcTransaction pegoutTransaction, Coin amount) {
        CallTransaction.Function rejectedPeginEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();
        byte[] pegoutTransactionHash = pegoutTransaction.getHash().getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(rejectedPeginEvent, rskTransactionHash, pegoutTransactionHash);
        byte[] encodedData = getEncodedData(rejectedPeginEvent, amount.getValue());

        assertEventWasEmittedWithExpectedTopics(encodedTopics, logs);
        assertEventWasEmittedWithExpectedData(encodedData, logs);
    }

    private void assertPegoutTransactionCreated(Sha256Hash pegoutTransactionHash, List<Coin> outpointValues) {
        CallTransaction.Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(pegoutTransactionCreatedEvent, pegoutTransactionHash.getBytes());
        byte[] serializedOutpointValues = UtxoUtils.encodeOutpointValues(outpointValues);
        byte[] encodedData = getEncodedData(pegoutTransactionCreatedEvent, serializedOutpointValues);

        assertEventWasEmittedWithExpectedTopics(encodedTopics, logs);
        assertEventWasEmittedWithExpectedData(encodedData, logs);
    }
}
