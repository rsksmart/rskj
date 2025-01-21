package co.rsk.peg;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_0;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.pegin.RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER;
import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.btcLockSender.BtcLockSender;
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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

class RegisterBtcTransactionIT {
    public static final long RSK_EXECUTION_BLOCK_NUMBER = 1000L;
    public static final long RSK_EXECUTION_BLOCK_TIMESTAMP = 10L;
    private static int spendTxHashSeed = 0;
    private static int outputIndex = 0;
    private final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters btcNetworkParams = bridgeConstants.getBtcParams();
    private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
    private final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
    private final Transaction rskTx = TransactionUtils.createTransaction();
    private final Coin minimumPeginValue =  bridgeConstants.getMinimumPeginTxValue(activations);
    private final Block rskExecutionBlock = getRskExecutionBlock(RSK_EXECUTION_BLOCK_NUMBER, RSK_EXECUTION_BLOCK_TIMESTAMP);
    private final BtcECKey btcPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed("seed");
    private final Federation federation = P2shErpFederationBuilder.builder().build();
    private Repository repository;
    private FederationSupport federationSupport;
    private BridgeStorageProvider bridgeStorageProvider;
    private RskAddress rskReceiver;
    private BridgeSupport bridgeSupport;
    private ArrayList<LogInfo> logs;
    private BtcBlockStoreWithCache btcBlockStoreWithCache;
    private BtcLockSenderProvider btcLockSenderProvider;


