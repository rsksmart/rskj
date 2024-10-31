package co.rsk.peg;

import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.PegUtils.getFlyoverRedeemScript;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.generateSignerEncodedSignatures;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.generateTransactionInputsSigHashes;
import static co.rsk.peg.bitcoin.BitcoinUtils.*;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.*;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.*;

public class BridgeSupportSvpTest {
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);
    private static final RskAddress bridgeContractAddress = PrecompiledContracts.BRIDGE_ADDR;
    private static final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainNetConstants.getBtcParams();
    private static final FederationConstants federationMainNetConstants = bridgeMainNetConstants.getFederationConstants();

    private static final Coin spendableValueFromProposedFederation = bridgeMainNetConstants.getSpendableValueFromProposedFederation();
    private static final Coin feePerKb = Coin.valueOf(1000L);
    private static final Keccak256 svpSpendTxCreationHash = RskTestUtils.createHash(1);

    private static final CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();
    private static final CallTransaction.Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();
    private static final CallTransaction.Function addSignatureEvent = BridgeEvents.ADD_SIGNATURE.getEvent();
    private static final CallTransaction.Function releaseBtcEvent = BridgeEvents.RELEASE_BTC.getEvent();
    private static final CallTransaction.Function commitFederationFailedEvent = BridgeEvents.COMMIT_FEDERATION_FAILED.getEvent();

    private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();

    private List<LogInfo> logs;
    private BridgeEventLogger bridgeEventLogger;

    private Block rskExecutionBlock;
    private Transaction rskTx;

    private Repository repository;
    private BridgeStorageProvider bridgeStorageProvider;

    private BridgeSupport bridgeSupport;
    private FederationSupport federationSupport;
    private FeePerKbSupport feePerKbSupport;

    private Federation activeFederation;
    private Federation proposedFederation;

    private Sha256Hash svpFundTransactionHashUnsigned;
    private BtcTransaction svpFundTransaction;
    private Sha256Hash svpSpendTransactionHashUnsigned;
    private BtcTransaction svpSpendTransaction;

    @BeforeEach
    void setUp() {
        long rskExecutionBlockNumber = 1000L;
        long rskExecutionBlockTimestamp = 10L;
        BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(rskExecutionBlockNumber)
            .setTimestamp(rskExecutionBlockTimestamp)
            .build();
        rskExecutionBlock = Block.createBlockFromHeader(blockHeader, true);

        Keccak256 rskTxHash = PegTestUtils.createHash3(1);
        rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(rskTxHash);

        activeFederation = FederationTestUtils.getErpFederation(federationMainNetConstants.getBtcParams());
        List<UTXO> activeFederationUTXOs = BitcoinTestUtils.createUTXOs(10, activeFederation.getAddress());
        proposedFederation = P2shErpFederationBuilder.builder().build();

        federationSupport = mock(FederationSupport.class);
        when(federationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(federationSupport.getActiveFederationAddress()).thenReturn(activeFederation.getAddress());
        when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(activeFederationUTXOs);
        when(federationSupport.getProposedFederation()).thenReturn(Optional.of(proposedFederation));

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKb);

        repository = createRepository();
        bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            bridgeContractAddress,
            btcMainnetParams,
            allActivations
        );

        logs = new ArrayList<>();
        bridgeEventLogger = new BridgeEventLoggerImpl(
            bridgeMainNetConstants,
            allActivations,
            logs
        );

        ECKey key = RskTestUtils.getEcKeyFromSeed("key");
        RskAddress address = new RskAddress(key.getAddress());
        when(rskTx.getSender(any())).thenReturn(address); // to not throw when logging update collections after calling it

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(bridgeStorageProvider)
            .withEventLogger(bridgeEventLogger)
            .withActivations(allActivations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withExecutionBlock(rskExecutionBlock)
            .build();
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("SVP failure tests")
    class SVPFailureTests {

        @BeforeEach
        void setUp() {
            arrangeExecutionBlockIsAfterValidationPeriodEnded();

            // this is needed to really check proposed federation was cleared
            InMemoryStorage storageAccessor = new InMemoryStorage();
            FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);
            federationStorageProvider.setProposedFederation(proposedFederation);

            federationSupport = new FederationSupportImpl(
                bridgeMainNetConstants.getFederationConstants(),
                federationStorageProvider,
                rskExecutionBlock,
                allActivations
            );

            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainNetConstants)
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .build();
        }

        @Test
        void updateCollections_whenSvpFundTxHashUnsigned_shouldLogValidationFailureAndClearValue() throws IOException {
            // arrange
            svpFundTransactionHashUnsigned = BitcoinTestUtils.createHash(1);
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTransactionHashUnsigned);

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertLogCommitFederationFailed();
            assertNoProposedFederation();
            assertNoSVPValues();
        }

        @Test
        void updateCollections_whenSvpFundTxSigned_shouldLogValidationFailureAndClearValue() throws IOException {
            // arrange
            svpFundTransaction = new BtcTransaction(btcMainnetParams);
            bridgeStorageProvider.setSvpFundTxSigned(svpFundTransaction);

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertLogCommitFederationFailed();
            assertNoProposedFederation();
            assertNoSVPValues();
        }

        @Test
        void updateCollections_whenSvpSpendTxWFS_shouldLogValidationFailureAndClearSpendTxValues() throws IOException {
            // arrange
            Keccak256 svpSpendTxCreationHash = RskTestUtils.createHash(1);
            svpSpendTransaction = new BtcTransaction(btcMainnetParams);
            Map.Entry<Keccak256, BtcTransaction> svpSpendTxWFS = new AbstractMap.SimpleEntry<>(svpSpendTxCreationHash, svpSpendTransaction);
            bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWFS);

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertLogCommitFederationFailed();
            assertNoProposedFederation();
            assertNoSVPValues();
        }

        @Test
        void updateCollections_whenSvpSpendTxHashUnsigned_shouldLogValidationFailureAndClearValue() throws IOException {
            // arrange
            svpSpendTransactionHashUnsigned = BitcoinTestUtils.createHash(2);
            bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTransactionHashUnsigned);

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertLogCommitFederationFailed();
            assertNoProposedFederation();
            assertNoSVPValues();
        }

        private void assertLogCommitFederationFailed() {
            List<DataWord> encodedTopics = getEncodedTopics(commitFederationFailedEvent);

            byte[] proposedFederationRedeemScriptSerialized = proposedFederation.getRedeemScript().getProgram();
            byte[] encodedData = getEncodedData(commitFederationFailedEvent, proposedFederationRedeemScriptSerialized, rskExecutionBlock.getNumber());

            assertEventWasEmittedWithExpectedTopics(encodedTopics);
            assertEventWasEmittedWithExpectedData(encodedData);
        }

        private void assertNoProposedFederation() {
            assertFalse(federationSupport.getProposedFederation().isPresent());
        }

        private void assertNoSVPValues() {
            assertNoSvpFundTxHashUnsigned();
            assertNoSvpFundTxSigned();
            assertNoSvpSpendTxWFS();
            assertNoSvpSpendTxHash();
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("Fund transaction creation and processing tests")
    class FundTxCreationAndProcessingTests {
        @Test
        void updateCollections_whenProposedFederationDoesNotExist_shouldNotCreateFundTransaction() throws IOException {
            // arrange
            when(federationSupport.getProposedFederation()).thenReturn(Optional.empty());

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertNoSvpFundTxHashUnsigned();
        }

        @Test
        void updateCollections_whenThereAreNoEnoughUTXOs_shouldNotCreateFundTransaction() throws IOException {
            // arrange
            List<UTXO> insufficientUtxos = new ArrayList<>();
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(insufficientUtxos);

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertNoSvpFundTxHashUnsigned();
        }

        @Test
        void updateCollections_whenFundTxCanBeCreated_createsExpectedFundTxAndSavesTheHashInStorageEntryAndPerformsPegoutActions() throws Exception {
            // act
            bridgeSupport.updateCollections(rskTx);
            bridgeStorageProvider.save(); // to save the tx sig hash

            // assert
            assertSvpFundTxHashUnsignedWasSavedInStorage();
            assertSvpFundTxReleaseWasSettled();
            assertSvpFundTransactionHasExpectedInputsAndOutputs();
        }

        private void assertSvpFundTransactionHasExpectedInputsAndOutputs() {
            assertInputsAreFromActiveFederation();

            List<TransactionOutput> svpFundTransactionUnsignedOutputs = svpFundTransaction.getOutputs();
            int svpFundTransactionUnsignedOutputsExpectedSize = 3;
            assertEquals(svpFundTransactionUnsignedOutputsExpectedSize, svpFundTransactionUnsignedOutputs.size());
            assertOneOutputIsChange(svpFundTransactionUnsignedOutputs);
            assertOneOutputIsToProposedFederationWithExpectedAmount(svpFundTransactionUnsignedOutputs);
            assertOneOutputIsToProposedFederationWithFlyoverPrefixWithExpectedAmount(svpFundTransactionUnsignedOutputs);
        }

        private void assertInputsAreFromActiveFederation() {
            Script activeFederationScriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(activeFederation);
            List<TransactionInput> inputs = svpFundTransaction.getInputs();

            for (TransactionInput input : inputs) {
                assertEquals(activeFederationScriptSig, input.getScriptSig());
            }
        }

        private void assertOneOutputIsChange(List<TransactionOutput> transactionOutputs) {
            Script activeFederationScriptPubKey = activeFederation.getP2SHScript();

            Optional<TransactionOutput> changeOutputOpt = searchForOutput(transactionOutputs, activeFederationScriptPubKey);
            assertTrue(changeOutputOpt.isPresent());
        }

        private void assertOneOutputIsToProposedFederationWithExpectedAmount(List<TransactionOutput> svpFundTransactionUnsignedOutputs) {
            Script proposedFederationScriptPubKey = proposedFederation.getP2SHScript();

            assertOutputWasSentToExpectedScriptWithExpectedAmount(svpFundTransactionUnsignedOutputs, proposedFederationScriptPubKey, spendableValueFromProposedFederation);
        }

        private void assertOneOutputIsToProposedFederationWithFlyoverPrefixWithExpectedAmount(List<TransactionOutput> svpFundTransactionUnsignedOutputs) {
            Script proposedFederationWithFlyoverPrefixScriptPubKey =
                PegUtils.getFlyoverScriptPubKey(bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederation.getRedeemScript());

            assertOutputWasSentToExpectedScriptWithExpectedAmount(svpFundTransactionUnsignedOutputs, proposedFederationWithFlyoverPrefixScriptPubKey, spendableValueFromProposedFederation);
        }
    }

    private void arrangeSvpFundTransactionUnsigned() {
        recreateSvpFundTransactionUnsigned();
        addOutputChange(svpFundTransaction);
        saveSvpFundTransactionUnsigned();
    }

    private void saveSvpFundTransactionUnsigned() {
        savePegoutIndex(svpFundTransaction);
        saveSvpFundTransactionHashUnsigned(svpFundTransaction.getHash());
    }

    private void savePegoutIndex(BtcTransaction pegout) {
        BitcoinUtils.getFirstInputSigHash(pegout)
            .ifPresent(inputSigHash -> bridgeStorageProvider.setPegoutTxSigHash(inputSigHash));
    }

    private void saveSvpFundTransactionHashUnsigned(Sha256Hash svpFundTransactionHashUnsigned) {
        bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTransactionHashUnsigned);
        bridgeSupport.save();
    }

    private void assertSvpFundTxHashUnsignedWasSavedInStorage() {
        Optional<Sha256Hash> svpFundTransactionHashUnsignedOpt = bridgeStorageProvider.getSvpFundTxHashUnsigned();
        assertTrue(svpFundTransactionHashUnsignedOpt.isPresent());

        svpFundTransactionHashUnsigned = svpFundTransactionHashUnsignedOpt.get();
    }

    private void assertSvpFundTxReleaseWasSettled() throws IOException {
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = bridgeStorageProvider.getPegoutsWaitingForConfirmations();
        assertPegoutWasAddedToPegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations, svpFundTransactionHashUnsigned, rskTx.getHash());

        svpFundTransaction = getSvpFundTransactionFromPegoutsMap(pegoutsWaitingForConfirmations);

        assertPegoutTxSigHashWasSaved(svpFundTransaction);
        assertLogReleaseRequested(rskTx.getHash(), svpFundTransactionHashUnsigned, spendableValueFromProposedFederation);
        assertLogPegoutTransactionCreated(svpFundTransaction);
    }

    private void assertPegoutWasAddedToPegoutsWaitingForConfirmations(PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations, Sha256Hash pegoutTransactionHash, Keccak256 releaseCreationTxHash) {
        Set<PegoutsWaitingForConfirmations.Entry> pegoutEntries = pegoutsWaitingForConfirmations.getEntries();
        Optional<PegoutsWaitingForConfirmations.Entry> pegoutEntry = pegoutEntries.stream()
            .filter(entry -> entry.getBtcTransaction().getHash().equals(pegoutTransactionHash) &&
                entry.getPegoutCreationRskBlockNumber().equals(rskExecutionBlock.getNumber()) &&
                entry.getPegoutCreationRskTxHash().equals(releaseCreationTxHash))
            .findFirst();
        assertTrue(pegoutEntry.isPresent());
    }

    private BtcTransaction getSvpFundTransactionFromPegoutsMap(PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations) {
        Set<PegoutsWaitingForConfirmations.Entry> pegoutEntries = pegoutsWaitingForConfirmations.getEntries();
        assertEquals(1, pegoutEntries.size());
        // we now know that the only present pegout is the fund tx
        Iterator<PegoutsWaitingForConfirmations.Entry> iterator = pegoutEntries.iterator();
        PegoutsWaitingForConfirmations.Entry pegoutEntry = iterator.next();

        return pegoutEntry.getBtcTransaction();
    }

    private void assertPegoutTxSigHashWasSaved(BtcTransaction pegoutTransaction) {
        Optional<Sha256Hash> pegoutTxSigHashOpt = BitcoinUtils.getFirstInputSigHash(pegoutTransaction);
        assertTrue(pegoutTxSigHashOpt.isPresent());

        Sha256Hash pegoutTxSigHash = pegoutTxSigHashOpt.get();
        assertTrue(bridgeStorageProvider.hasPegoutTxSigHash(pegoutTxSigHash));
    }

    private void assertSvpFundTransactionValuesWereNotUpdated() {
        assertTrue(bridgeStorageProvider.getSvpFundTxHashUnsigned().isPresent());
        assertNoSvpFundTxSigned();
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("Fund transaction registration tests")
    class FundTxRegistrationTests {
        private PartialMerkleTree pmtWithTransactions;
        private int btcBlockWithPmtHeight;

        @Test
        void registerBtcTransaction_forSvpFundTransactionChange_whenProposedFederationDoesNotExist_shouldRegisterTransactionButNotUpdateSvpFundTransactionValues() throws Exception {
            // arrange
            arrangeSvpFundTransactionUnsigned();

            signInputs(svpFundTransaction); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(svpFundTransaction);

            when(federationSupport.getProposedFederation()).thenReturn(Optional.empty());

            // act
            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpFundTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(svpFundTransaction.getHash());
            assertSvpFundTransactionValuesWereNotUpdated();
        }

        @Test
        void registerBtcTransaction_forSvpFundTransactionChange_whenValidationPeriodEnded_shouldRegisterTransactionButNotUpdateSvpFundTransactionValues() throws Exception {
            // arrange
            arrangeExecutionBlockIsAfterValidationPeriodEnded();

            arrangeSvpFundTransactionUnsigned();
            signInputs(svpFundTransaction); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(svpFundTransaction);

            // Act
            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpFundTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(svpFundTransaction.getHash());
            assertSvpFundTransactionValuesWereNotUpdated();
        }

        @Test
        void registerBtcTransaction_forNormalPegout_whenSvpPeriodIsOngoing_shouldRegisterTransactionButNotUpdateSvpFundTransactionValues() throws Exception {
            // Arrange
            arrangeSvpFundTransactionUnsigned();

            BtcTransaction pegout = createPegout(proposedFederation.getRedeemScript());
            savePegoutIndex(pegout);
            signInputs(pegout); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(pegout);

            // Act
            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
            bridgeSupport.registerBtcTransaction(
                rskTx,
                pegout.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(pegout.getHash());
            assertSvpFundTransactionValuesWereNotUpdated();
        }

        @Test
        void registerBtcTransaction_forSvpFundTransactionWithoutChangeOutput_whenSvpPeriodIsOngoing_shouldJustUpdateSvpFundTransactionValues() throws Exception {
            // Arrange
            recreateSvpFundTransactionUnsigned();
            saveSvpFundTransactionUnsigned();

            signInputs(svpFundTransaction); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(svpFundTransaction);

            // Act
            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpFundTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // Assert
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx);
            assertTransactionWasProcessed(svpFundTransaction.getHash());
            assertSvpFundTransactionValuesWereUpdated();
        }

        @Test
        void registerBtcTransaction_forSvpFundTransactionChange_whenSvpPeriodIsOngoing_shouldRegisterTransactionAndUpdateSvpFundTransactionValues() throws Exception {
            // Arrange
            arrangeSvpFundTransactionUnsigned();
            signInputs(svpFundTransaction); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(svpFundTransaction);

            // Act
            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpFundTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // Assert
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(svpFundTransaction.getHash());
            assertSvpFundTransactionValuesWereUpdated();
        }

        private void setUpForTransactionRegistration(BtcTransaction transaction) throws BlockStoreException {
            // recreate a valid chain that has the tx, so it passes the previous checks in registerBtcTransaction
            BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(btcMainnetParams, 100, 100);
            BtcBlockStoreWithCache btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(repository, bridgeMainNetConstants, bridgeStorageProvider, allActivations);

            pmtWithTransactions = createValidPmtForTransactions(Collections.singletonList(transaction.getHash()), btcMainnetParams);
            btcBlockWithPmtHeight = bridgeMainNetConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeMainNetConstants.getPegoutTxIndexGracePeriodInBtcBlocks(); // we want pegout tx index to be activated

            int chainHeight = btcBlockWithPmtHeight + bridgeMainNetConstants.getBtc2RskMinimumAcceptableConfirmations();
            recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcMainnetParams);

            bridgeStorageProvider.save();

            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainNetConstants)
                .withProvider(bridgeStorageProvider)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withRepository(repository)
                .build();
        }

        private void assertActiveFederationUtxosSize(int expectedActiveFederationUtxosSize) {
            assertEquals(expectedActiveFederationUtxosSize, federationSupport.getActiveFederationBtcUTXOs().size());
        }

        private void assertTransactionWasProcessed(Sha256Hash transactionHash) throws IOException {
            Optional<Long> rskBlockHeightAtWhichBtcTxWasProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(transactionHash);
            assertTrue(rskBlockHeightAtWhichBtcTxWasProcessed.isPresent());

            long rskExecutionBlockNumber = rskExecutionBlock.getNumber();
            assertEquals(rskExecutionBlockNumber, rskBlockHeightAtWhichBtcTxWasProcessed.get());
        }

        private void assertSvpFundTransactionValuesWereUpdated() {
            Optional<BtcTransaction> svpFundTransactionSignedOpt = bridgeStorageProvider.getSvpFundTxSigned();
            assertTrue(svpFundTransactionSignedOpt.isPresent());

            assertNoSvpFundTxHashUnsigned();
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("Spend transaction creation and processing tests")
    class SpendTxCreationAndProcessingTests {

        @Test
        void updateCollections_whenThereIsNoFundTxSigned_shouldNotCreateNorProcessSpendTx() throws IOException {
            // act
            bridgeSupport.updateCollections(rskTx);
            bridgeStorageProvider.save();

            // assert
            assertNoSvpSpendTxHash();
            assertNoSvpSpendTxWFS();
        }

        @Test
        void updateCollections_whenSpendTxCanBeCreated_createsExpectedSpendTxAndSavesTheValuesAndLogsExpectedEvents() throws IOException {
            // arrange
            arrangeSvpFundTransactionSigned();

            // act
            bridgeSupport.updateCollections(rskTx);
            bridgeStorageProvider.save();

            // assert
            svpSpendTransactionHashUnsigned = assertSvpSpendTxHashUnsignedWasSavedInStorage();
            svpSpendTransaction = assertSvpSpendTxIsWaitingForSignatures();
            assertSvpSpendTxHasExpectedInputsAndOutputs();

            assertSvpFundTxSignedWasRemovedFromStorage();

            assertLogPegoutTransactionCreated(svpSpendTransaction);
            TransactionOutput outputToActiveFed = svpSpendTransaction.getOutput(0);
            assertLogReleaseRequested(rskTx.getHash(), svpSpendTransactionHashUnsigned, outputToActiveFed.getValue());
        }

        private void assertSvpFundTxSignedWasRemovedFromStorage() {
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();
            assertFalse(svpFundTxSigned.isPresent());
        }

        private void assertSvpSpendTxHasExpectedInputsAndOutputs() {
            List<TransactionInput> inputs = svpSpendTransaction.getInputs();
            assertEquals(2, inputs.size());
            assertInputsHaveExpectedScriptSig(inputs);
            assertInputsOutpointHashIsFundTxHash(inputs, svpFundTransaction.getHash());

            List<TransactionOutput> outputs = svpSpendTransaction.getOutputs();
            assertEquals(1, outputs.size());

            long calculatedTransactionSize = 1762L; // using calculatePegoutTxSize method
            Coin expectedAmount = feePerKb
                .multiply(calculatedTransactionSize * 12L / 10L) // back up calculation
                .divide(1000);
            assertOutputWasSentToExpectedScriptWithExpectedAmount(outputs, activeFederation.getP2SHScript(), expectedAmount);
        }

        private void assertInputsHaveExpectedScriptSig(List<TransactionInput> inputs) {
            TransactionInput inputToProposedFederation = inputs.get(0);
            Script proposedFederationRedeemScript = proposedFederation.getRedeemScript();
            assertInputHasExpectedScriptSig(inputToProposedFederation, proposedFederationRedeemScript);

            TransactionInput inputToFlyoverProposedFederation = inputs.get(1);
            Script flyoverRedeemScript =
                getFlyoverRedeemScript(bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederationRedeemScript);
            assertInputHasExpectedScriptSig(inputToFlyoverProposedFederation, flyoverRedeemScript);
        }

        private void assertInputsOutpointHashIsFundTxHash(List<TransactionInput> inputs, Sha256Hash svpFundTxHashSigned) {
            for (TransactionInput input : inputs) {
                Sha256Hash outpointHash = input.getOutpoint().getHash();
                assertEquals(svpFundTxHashSigned, outpointHash);
            }
        }

        private void assertInputHasExpectedScriptSig(TransactionInput input, Script redeemScript) {
            List<ScriptChunk> scriptSigChunks = input.getScriptSig().getChunks();
            int redeemScriptChunkIndex = scriptSigChunks.size() - 1;

            assertArrayEquals(redeemScript.getProgram(), scriptSigChunks.get(redeemScriptChunkIndex).data); // last chunk should be the redeem script
            for (ScriptChunk chunk : scriptSigChunks.subList(0, redeemScriptChunkIndex)) { // all the other chunks should be zero
                assertEquals(0, chunk.opcode);
            }
        }
    }

    private Sha256Hash assertSvpSpendTxHashUnsignedWasSavedInStorage() {
        Optional<Sha256Hash> svpSpendTransactionHashUnsignedOpt = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
        assertTrue(svpSpendTransactionHashUnsignedOpt.isPresent());

        svpSpendTransactionHashUnsigned = svpSpendTransactionHashUnsignedOpt.get();
        return svpSpendTransactionHashUnsigned;
    }

    private BtcTransaction assertSvpSpendTxIsWaitingForSignatures() {
        Optional<Map.Entry<Keccak256, BtcTransaction>> svpSpendTxWaitingForSignaturesOpt = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
        assertTrue(svpSpendTxWaitingForSignaturesOpt.isPresent());
        Map.Entry<Keccak256, BtcTransaction> svpSpendTxWaitingForSignatures = svpSpendTxWaitingForSignaturesOpt.get();

        Keccak256 svpSpendTxWaitingForSignaturesRskTxHash = svpSpendTxWaitingForSignatures.getKey();
        assertEquals(rskTx.getHash(), svpSpendTxWaitingForSignaturesRskTxHash);

        BtcTransaction svpSpendTxWaitingForSignaturesSpendTx = svpSpendTxWaitingForSignatures.getValue();
        assertEquals(svpSpendTransactionHashUnsigned, svpSpendTxWaitingForSignaturesSpendTx.getHash());

        return svpSpendTxWaitingForSignaturesSpendTx;
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("Spend transaction signing tests")
    class SpendTxSigning {
        private final List<BtcECKey> proposedFederationSignersKeys =
            BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"member01", "member02", "member03", "member04", "member05"}, true); // this is needed to have the private keys too

        private final Keccak256 svpSpendTxCreationHash = RskTestUtils.createHash(1);

        private List<Sha256Hash> svpSpendTxSigHashes;

        @BeforeEach
        void setUp() {
            arrangeSvpSpendTransaction();
            svpSpendTxSigHashes = generateTransactionInputsSigHashes(svpSpendTransaction);
        }

        @Test
        void addSignature_forSvpSpendTx_withWrongKeys_shouldThrowIllegalStateExceptionAndNotAddProposedFederatorSignatures() {
            // arrange
            List<BtcECKey> wrongSigningKeys =
                BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"wrong01", "wrong02", "wrong03", "wrong04", "wrong05"}, true);

            // act & assert
            for (BtcECKey wrongSigningKey : wrongSigningKeys) {
                List<byte[]> signatures = generateSignerEncodedSignatures(wrongSigningKey, svpSpendTxSigHashes);
                assertThrows(IllegalStateException.class,
                    () -> bridgeSupport.addSignature(wrongSigningKey, signatures, svpSpendTxCreationHash));
            }

            // assert
            for (BtcECKey wrongSigningKey : wrongSigningKeys) {
                assertFederatorDidNotSignInputs(svpSpendTransaction.getInputs(), svpSpendTxSigHashes, wrongSigningKey);
            }

            assertAddSignatureWasNotLogged();
            assertSvpSpendTxWFSWasNotRemoved();
        }

        @Test
        void addSignature_forSvpSpendTx_whenProposedFederationDoesNotExist_shouldNotAddProposedFederatorSignatures() throws Exception {
            // arrange
            when(federationSupport.getProposedFederation()).thenReturn(Optional.empty());

            // act
            for (BtcECKey proposedFederatorSignerKey : proposedFederationSignersKeys) {
                List<byte[]> signatures = generateSignerEncodedSignatures(proposedFederatorSignerKey, svpSpendTxSigHashes);
                bridgeSupport.addSignature(proposedFederatorSignerKey, signatures, svpSpendTxCreationHash);
            }

            // assert
            for (BtcECKey key : proposedFederationSignersKeys) {
                assertFederatorDidNotSignInputs(svpSpendTransaction.getInputs(), svpSpendTxSigHashes, key);
            }

            assertAddSignatureWasNotLogged();
            assertSvpSpendTxWFSWasNotRemoved();
        }

        @Test
        void addSignature_forSvpSpendTx_whenValidationPeriodEnded_shouldNotAddProposedFederatorsSignatures() throws Exception {
            // arrange
            arrangeExecutionBlockIsAfterValidationPeriodEnded();
            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainNetConstants)
                .withProvider(bridgeStorageProvider)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withEventLogger(bridgeEventLogger)
                .withExecutionBlock(rskExecutionBlock)
                .build();

            // act
            for (BtcECKey proposedFederatorSignerKeys : proposedFederationSignersKeys) {
                List<byte[]> signatures = generateSignerEncodedSignatures(proposedFederatorSignerKeys, svpSpendTxSigHashes);
                bridgeSupport.addSignature(proposedFederatorSignerKeys, signatures, svpSpendTxCreationHash);
            }

            // assert
            for (BtcECKey proposedFederatorSignerKeys : proposedFederationSignersKeys) {
                assertFederatorDidNotSignInputs(svpSpendTransaction.getInputs(), svpSpendTxSigHashes, proposedFederatorSignerKeys);
            }

            assertAddSignatureWasNotLogged();
            assertSvpSpendTxWFSWasNotRemoved();
        }

        @Test
        void addSignature_forSvpSpendTx_withoutEnoughSignatures_shouldNotAddProposedFederatorsSignatures() throws Exception {
            // act
            for (BtcECKey proposedFederatorSignerKey : proposedFederationSignersKeys) {
                List<byte[]> signatures = generateSignerEncodedSignatures(proposedFederatorSignerKey, svpSpendTxSigHashes);
                List<byte[]> notEnoughSignatures = signatures.subList(0, signatures.size() - 1);
                bridgeSupport.addSignature(proposedFederatorSignerKey, notEnoughSignatures, svpSpendTxCreationHash);
            }

            // assert
            for (BtcECKey proposedFederatorSignerKeys : proposedFederationSignersKeys) {
                assertFederatorDidNotSignInputs(svpSpendTransaction.getInputs(), svpSpendTxSigHashes, proposedFederatorSignerKeys);
            }

            assertAddSignatureWasNotLogged();
            assertSvpSpendTxWFSWasNotRemoved();
        }

        private void assertFederatorDidNotSignInputs(List<TransactionInput> inputs, List<Sha256Hash> sigHashes, BtcECKey key) {
            for (TransactionInput input : inputs) {
                Sha256Hash sigHash = sigHashes.get(inputs.indexOf(input));
                assertFalse(BridgeUtils.isInputSignedByThisFederator(key, sigHash, input));
            }
        }

        private void assertAddSignatureWasNotLogged() {
            assertEquals(0, logs.size());
        }

        private void assertSvpSpendTxWFSWasNotRemoved() {
            assertTrue(bridgeStorageProvider.getSvpSpendTxWaitingForSignatures().isPresent());
        }

        @Test
        void addSignature_forSvpSpendTx_shouldAddProposedFederatorsSignatures() throws Exception {
            // act
            for (BtcECKey proposedFederatorSignerKey : proposedFederationSignersKeys) {
                List<byte[]> signatures = generateSignerEncodedSignatures(proposedFederatorSignerKey, svpSpendTxSigHashes);
                bridgeSupport.addSignature(proposedFederatorSignerKey, signatures, svpSpendTxCreationHash);
            }

            // assert
            for (BtcECKey proposedFederatorSignerKey : proposedFederationSignersKeys) {
                assertFederatorSigning(
                    svpSpendTxCreationHash.getBytes(),
                    svpSpendTransaction.getInputs(),
                    svpSpendTxSigHashes,
                    proposedFederation,
                    proposedFederatorSignerKey
                );
            }
            assertLogReleaseBtc(svpSpendTxCreationHash, svpSpendTransaction);
            assertLogsSize(proposedFederationSignersKeys.size() + 1); // proposedFedSigners size for addSignature, +1 for release btc
            assertNoSvpSpendTxWFS();
        }

        @Test
        void addSignature_forNormalPegout_whenSvpIsOngoing_shouldAddJustActiveFederatorsSignaturesToPegout() throws Exception {
            Keccak256 rskTxHash = RskTestUtils.createHash(2);

            BtcTransaction pegout = createPegout(activeFederation.getRedeemScript());
            SortedMap<Keccak256, BtcTransaction> pegoutsWFS = bridgeStorageProvider.getPegoutsWaitingForSignatures();
            pegoutsWFS.put(rskTxHash, pegout);

            List<BtcECKey> activeFedSignersKeys =
                BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"fa01", "fa02", "fa03", "fa04", "fa05"}, true);

            List<Sha256Hash> pegoutTxSigHashes = generateTransactionInputsSigHashes(pegout);

            // act
            for (BtcECKey activeFedSignerKey : activeFedSignersKeys) {
                List<byte[]> signatures = generateSignerEncodedSignatures(activeFedSignerKey, pegoutTxSigHashes);
                bridgeSupport.addSignature(activeFedSignerKey, signatures, rskTxHash);
            }

            // assert
            List<TransactionInput> pegoutInputs = pegout.getInputs();
            for (BtcECKey key : activeFedSignersKeys) {
                assertFederatorSigning(
                    rskTxHash.getBytes(),
                    pegout.getInputs(),
                    pegoutTxSigHashes,
                    activeFederation,
                    key
                );
            }

            assertLogReleaseBtc(rskTxHash, pegout);
            assertLogsSize(activeFedSignersKeys.size() + 1); // activeFedSignersKeys size for addSignature, +1 for release btc

            for (BtcECKey key : proposedFederationSignersKeys) {
                assertFederatorDidNotSignInputs(pegoutInputs, pegoutTxSigHashes, key);
            }
            assertSvpSpendTxWFSWasNotRemoved();
        }

        private void assertLogsSize(int expectedLogs) {
            assertEquals(expectedLogs, logs.size());
        }

        private void assertFederatorSigning(
            byte[] rskTxHashSerialized,
            List<TransactionInput> inputs,
            List<Sha256Hash> sigHashes,
            Federation federation,
            BtcECKey key
        ) {
            Optional<FederationMember> federationMember = federation.getMemberByBtcPublicKey(key);
            assertTrue(federationMember.isPresent());
            assertLogAddSignature(federationMember.get(), rskTxHashSerialized);
            assertFederatorSignedInputs(inputs, sigHashes, key);
        }

        private void assertLogAddSignature(FederationMember federationMember, byte[] rskTxHash) {
            ECKey federatorRskPublicKey = federationMember.getRskPublicKey();
            String federatorRskAddress = ByteUtil.toHexString(federatorRskPublicKey.getAddress());

            List<DataWord> encodedTopics = getEncodedTopics(addSignatureEvent, rskTxHash, federatorRskAddress);

            BtcECKey federatorBtcPublicKey = federationMember.getBtcPublicKey();
            byte[] encodedData = getEncodedData(addSignatureEvent, federatorBtcPublicKey.getPubKey());

            assertEventWasEmittedWithExpectedTopics(encodedTopics);
            assertEventWasEmittedWithExpectedData(encodedData);
        }

        private void assertLogReleaseBtc(Keccak256 rskTxHash, BtcTransaction btcTx) {
            byte[] rskTxHashSerialized = rskTxHash.getBytes();
            List<DataWord> encodedTopics = getEncodedTopics(releaseBtcEvent, rskTxHashSerialized);

            byte[] btcTxSerialized = btcTx.bitcoinSerialize();
            byte[] encodedData = getEncodedData(releaseBtcEvent, btcTxSerialized);

            assertEventWasEmittedWithExpectedTopics(encodedTopics);
            assertEventWasEmittedWithExpectedData(encodedData);
        }

        private void assertFederatorSignedInputs(List<TransactionInput> inputs, List<Sha256Hash> sigHashes, BtcECKey key) {
            for (TransactionInput input : inputs) {
                Sha256Hash sigHash = sigHashes.get(inputs.indexOf(input));
                assertTrue(BridgeUtils.isInputSignedByThisFederator(key, sigHash, input));
            }
        }
    }

    private void arrangeExecutionBlockIsAfterValidationPeriodEnded() {
        long validationPeriodEndBlock = proposedFederation.getCreationBlockNumber()
            + bridgeMainNetConstants.getFederationConstants().getValidationPeriodDurationInBlocks();
        long rskExecutionBlockTimestamp = 10L;

        rskExecutionBlock = createRskBlock(validationPeriodEndBlock, rskExecutionBlockTimestamp);
    }

    private void arrangeSvpFundTransactionSigned() {
        recreateSvpFundTransactionUnsigned();
        signInputs(svpFundTransaction);

        bridgeStorageProvider.setSvpFundTxSigned(svpFundTransaction);
        bridgeStorageProvider.save();
    }

    private void recreateSvpFundTransactionUnsigned() {
        svpFundTransaction = new BtcTransaction(btcMainnetParams);

        Sha256Hash parentTxHash = BitcoinTestUtils.createHash(1);
        addInput(svpFundTransaction, parentTxHash, proposedFederation.getRedeemScript());

        svpFundTransaction.addOutput(spendableValueFromProposedFederation, proposedFederation.getAddress());
        Address flyoverProposedFederationAddress =
            PegUtils.getFlyoverAddress(btcMainnetParams, bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederation.getRedeemScript());
        svpFundTransaction.addOutput(spendableValueFromProposedFederation, flyoverProposedFederationAddress);
    }

    private BtcTransaction createPegout(Script redeemScript) {
        BtcTransaction pegout = new BtcTransaction(btcMainnetParams);
        Sha256Hash parentTxHash = BitcoinTestUtils.createHash(2);
        addInput(pegout, parentTxHash, redeemScript);
        addOutputChange(pegout);

        return pegout;
    }

    private void addInput(BtcTransaction transaction, Sha256Hash parentTxHash, Script redeemScript) {
        // we need to add an input that we can actually sign
        transaction.addInput(
            parentTxHash,
            0,
            createBaseP2SHInputScriptThatSpendsFromRedeemScript(redeemScript)
        );
    }

    private void addOutputChange(BtcTransaction transaction) {
        // add output to the active fed
        Script activeFederationP2SHScript = activeFederation.getP2SHScript();
        transaction.addOutput(Coin.COIN.multiply(10), activeFederationP2SHScript);
    }

    private void signInputs(BtcTransaction transaction) {
        List<BtcECKey> keysToSign =
            BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"member01", "member02", "member03", "member04", "member05"}, true);
        List<TransactionInput> inputs = transaction.getInputs();
        IntStream.range(0, inputs.size()).forEach(i ->
            BitcoinTestUtils.signTransactionInputFromP2shMultiSig(transaction, i, keysToSign)
        );
    }

    private void arrangeSvpSpendTransaction() {
        recreateSvpSpendTransactionUnsigned();
        saveSvpSpendTransactionWFSValues();
    }

    private void recreateSvpSpendTransactionUnsigned() {
        svpSpendTransaction = new BtcTransaction(btcMainnetParams);
        svpSpendTransaction.setVersion(BTC_TX_VERSION_2);

        arrangeSvpFundTransactionSigned();
        // add inputs
        addInputFromMatchingOutputScript(svpSpendTransaction, svpFundTransaction, proposedFederation.getP2SHScript());
        Script proposedFederationRedeemScript = proposedFederation.getRedeemScript();
        svpSpendTransaction.getInput(0)
            .setScriptSig(createBaseP2SHInputScriptThatSpendsFromRedeemScript(proposedFederationRedeemScript));

        Script flyoverRedeemScript = getFlyoverRedeemScript(bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederationRedeemScript);
        addInputFromMatchingOutputScript(svpSpendTransaction, svpFundTransaction, ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript));
        svpSpendTransaction.getInput(1)
            .setScriptSig(createBaseP2SHInputScriptThatSpendsFromRedeemScript(flyoverRedeemScript));

        // add output
        svpSpendTransaction.addOutput(Coin.valueOf(1762), federationSupport.getActiveFederationAddress());
    }

    private void saveSvpSpendTransactionWFSValues() {
        Map.Entry<Keccak256, BtcTransaction> svpSpendTxWaitingForSignatures = new AbstractMap.SimpleEntry<>(svpSpendTxCreationHash, svpSpendTransaction);
        bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWaitingForSignatures);
        bridgeStorageProvider.save();
    }

    private void assertNoSvpFundTxHashUnsigned() {
        assertFalse(bridgeStorageProvider.getSvpFundTxHashUnsigned().isPresent());
    }

    private void assertNoSvpFundTxSigned() {
        assertFalse(bridgeStorageProvider.getSvpFundTxSigned().isPresent());
    }

    private void assertNoSvpSpendTxWFS() {
        assertFalse(bridgeStorageProvider.getSvpSpendTxWaitingForSignatures().isPresent());
    }

    private void assertNoSvpSpendTxHash() {
        assertFalse(bridgeStorageProvider.getSvpSpendTxHashUnsigned().isPresent());
    }

    private void assertOutputWasSentToExpectedScriptWithExpectedAmount(List<TransactionOutput> transactionOutputs, Script expectedScriptPubKey, Coin expectedAmount) {
        Optional<TransactionOutput> expectedOutput = searchForOutput(transactionOutputs, expectedScriptPubKey);
        assertTrue(expectedOutput.isPresent());

        TransactionOutput output = expectedOutput.get();
        assertEquals(expectedAmount, output.getValue());
    }

    private void assertLogReleaseRequested(Keccak256 releaseCreationTxHash, Sha256Hash pegoutTransactionHash, Coin requestedAmount) {
        byte[] releaseCreationTxHashSerialized = releaseCreationTxHash.getBytes();
        byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(releaseRequestedEvent, releaseCreationTxHashSerialized, pegoutTransactionHashSerialized);

        byte[] encodedData = getEncodedData(releaseRequestedEvent, requestedAmount.getValue());

        assertEventWasEmittedWithExpectedTopics(encodedTopics);
        assertEventWasEmittedWithExpectedData(encodedData);
    }

    private void assertLogPegoutTransactionCreated(BtcTransaction pegoutTransaction) {
        Sha256Hash pegoutTransactionHash = pegoutTransaction.getHash();
        byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(pegoutTransactionCreatedEvent, pegoutTransactionHashSerialized);

        List<Coin> outpointValues = extractOutpointValues(pegoutTransaction);
        byte[] serializedOutpointValues = UtxoUtils.encodeOutpointValues(outpointValues);
        byte[] encodedData = getEncodedData(pegoutTransactionCreatedEvent, serializedOutpointValues);

        assertEventWasEmittedWithExpectedTopics(encodedTopics);
        assertEventWasEmittedWithExpectedData(encodedData);
    }

    private List<DataWord> getEncodedTopics(CallTransaction.Function bridgeEvent, Object... args) {
        byte[][] encodedTopicsInBytes = bridgeEvent.encodeEventTopics(args);
        return LogInfo.byteArrayToList(encodedTopicsInBytes);
    }

    private byte[] getEncodedData(CallTransaction.Function bridgeEvent, Object... args) {
        return bridgeEvent.encodeEventData(args);
    }

    private void assertEventWasEmittedWithExpectedTopics(List<DataWord> expectedTopics) {
        Optional<LogInfo> topicOpt = logs.stream()
            .filter(log -> log.getTopics().equals(expectedTopics))
            .findFirst();
        assertTrue(topicOpt.isPresent());
    }

    private void assertEventWasEmittedWithExpectedData(byte[] expectedData) {
        Optional<LogInfo> data = logs.stream()
            .filter(log -> Arrays.equals(log.getData(), expectedData))
            .findFirst();
        assertTrue(data.isPresent());
    }
}