    @BeforeEach
    void setUp() {
        repository = BridgeSupportTestUtil.createRepository().startTracking();

        btcLockSenderProvider = new BtcLockSenderProvider();
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);

        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(bridgeStorageAccessor);
        FeePerKbSupport feePerKbSupport = new FeePerKbSupportImpl(bridgeConstants.getFeePerKbConstants(), feePerKbStorageProvider);

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
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
        // Arrange
        BtcTransaction btcTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue, btcPublicKey, spendTxHashSeed, outputIndex);
        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash()), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeStorageProvider.save();

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertItWasProcessed(btcTransaction);

        int newOutputIndex = 0;
        TransactionOutput output = btcTransaction.getOutput(newOutputIndex);
        List<UTXO> expectedFederationUtxos = List.of(utxoOf(btcTransaction, output));
        assertEquals(expectedFederationUtxos, federationSupport.getActiveFederationBtcUTXOs());

        co.rsk.core.Coin expectedReceiverBalance = co.rsk.core.Coin.fromBitcoin(output.getValue());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        assertLogPegInBtc(btcTransaction, minimumPeginValue.getValue());
    }

    @Test
    void registerBtcTransaction_forMultipleLegacyBtcTransaction_shouldRegisterTheNewUtxosAndTransferTheRbtcBalance() throws Exception {
        // Arrange
        short numberOfOutputs = 3;
        BtcTransaction btcTransaction = createTransactionWithMultiplePegIns(federationSupport.getActiveFederation().getAddress(), btcPublicKey, minimumPeginValue, numberOfOutputs, outputIndex, spendTxHashSeed);
        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash()), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeStorageProvider.save();

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertItWasProcessed(btcTransaction);

        List<UTXO> expectedFederationUtxos = new ArrayList<>();
        for (short newOutputIndex = 0; newOutputIndex < numberOfOutputs; newOutputIndex++) {
            TransactionOutput output = btcTransaction.getOutput(newOutputIndex);
            expectedFederationUtxos.add(utxoOf(btcTransaction, output));
        }

        assertEquals(expectedFederationUtxos, federationSupport.getActiveFederationBtcUTXOs());

        co.rsk.core.Coin expectedReceiverBalance = co.rsk.core.Coin.ZERO;
        for (short newOutputIndex = 0; newOutputIndex < numberOfOutputs; newOutputIndex++) {
            TransactionOutput output = btcTransaction.getOutput(newOutputIndex);
            expectedReceiverBalance = expectedReceiverBalance.add(co.rsk.core.Coin.fromBitcoin(output.getValue()));
        }

        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));
        assertLogPegInBtc(btcTransaction, minimumPeginValue.getValue() * numberOfOutputs);
    }

    @Test
    void registerBtcTransaction_forMultipleLegacyBtcTransactionBelowMinimumWithSumAboveMinimum_shouldNotPerformAnyPegIn() throws Exception {
        // Arrange
        short numberOfOutputs = 2;
        Coin partOfMinimumValue = Coin.valueOf(minimumPeginValue.getValue() / numberOfOutputs);
        BtcTransaction btcTransaction = createTransactionWithMultiplePegIns(federationSupport.getActiveFederation().getAddress(), btcPublicKey, partOfMinimumValue, numberOfOutputs, outputIndex, spendTxHashSeed);
        assertTrue((partOfMinimumValue.getValue() * numberOfOutputs) >= minimumPeginValue.getValue());

        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash()), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeStorageProvider.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = List.copyOf(federationSupport.getActiveFederationBtcUTXOs());

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        assertRejectedPeginTransaction(btcTransaction, BridgeEvents.UNREFUNDABLE_PEGIN.getEvent(), RejectedPeginReason.INVALID_AMOUNT.getValue());
        Optional<Long> heightIfBtcTxHashIsAlreadyProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTransaction.getHash());
        assertFalse(heightIfBtcTxHashIsAlreadyProcessed.isPresent());
    }

    @Test
    void registerBtcTransaction_forARepeatedLegacyBtcTransaction_shouldNotPerformAnyChange() throws Exception {
        // Arrange
        BtcTransaction btcTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue, btcPublicKey, spendTxHashSeed, outputIndex);
        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash()), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);

        bridgeStorageProvider.save();
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = List.copyOf(federationSupport.getActiveFederationBtcUTXOs());

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));
    }

    @Test
    void registerBtcTransaction_whenLegacyBtcTransactionWithNegativeHeight_shouldNotPerformAnyChange() throws Exception {
        // Arrange
        BtcTransaction btcTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue, btcPublicKey, spendTxHashSeed, outputIndex);
        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash()), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeStorageProvider.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = List.copyOf(federationSupport.getActiveFederationBtcUTXOs());

        // Act
        int height = -1;
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), height, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));
    }

    @Test
    void registerBtcTransaction_whenLegacyBtcTransactionWithBalanceBelowMinimum_shouldNotRefundFunds() throws Exception {
        // Arrange
        Coin valueBelowMinimumPegin = minimumPeginValue.subtract(Coin.SATOSHI);
        BtcTransaction btcTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), valueBelowMinimumPegin, btcPublicKey, spendTxHashSeed, outputIndex);
        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash()), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeStorageProvider.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = List.copyOf(federationSupport.getActiveFederationBtcUTXOs());

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        assertRejectedPeginTransaction(btcTransaction, BridgeEvents.UNREFUNDABLE_PEGIN.getEvent(), RejectedPeginReason.INVALID_AMOUNT.getValue());
        Optional<Long> heightIfBtcTxHashIsAlreadyProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTransaction.getHash());
        assertFalse(heightIfBtcTxHashIsAlreadyProcessed.isPresent());
    }

    @Test
    void registerBtcTransaction_whenLegacyPeginBtcTransactionFromAMultiSig_shouldRefundTheFunds() throws Exception {
        // Arrange
        BtcTransaction btcTransaction = createMultiSigPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue, outputIndex, spendTxHashSeed);
        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash()), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeSupport.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = List.copyOf(federationSupport.getActiveFederationBtcUTXOs());

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        Set<PegoutsWaitingForConfirmations.Entry> pegoutEntries = bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries();
        int expectedPegoutsWaitingForConfirmations = 1;
        assertEquals(expectedPegoutsWaitingForConfirmations, pegoutEntries.size());

        Optional<PegoutsWaitingForConfirmations.Entry> pegOutWaitingForConfirmationOptional = pegoutEntries.stream().findFirst();
        assertTrue(pegOutWaitingForConfirmationOptional.isPresent());

        PegoutsWaitingForConfirmations.Entry pegOutWaitingForConfirmationEntry = pegOutWaitingForConfirmationOptional.get();
        assertEquals(rskTx.getHash(), pegOutWaitingForConfirmationEntry.getPegoutCreationRskTxHash());
        assertEquals(rskExecutionBlock.getNumber() ,pegOutWaitingForConfirmationEntry.getPegoutCreationRskBlockNumber());

        // Pegout value + fee == Pegin value
        BtcTransaction pegOut = pegOutWaitingForConfirmationEntry.getBtcTransaction();
        int newOutputIndex = 0;
        TransactionOutput pegOutOutput = pegOut.getOutput(newOutputIndex);
        Coin pegOutTotalValue = pegOutOutput.getValue().add(pegOut.getFee());
        assertEquals(minimumPeginValue, pegOutTotalValue);

        Address pegInTxSender = btcLockSenderProvider.tryGetBtcLockSender(btcTransaction).get().getBTCAddress();
        Address pegoutReceiver = pegOutOutput.getAddressFromP2SH(btcNetworkParams);
        assertEquals(pegInTxSender, pegoutReceiver);

        assertRejectedPeginTransaction(btcTransaction, BridgeEvents.REJECTED_PEGIN.getEvent(), RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER.getValue());
        assertReleaseBtcRequested(rskTx.getHash().getBytes(), pegOut, minimumPeginValue);
        assertPegoutTransactionCreated(pegOut.getHash(), UtxoUtils.extractOutpointValues(pegOut));

        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        assertItWasProcessed(btcTransaction);
    }

    @Test
    void registerBtcTransaction_forALegacyBtcTransactionWithMultipleInputs_shouldRegisterTheNewUtxoAndTransferTheRbtcBalanceToTheFirstInputAddress() throws Exception {
        // Arrange
        BtcTransaction btcTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue, btcPublicKey, outputIndex, spendTxHashSeed);

        List<BtcECKey> fedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"seed1", "seed2"}, true);
        for (BtcECKey fedKey : fedKeys) {
            spendTxHashSeed++;
            outputIndex++;
            btcTransaction.addInput(BitcoinTestUtils.createHash(spendTxHashSeed), outputIndex, ScriptBuilder.createInputScript(null, fedKey));
        }

        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash()), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeStorageProvider.save();

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertItWasProcessed(btcTransaction);

        int newOutputIndex = 0;
        TransactionOutput output = btcTransaction.getOutput(newOutputIndex);
        List<UTXO> expectedFederationUtxos = List.of(utxoOf(btcTransaction, output));
        assertEquals(expectedFederationUtxos, federationSupport.getActiveFederationBtcUTXOs());

        co.rsk.core.Coin expectedReceiverBalance = co.rsk.core.Coin.fromBitcoin(output.getValue());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        for (BtcECKey fedKey : fedKeys) {
            ECKey ecKey = ECKey.fromPublicOnly(fedKey.getPubKey());
            co.rsk.core.Coin expectedNonReceiverBalance = co.rsk.core.Coin.ZERO;
            assertEquals(expectedNonReceiverBalance, repository.getBalance(new RskAddress(ecKey.getAddress())));
        }

        assertLogPegInBtc(btcTransaction, minimumPeginValue.getValue());
    }

    @Test
    void registerBtcTransaction_whenLegacyPeginBtcTransactionFromUnknwonAddress_shouldNotRegisterNorRefund() throws Exception {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcNetworkParams);
        Script scriptForAnUnknownSender = new Script(new byte[]{});
        btcTransaction.addInput(BitcoinTestUtils.createHash(spendTxHashSeed), outputIndex, scriptForAnUnknownSender);
        btcTransaction.addOutput(minimumPeginValue, federationSupport.getActiveFederation().getAddress());

        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash()), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeSupport.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = List.copyOf(federationSupport.getActiveFederationBtcUTXOs());

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        Optional<Long> heightIfBtcTxHashIsAlreadyProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTransaction.getHash());
        assertFalse(heightIfBtcTxHashIsAlreadyProcessed.isPresent());

        assertRejectedPeginTransaction(btcTransaction, BridgeEvents.UNREFUNDABLE_PEGIN.getEvent(), LEGACY_PEGIN_UNDETERMINED_SENDER.getValue());
    }

    @Test
    void registerBtcTransaction_WhenLegacyPeginBtcTransactionFromP2WSHAddress_shouldRefundTheFunds() throws Exception {
        // Arrange
        // TODO(juli): At the moment rskj does not supports P2SHP2WSH we need to change this test.
        // The rawTx is obtained from the code on the bottom of this file when executed in bitcoinj-thin.
        String rawTx = "020000000001010100000000000000000000000000000000000000000000000000000000000000000000002322002051b06d" +
            "bcfc7138cb09fc31d5caa882dc3b9ad81f03a400474f81e7d8b130f4ebffffffff0120a107000000000017a914eba536f25415ec64caa23" +
            "28897dffb101b48743e870400483045022100c547bc3a0af10bd7ea4834a48336687dd386567b44abc5bc76d24f0cdf14e20a022006b2162" +
            "6a722c2ac9f5239755c37c79449187f3a790c614137e18dd63162cf75010025512102c8f031561c4758c9551cff47246f2c347189fe684c04" +
            "da35cf88e813f810e3c251ae00000000";
        BtcTransaction btcTransaction = new BtcTransaction(btcNetworkParams, Hex.decode(rawTx));

        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash(true)), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeSupport.save();

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = List.copyOf(federationSupport.getActiveFederationBtcUTXOs());

        // Assert
        assertItWasProcessed(btcTransaction);

        Set<PegoutsWaitingForConfirmations.Entry> pegoutEntries = bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries();
        int expectedPegoutsWaitingForConfirmations = 1;
        assertEquals(expectedPegoutsWaitingForConfirmations, pegoutEntries.size());

        Optional<PegoutsWaitingForConfirmations.Entry> pegOutWaitingForConfirmationOptional = pegoutEntries.stream().findFirst();
        assertTrue(pegOutWaitingForConfirmationOptional.isPresent());

        PegoutsWaitingForConfirmations.Entry pegOutWaitingForConfirmationEntry = pegOutWaitingForConfirmationOptional.get();
        assertEquals(rskTx.getHash(), pegOutWaitingForConfirmationEntry.getPegoutCreationRskTxHash());
        assertEquals(rskExecutionBlock.getNumber() ,pegOutWaitingForConfirmationEntry.getPegoutCreationRskBlockNumber());

        // Pegout value + fee == Pegin value
        BtcTransaction pegOut = pegOutWaitingForConfirmationEntry.getBtcTransaction();
        int newOutputIndex = 0;
        TransactionOutput pegOutOutput = pegOut.getOutput(newOutputIndex);
        Coin pegOutTotalValue = pegOutOutput.getValue().add(pegOut.getFee());
        assertEquals(minimumPeginValue, pegOutTotalValue);

        Optional<BtcLockSender> btcLockSender = btcLockSenderProvider.tryGetBtcLockSender(btcTransaction);
        assertTrue(btcLockSender.isPresent());

        Address pegInTxSender = btcLockSender.get().getBTCAddress();
        Address pegoutReceiver = pegOutOutput.getAddressFromP2SH(btcNetworkParams);
        assertEquals(pegInTxSender, pegoutReceiver);

        assertRejectedPeginTransaction(btcTransaction, BridgeEvents.REJECTED_PEGIN.getEvent(), RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER.getValue());
        assertReleaseBtcRequested(rskTx.getHash().getBytes(), pegOut, minimumPeginValue);
        assertPegoutTransactionCreated(pegOut.getHash(), UtxoUtils.extractOutpointValues(pegOut));

        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));
    }

    @Test
    void registerBtcTransaction_WhenLegacyPeginBtcTransactionFromP2WPKHAddress_shouldRegisterTheNewUtxoAndTransferTheRbtcBalance() throws Exception {
        // Arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcNetworkParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(spendTxHashSeed), outputIndex, new Script(new byte[]{}));
        btcTransaction.addOutput(minimumPeginValue, federationSupport.getActiveFederation().getAddress());

        Script redeemScript = ScriptBuilder.createInputScript(null, btcPublicKey);
        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
        Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
        Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();
        btcTransaction.getInput(0).setScriptSig(segwitScriptSig);

        TransactionWitness txWitness = TransactionWitness.createWitness(null, btcPublicKey);
        btcTransaction.setWitness(0, txWitness);

        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(List.of(btcTransaction.getHash(true)), btcNetworkParams);
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcNetworkParams);
        bridgeSupport.save();

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, btcTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertItWasProcessed(btcTransaction);

        int newOutputIndex = 0;
        TransactionOutput output = btcTransaction.getOutput(newOutputIndex);
        List<UTXO> expectedFederationUtxos = List.of(utxoOf(btcTransaction, output));
        assertEquals(expectedFederationUtxos, federationSupport.getActiveFederationBtcUTXOs());

        co.rsk.core.Coin expectedReceiverBalance = co.rsk.core.Coin.fromBitcoin(output.getValue());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        assertLogPegInBtc(btcTransaction, minimumPeginValue.getValue());
    }

    private static UTXO utxoOf(BtcTransaction btcTransaction, TransactionOutput output) {
        int height = 0;
        return new UTXO(
            btcTransaction.getHash(),
            output.getIndex(),
            output.getValue(),
            height,
            btcTransaction.isCoinBase(),
            output.getScriptPubKey()
        );
    }

    private BtcTransaction createPegInTransaction(Address federationAddress, Coin coin, BtcECKey pubKey, int spendTxHashSeed, int outputIndex) {
        BtcTransaction btcTx = new BtcTransaction(btcNetworkParams);
        btcTx.addInput(BitcoinTestUtils.createHash(spendTxHashSeed), outputIndex, ScriptBuilder.createInputScript(null, pubKey));
        btcTx.addOutput(coin, federationAddress);

        return btcTx;
    }

    private BtcTransaction createTransactionWithMultiplePegIns(Address federationAddress, BtcECKey pubKey, Coin value, short numberOfOutputs, int outputIndex, int spendTxHashSeed) {
        BtcTransaction btcTx = new BtcTransaction(btcNetworkParams);
        btcTx.addInput(BitcoinTestUtils.createHash(spendTxHashSeed), outputIndex, ScriptBuilder.createInputScript(null, pubKey));
        for (int i = 0; i < numberOfOutputs; i++) {
            btcTx.addOutput(value, federationAddress);
        }
        return btcTx;
    }

    private BtcTransaction createMultiSigPegInTransaction(Address federationAddress, Coin coin, int outputIndex, int spendTxHashSeed) {
        BtcTransaction btcTx = new BtcTransaction(btcNetworkParams);
        btcTx.addInput(
            BitcoinTestUtils.createHash(spendTxHashSeed),
            outputIndex,
            ScriptBuilder.createP2SHMultiSigInputScript(null, federationSupport.getActiveFederation().getRedeemScript())
        );
        btcTx.addOutput(coin, federationAddress);

        return btcTx;
    }

    private void assertItWasProcessed(BtcTransaction btcTransaction) throws IOException {
        Optional<Long> heightIfBtcTxHashIsAlreadyProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTransaction.getHash());
        assertTrue(heightIfBtcTxHashIsAlreadyProcessed.isPresent());
        assertEquals(RSK_EXECUTION_BLOCK_NUMBER, heightIfBtcTxHashIsAlreadyProcessed.get());
    }

    private void assertLogPegInBtc(BtcTransaction btcTransaction, long value) {
        CallTransaction.Function pegInBtcEvent = BridgeEvents.PEGIN_BTC.getEvent();
        Sha256Hash peginTransactionHash = btcTransaction.getHash();

        List<DataWord> encodedTopics = getEncodedTopics(pegInBtcEvent, rskReceiver.toString(), peginTransactionHash.getBytes());

        int protocolVersion = 0;
        byte[] encodedData = getEncodedData(pegInBtcEvent, value, protocolVersion);

        assertEventWasEmittedWithExpectedTopics(encodedTopics, logs);
        assertEventWasEmittedWithExpectedData(encodedData, logs);
    }

    private void assertRejectedPeginTransaction(BtcTransaction btcTransaction, CallTransaction.Function rejectionEvent, int rejectionReason) {
        Sha256Hash peginTransactionHash = btcTransaction.getHash();
        List<DataWord> encodedTopics = getEncodedTopics(rejectionEvent, peginTransactionHash.getBytes());
        byte[] encodedData = getEncodedData(rejectionEvent, rejectionReason);

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


/*
    public void obtainTheHexCodeForTheP2WSH() {
        NetworkParameters btcNetworkParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        Address receiver = Address.fromP2SHHash(btcNetworkParams, HEX.decode("eba536f25415ec64caa2328897dffb101b48743e"));
        Coin value = Coin.valueOf(500_000);

        BtcTransaction btcTx = new BtcTransaction(btcNetworkParams);
        Sha256Hash spendTxHash = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");
        btcTx.addInput(spendTxHash, 0, new Script(new byte[]{}));
        btcTx.addOutput(value, receiver);
        btcTx.setVersion(2);

        // Multisig 1-of-1
        // data comes from BitcoinTestUtils.getBtcEcKeyFromSeed("seed") in rskj
        BtcECKey btcPublicKey = BtcECKey.fromPublicOnly(Hex.decode("03ebc18a8b308fdd3efeae515861cb4b553d854ff6a40ce7bf1c3ced2524dc80ad"));
        Script redeemScript = ScriptBuilder.createRedeemScript(1, Collections.singletonList(btcPublicKey));

        Sha256Hash sigHash = btcTx.hashForWitnessSignature(
            0, // Input index
            redeemScript,
            value,
            BtcTransaction.SigHash.ALL,
            false
        );
        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript.getProgram());
        Script scriptSig = new ScriptBuilder().number(OP_0).data(redeemScriptHash).build();
        Script segwitScriptSig = new ScriptBuilder().data(scriptSig.getProgram()).build();
        btcTx.getInput(0).setScriptSig(segwitScriptSig);

        BtcECKey.ECDSASignature signature = btcPublicKey.sign(sigHash);
        TransactionSignature txSignature = new TransactionSignature(
            signature,
            BtcTransaction.SigHash.ALL,
            false
        );
        List<TransactionSignature> signatures = Collections.singletonList(txSignature);
        TransactionWitness txWitness = TransactionWitness.createWitnessErpStandardScript(redeemScript, signatures);
        btcTx.setWitness(0, txWitness);

        System.out.println(Hex.toHexString(btcTx.bitcoinSerialize()));
    }
 */
