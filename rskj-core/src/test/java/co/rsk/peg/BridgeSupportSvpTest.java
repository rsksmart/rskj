package co.rsk.peg;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.PegUtils.getFlyoverRedeemScript;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.generateSignerEncodedSignatures;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.generateTransactionInputsSigHashes;
import static co.rsk.peg.bitcoin.BitcoinUtils.*;
import static co.rsk.peg.bitcoin.BitcoinUtils.addInputFromMatchingOutputScript;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BridgeSupportSvpTest {
    private static final RskAddress bridgeContractAddress = PrecompiledContracts.BRIDGE_ADDR;
    private final CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();
    private final CallTransaction.Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();
    private final CallTransaction.Function addSignatureEvent = BridgeEvents.ADD_SIGNATURE.getEvent();
    private final CallTransaction.Function releaseBtcEvent = BridgeEvents.RELEASE_BTC.getEvent();
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);

    private static final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainNetConstants.getBtcParams();
    private static final FederationConstants federationMainNetConstants = bridgeMainNetConstants.getFederationConstants();

    private static final Coin feePerKb = Coin.valueOf(1000L);

    private static final Coin spendableValueFromProposedFederation = bridgeMainNetConstants.getSpendableValueFromProposedFederation();

    private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();

    private Block rskExecutionBlock;
    private Transaction rskTx;

    private Repository repository;
    private BridgeStorageProvider bridgeStorageProvider;

    private BridgeSupport bridgeSupport;
    private FederationSupport federationSupport;
    private FeePerKbSupport feePerKbSupport;

    private Federation activeFederation;
    private Federation proposedFederation;

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
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("svp fund transaction creation and processing tests")
    class SvpFundTxCreationAndProcessingTests {
        private List<LogInfo> logs;

        private BtcTransaction svpFundTransactionUnsigned;
        private Sha256Hash svpFundTransactionHashUnsigned;

        @BeforeEach
        void setUp() {
            logs = new ArrayList<>();
            BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeMainNetConstants,
                allActivations,
                logs
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
        void processSvpFundTransactionWithoutSignatures_whenProposedFederationDoesNotExist_throwsIllegalStateException() {
            // arrange
            when(federationSupport.getProposedFederation()).thenReturn(Optional.empty());

            // act & assert
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> bridgeSupport.processSvpFundTransactionUnsigned(rskTx));

            String expectedMessage = "Proposed federation should be present when processing SVP fund transaction.";
            assertEquals(expectedMessage, exception.getMessage());
        }

        @Test
        void processSvpFundTransactionWithoutSignatures_whenThereAreNoEnoughUTXOs_throwsInsufficientMoneyException() {
            // arrange
            List<UTXO> insufficientUtxos = new ArrayList<>();
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(insufficientUtxos);

            // act & assert
            InsufficientMoneyException exception = assertThrows(InsufficientMoneyException.class,
                () -> bridgeSupport.processSvpFundTransactionUnsigned(rskTx));

            String totalNeededValueInBtc = spendableValueFromProposedFederation.multiply(2).toFriendlyString();
            String expectedMessage = String.format("Insufficient money,  missing %s", totalNeededValueInBtc);
            assertEquals(expectedMessage, exception.getMessage());
        }

        @Test
        void processSvpFundTransactionWithoutSignatures_createsExpectedTransactionAndSavesTheHashInStorageEntryAndPerformsPegoutActions() throws Exception {
            // act
            bridgeSupport.processSvpFundTransactionUnsigned(rskTx);
            bridgeStorageProvider.save(); // to save the tx sig hash

            // assert
            assertSvpFundTxHashUnsignedWasSavedInStorage();
            assertSvpReleaseWasSettled();
            assertSvpFundTransactionHasExpectedInputsAndOutputs();
        }

        private void assertSvpFundTxHashUnsignedWasSavedInStorage() {
            Optional<Sha256Hash> svpFundTransactionHashUnsignedOpt = bridgeStorageProvider.getSvpFundTxHashUnsigned();
            assertTrue(svpFundTransactionHashUnsignedOpt.isPresent());

            svpFundTransactionHashUnsigned = svpFundTransactionHashUnsignedOpt.get();
        }

        private void assertSvpReleaseWasSettled() throws IOException {
            PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = bridgeStorageProvider.getPegoutsWaitingForConfirmations();
            assertPegoutWasAddedToPegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations, svpFundTransactionHashUnsigned, rskTx.getHash());

            svpFundTransactionUnsigned = getSvpFundTransactionFromPegoutsMap(pegoutsWaitingForConfirmations);

            assertPegoutTxSigHashWasSaved(svpFundTransactionUnsigned);
            assertLogReleaseRequested(logs, rskTx.getHash(), svpFundTransactionHashUnsigned, spendableValueFromProposedFederation);
            assertLogPegoutTransactionCreated(logs, svpFundTransactionUnsigned);
        }

        private BtcTransaction getSvpFundTransactionFromPegoutsMap(PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations) {
            Set<PegoutsWaitingForConfirmations.Entry> pegoutEntries = pegoutsWaitingForConfirmations.getEntries();
            assertEquals(1, pegoutEntries.size());
            // we now know that the only present pegout is the fund tx
            Iterator<PegoutsWaitingForConfirmations.Entry> iterator = pegoutEntries.iterator();
            PegoutsWaitingForConfirmations.Entry pegoutEntry = iterator.next();

            return pegoutEntry.getBtcTransaction();
        }

        private void assertSvpFundTransactionHasExpectedInputsAndOutputs() {
            assertInputsAreFromActiveFederation();

            List<TransactionOutput> svpFundTransactionUnsignedOutputs = svpFundTransactionUnsigned.getOutputs();
            int svpFundTransactionUnsignedOutputsExpectedSize = 3;
            assertEquals(svpFundTransactionUnsignedOutputsExpectedSize, svpFundTransactionUnsignedOutputs.size());
            assertOneOutputIsChange(svpFundTransactionUnsignedOutputs);
            assertOneOutputIsToProposedFederationWithExpectedAmount(svpFundTransactionUnsignedOutputs);
            assertOneOutputIsToProposedFederationWithFlyoverPrefixWithExpectedAmount(svpFundTransactionUnsignedOutputs);
        }

        private void assertInputsAreFromActiveFederation() {
            Script activeFederationScriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(activeFederation);
            List<TransactionInput> inputs = svpFundTransactionUnsigned.getInputs();

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

        private void assertPegoutWasAddedToPegoutsWaitingForConfirmations(PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations, Sha256Hash pegoutTransactionHash, Keccak256 releaseCreationTxHash) {
            Set<PegoutsWaitingForConfirmations.Entry> pegoutEntries = pegoutsWaitingForConfirmations.getEntries();
            Optional<PegoutsWaitingForConfirmations.Entry> pegoutEntry = pegoutEntries.stream()
                .filter(entry -> entry.getBtcTransaction().getHash().equals(pegoutTransactionHash) &&
                    entry.getPegoutCreationRskBlockNumber().equals(rskExecutionBlock.getNumber()) &&
                    entry.getPegoutCreationRskTxHash().equals(releaseCreationTxHash))
                .findFirst();
            assertTrue(pegoutEntry.isPresent());
        }

        private void assertPegoutTxSigHashWasSaved(BtcTransaction pegoutTransaction) {
            Optional<Sha256Hash> pegoutTxSigHashOpt = BitcoinUtils.getFirstInputSigHash(pegoutTransaction);
            assertTrue(pegoutTxSigHashOpt.isPresent());

            Sha256Hash pegoutTxSigHash = pegoutTxSigHashOpt.get();
            assertTrue(bridgeStorageProvider.hasPegoutTxSigHash(pegoutTxSigHash));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("svp fund transaction registration tests")
    class SvpFundTxRegistrationTests {
        private PartialMerkleTree pmtWithTransactions;
        private int btcBlockWithPmtHeight;

        @BeforeEach
        void setUp() {
            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainNetConstants)
                .withProvider(bridgeStorageProvider)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .build();
        }

        @Test
        void registerBtcTransaction_forSvpFundTransactionChange_whenProposedFederationDoesNotExist_shouldRegisterTransactionButNotUpdateSvpFundTransactionValues() throws Exception {
            // arrange
            BtcTransaction svpFundTransaction = arrangeSvpFundTransactionUnsignedWithChange();

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

        private void assertSvpFundTransactionValuesWereNotUpdated() {
            assertTrue(bridgeStorageProvider.getSvpFundTxHashUnsigned().isPresent());
            assertFalse(bridgeStorageProvider.getSvpFundTxSigned().isPresent());
        }

        @Test
        void registerBtcTransaction_forSvpFundTransactionChange_whenValidationPeriodEnded_shouldRegisterTransactionButNotUpdateSvpFundTransactionValues() throws Exception {
            // arrange
            arrangeExecutionBlockIsAfterValidationPeriodEnded();

            BtcTransaction svpFundTransaction = arrangeSvpFundTransactionUnsignedWithChange();
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
            arrangeSvpFundTransactionUnsignedWithChange();

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
            BtcTransaction svpFundTransaction = recreateSvpFundTransactionUnsigned();
            saveSvpFundTransactionUnsigned(svpFundTransaction);

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
            BtcTransaction svpFundTransaction = arrangeSvpFundTransactionUnsignedWithChange();

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

        private BtcTransaction arrangeSvpFundTransactionUnsignedWithChange() {
            BtcTransaction svpFundTransaction = recreateSvpFundTransactionUnsigned();
            addOutputChange(svpFundTransaction);
            saveSvpFundTransactionUnsigned(svpFundTransaction);

            return svpFundTransaction;
        }

        private void saveSvpFundTransactionUnsigned(BtcTransaction svpFundTransaction) {
            savePegoutIndex(svpFundTransaction);
            saveSvpFundTransactionHashUnsigned(svpFundTransaction.getHash());
        }

        private void saveSvpFundTransactionHashUnsigned(Sha256Hash svpFundTransactionHashUnsigned) {
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTransactionHashUnsigned);
            bridgeSupport.save();
        }

        private void savePegoutIndex(BtcTransaction pegout) {
            BitcoinUtils.getFirstInputSigHash(pegout)
                .ifPresent(inputSigHash -> bridgeStorageProvider.setPegoutTxSigHash(inputSigHash));
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

            assertFalse(bridgeStorageProvider.getSvpFundTxHashUnsigned().isPresent());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("svp spend transaction creation and processing tests")
    class SvpSpendTxCreationAndProcessingTests {
        private List<LogInfo> logs;
        private BtcTransaction svpFundTransaction;
        private Sha256Hash svpSpendTransactionHashUnsigned;
        private BtcTransaction svpSpendTransactionUnsigned;

        @BeforeEach
        void setUp() {
            logs = new ArrayList<>();
            BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeMainNetConstants,
                allActivations,
                logs
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
        void processSvpSpendTransaction_whenThereIsNoProposedFederation_shouldNotCreateNorProcessSpendTx() {
            // act
            when(federationSupport.getProposedFederation()).thenReturn(Optional.empty());
            bridgeSupport.processSvpSpendTransactionUnsigned(rskTx);
            bridgeStorageProvider.save();

            // assert
            assertSvpSpendTransactionValuesWereNotSaved();
            assertSvpSpendTransactionReleaseWasNotLogged();
        }

        @Test
        void processSvpSpendTransaction_whenThereIsNoFundTxSigned_shouldNotCreateNorProcessSpendTx() {
            // act
            bridgeSupport.processSvpSpendTransactionUnsigned(rskTx);
            bridgeStorageProvider.save();

            // assert
            assertSvpSpendTransactionValuesWereNotSaved();
            assertSvpSpendTransactionReleaseWasNotLogged();
        }

        private void assertSvpSpendTransactionValuesWereNotSaved() {
            Optional<Sha256Hash> svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
            assertFalse(svpSpendTxHashUnsigned.isPresent());

            Optional<Map.Entry<Keccak256, BtcTransaction>> svpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
            assertFalse(svpSpendTxWaitingForSignatures.isPresent());
        }

        private void assertSvpSpendTransactionReleaseWasNotLogged() {
            assertEquals(0, logs.size());
        }

        @Test
        void processSvpSpendTransaction_createsExpectedTransactionAndSavesTheValuesAndLogsExpectedEvents() {
            // arrange
            svpFundTransaction = arrangeSvpFundTransactionSigned();

            // act
            bridgeSupport.processSvpSpendTransactionUnsigned(rskTx);
            bridgeStorageProvider.save();

            // assert
            assertSvpSpendTxHashUnsignedWasSavedInStorage();
            assertSvpSpendTxIsWaitingForSignatures();
            assertSvpSpendTxHasExpectedInputsAndOutputs();

            assertSvpFundTxSignedWasRemovedFromStorage();

            assertLogPegoutTransactionCreated(logs, svpSpendTransactionUnsigned);

            TransactionOutput outputToActiveFed = svpSpendTransactionUnsigned.getOutput(0);
            assertLogReleaseRequested(logs, rskTx.getHash(), svpSpendTransactionHashUnsigned, outputToActiveFed.getValue());
        }

        private void assertSvpSpendTxHashUnsignedWasSavedInStorage() {
            Optional<Sha256Hash> svpSpendTransactionHashUnsignedOpt = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
            assertTrue(svpSpendTransactionHashUnsignedOpt.isPresent());

            svpSpendTransactionHashUnsigned = svpSpendTransactionHashUnsignedOpt.get();
        }

        private void assertSvpSpendTxIsWaitingForSignatures() {
            Optional<Map.Entry<Keccak256, BtcTransaction>> svpSpendTxWaitingForSignaturesOpt = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
            assertTrue(svpSpendTxWaitingForSignaturesOpt.isPresent());
            Map.Entry<Keccak256, BtcTransaction> svpSpendTxWaitingForSignatures = svpSpendTxWaitingForSignaturesOpt.get();

            Keccak256 svpSpendTxWaitingForSignaturesRskTxHash = svpSpendTxWaitingForSignatures.getKey();
            assertEquals(rskTx.getHash(), svpSpendTxWaitingForSignaturesRskTxHash);

            BtcTransaction svpSpendTxWaitingForSignaturesSpendTx = svpSpendTxWaitingForSignatures.getValue();
            assertEquals(svpSpendTransactionHashUnsigned, svpSpendTxWaitingForSignaturesSpendTx.getHash());
            svpSpendTransactionUnsigned = svpSpendTxWaitingForSignaturesSpendTx;
        }

        private void assertSvpFundTxSignedWasRemovedFromStorage() {
            Optional<BtcTransaction> svpFundTxSigned = bridgeStorageProvider.getSvpFundTxSigned();
            assertFalse(svpFundTxSigned.isPresent());
        }

        private void assertSvpSpendTxHasExpectedInputsAndOutputs() {
            List<TransactionInput> inputs = svpSpendTransactionUnsigned.getInputs();
            assertEquals(2, inputs.size());
            assertInputsHaveExpectedScriptSig(inputs);
            assertInputsOutpointHashIsFundTxHash(inputs, svpFundTransaction.getHash());

            List<TransactionOutput> outputs = svpSpendTransactionUnsigned.getOutputs();
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

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("svp spend transaction signing tests")
    class SvpSpendTxSigning {
        private static final List<BtcECKey> PROPOSED_FEDERATION_SIGNERS_KEYS =
            BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"member01", "member02", "member03", "member04", "member05"}, true); // this is needed to have the private keys too

        private static final Keccak256 svpSpendTxCreationHash = RskTestUtils.createHash(1);

        private BtcTransaction svpSpendTx;
        private List<Sha256Hash> svpSpendTxSigHashes;
        private List<LogInfo> logs;
        private BridgeEventLogger bridgeEventLogger;

        @BeforeEach
        void setUp() {
            logs = new ArrayList<>();
            bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeMainNetConstants,
                allActivations,
                logs
            );

            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainNetConstants)
                .withProvider(bridgeStorageProvider)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withEventLogger(bridgeEventLogger)
                .withExecutionBlock(rskExecutionBlock)
                .build();

            arrangeSvpSpendTransaction();
            svpSpendTxSigHashes = generateTransactionInputsSigHashes(svpSpendTx);
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
                assertFederatorDidNotSignInputs(svpSpendTx.getInputs(), svpSpendTxSigHashes, wrongSigningKey);
            }

            assertAddSignatureWasNotLogged();
            assertSvpSpendTxWFSWasNotRemoved();
        }

        @Test
        void addSignature_forSvpSpendTx_whenProposedFederationDoesNotExist_shouldNotAddProposedFederatorSignatures() throws Exception {
            // arrange
            when(federationSupport.getProposedFederation()).thenReturn(Optional.empty());

            // act
            for (BtcECKey proposedFederatorSignerKey : PROPOSED_FEDERATION_SIGNERS_KEYS) {
                List<byte[]> signatures = generateSignerEncodedSignatures(proposedFederatorSignerKey, svpSpendTxSigHashes);
                bridgeSupport.addSignature(proposedFederatorSignerKey, signatures, svpSpendTxCreationHash);
            }

            // assert
            for (BtcECKey key : PROPOSED_FEDERATION_SIGNERS_KEYS) {
                assertFederatorDidNotSignInputs(svpSpendTx.getInputs(), svpSpendTxSigHashes, key);
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
            for (BtcECKey proposedFederatorSignerKeys : PROPOSED_FEDERATION_SIGNERS_KEYS) {
                List<byte[]> signatures = generateSignerEncodedSignatures(proposedFederatorSignerKeys, svpSpendTxSigHashes);
                bridgeSupport.addSignature(proposedFederatorSignerKeys, signatures, svpSpendTxCreationHash);
            }

            // assert
            for (BtcECKey proposedFederatorSignerKeys : PROPOSED_FEDERATION_SIGNERS_KEYS) {
                assertFederatorDidNotSignInputs(svpSpendTx.getInputs(), svpSpendTxSigHashes, proposedFederatorSignerKeys);
            }

            assertAddSignatureWasNotLogged();
            assertSvpSpendTxWFSWasNotRemoved();
        }

        @Test
        void addSignature_forSvpSpendTx_withoutEnoughSignatures_shouldNotAddProposedFederatorsSignatures() throws Exception {
            // act
            for (BtcECKey proposedFederatorSignerKey : PROPOSED_FEDERATION_SIGNERS_KEYS) {
                List<byte[]> signatures = generateSignerEncodedSignatures(proposedFederatorSignerKey, svpSpendTxSigHashes);
                List<byte[]> notEnoughSignatures = signatures.subList(0, signatures.size() - 1);
                bridgeSupport.addSignature(proposedFederatorSignerKey, notEnoughSignatures, svpSpendTxCreationHash);
            }

            // assert
            for (BtcECKey proposedFederatorSignerKeys : PROPOSED_FEDERATION_SIGNERS_KEYS) {
                assertFederatorDidNotSignInputs(svpSpendTx.getInputs(), svpSpendTxSigHashes, proposedFederatorSignerKeys);
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
            for (BtcECKey proposedFederatorSignerKey : PROPOSED_FEDERATION_SIGNERS_KEYS) {
                List<byte[]> signatures = generateSignerEncodedSignatures(proposedFederatorSignerKey, svpSpendTxSigHashes);
                bridgeSupport.addSignature(proposedFederatorSignerKey, signatures, svpSpendTxCreationHash);
            }

            // assert
            for (BtcECKey proposedFederatorSignerKey : PROPOSED_FEDERATION_SIGNERS_KEYS) {
                assertFederatorSigning(
                    svpSpendTxCreationHash.getBytes(),
                    svpSpendTx.getInputs(),
                    svpSpendTxSigHashes,
                    proposedFederation,
                    proposedFederatorSignerKey
                );
            }
            assertLogReleaseBtc(svpSpendTxCreationHash, svpSpendTx);
            assertLogsSize(PROPOSED_FEDERATION_SIGNERS_KEYS.size() + 1); // proposedFedSigners size for addSignature, +1 for release btc
            assertFalse(bridgeStorageProvider.getSvpSpendTxWaitingForSignatures().isPresent());
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

            for (BtcECKey key : PROPOSED_FEDERATION_SIGNERS_KEYS) {
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

            assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
            assertEventWasEmittedWithExpectedData(logs, encodedData);
        }

        private void assertLogReleaseBtc(Keccak256 rskTxHash, BtcTransaction btcTx) {
            byte[] rskTxHashSerialized = rskTxHash.getBytes();
            List<DataWord> encodedTopics = getEncodedTopics(releaseBtcEvent, rskTxHashSerialized);

            byte[] btcTxSerialized = btcTx.bitcoinSerialize();
            byte[] encodedData = getEncodedData(releaseBtcEvent, btcTxSerialized);

            assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
            assertEventWasEmittedWithExpectedData(logs, encodedData);
        }

        private void assertFederatorSignedInputs(List<TransactionInput> inputs, List<Sha256Hash> sigHashes, BtcECKey key) {
            for (TransactionInput input : inputs) {
                Sha256Hash sigHash = sigHashes.get(inputs.indexOf(input));
                assertTrue(BridgeUtils.isInputSignedByThisFederator(key, sigHash, input));
            }
        }

        private void arrangeSvpSpendTransaction() {
            recreateSvpSpendTransaction();
            saveSvpSpendTransactionWFSValues();
        }

        private void recreateSvpSpendTransaction() {
            svpSpendTx = new BtcTransaction(btcMainnetParams);
            svpSpendTx.setVersion(BTC_TX_VERSION_2);

            BtcTransaction svpFundTx = arrangeSvpFundTransactionSigned();
            // add inputs
            addInputFromMatchingOutputScript(svpSpendTx, svpFundTx, proposedFederation.getP2SHScript());
            Script proposedFederationRedeemScript = proposedFederation.getRedeemScript();
            svpSpendTx.getInput(0)
                .setScriptSig(createBaseP2SHInputScriptThatSpendsFromRedeemScript(proposedFederationRedeemScript));

            Script flyoverRedeemScript = getFlyoverRedeemScript(bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederationRedeemScript);
            addInputFromMatchingOutputScript(svpSpendTx, svpFundTx, ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript));
            svpSpendTx.getInput(1)
                .setScriptSig(createBaseP2SHInputScriptThatSpendsFromRedeemScript(flyoverRedeemScript));

            // add output
            Coin amount = Coin.valueOf(2114L); // previously calculated amount
            svpSpendTx.addOutput(amount, federationSupport.getActiveFederationAddress());
        }

        private void saveSvpSpendTransactionWFSValues() {
            Map.Entry<Keccak256, BtcTransaction> svpSpendTxWaitingForSignatures = new AbstractMap.SimpleEntry<>(svpSpendTxCreationHash, svpSpendTx);
            bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWaitingForSignatures);
            bridgeStorageProvider.save();
        }
    }

    private void arrangeExecutionBlockIsAfterValidationPeriodEnded() {
        long validationPeriodEndBlock = proposedFederation.getCreationBlockNumber()
            + bridgeMainNetConstants.getFederationConstants().getValidationPeriodDurationInBlocks();
        long rskExecutionBlockTimestamp = 10L;

        rskExecutionBlock = createRskBlock(validationPeriodEndBlock, rskExecutionBlockTimestamp);
    }

    private BtcTransaction arrangeSvpFundTransactionSigned() {
        BtcTransaction svpFundTransaction = recreateSvpFundTransactionUnsigned();
        signInputs(svpFundTransaction);

        bridgeStorageProvider.setSvpFundTxSigned(svpFundTransaction);
        bridgeStorageProvider.save();

        return svpFundTransaction;
    }

    private BtcTransaction recreateSvpFundTransactionUnsigned() {
        BtcTransaction svpFundTransaction = new BtcTransaction(btcMainnetParams);

        Sha256Hash parentTxHash = BitcoinTestUtils.createHash(1);
        addInput(svpFundTransaction, parentTxHash, proposedFederation.getRedeemScript());

        svpFundTransaction.addOutput(spendableValueFromProposedFederation, proposedFederation.getAddress());
        Address flyoverProposedFederationAddress =
            PegUtils.getFlyoverAddress(btcMainnetParams, bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederation.getRedeemScript());
        svpFundTransaction.addOutput(spendableValueFromProposedFederation, flyoverProposedFederationAddress);

        return svpFundTransaction;
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

    private void assertOutputWasSentToExpectedScriptWithExpectedAmount(List<TransactionOutput> transactionOutputs, Script expectedScriptPubKey, Coin expectedAmount) {
        Optional<TransactionOutput> expectedOutput = searchForOutput(transactionOutputs, expectedScriptPubKey);
        assertTrue(expectedOutput.isPresent());

        TransactionOutput output = expectedOutput.get();
        assertEquals(expectedAmount, output.getValue());
    }

    private void assertLogReleaseRequested(List<LogInfo> logs, Keccak256 releaseCreationTxHash, Sha256Hash pegoutTransactionHash, Coin requestedAmount) {
        byte[] releaseCreationTxHashSerialized = releaseCreationTxHash.getBytes();
        byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(releaseRequestedEvent, releaseCreationTxHashSerialized, pegoutTransactionHashSerialized);

        byte[] encodedData = getEncodedData(releaseRequestedEvent, requestedAmount.getValue());

        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    private void assertLogPegoutTransactionCreated(List<LogInfo> logs, BtcTransaction pegoutTransaction) {
        Sha256Hash pegoutTransactionHash = pegoutTransaction.getHash();
        byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(pegoutTransactionCreatedEvent, pegoutTransactionHashSerialized);

        List<Coin> outpointValues = extractOutpointValues(pegoutTransaction);
        byte[] serializedOutpointValues = UtxoUtils.encodeOutpointValues(outpointValues);
        byte[] encodedData = getEncodedData(pegoutTransactionCreatedEvent, serializedOutpointValues);

        assertEventWasEmittedWithExpectedTopics(logs, encodedTopics);
        assertEventWasEmittedWithExpectedData(logs, encodedData);
    }

    private List<DataWord> getEncodedTopics(CallTransaction.Function bridgeEvent, Object... args) {
        byte[][] encodedTopicsInBytes = bridgeEvent.encodeEventTopics(args);
        return LogInfo.byteArrayToList(encodedTopicsInBytes);
    }

    private byte[] getEncodedData(CallTransaction.Function bridgeEvent, Object... args) {
        return bridgeEvent.encodeEventData(args);
    }

    private void assertEventWasEmittedWithExpectedTopics(List<LogInfo> logs, List<DataWord> expectedTopics) {
        Optional<LogInfo> topicOpt = logs.stream()
            .filter(log -> log.getTopics().equals(expectedTopics))
            .findFirst();
        assertTrue(topicOpt.isPresent());
    }

    private void assertEventWasEmittedWithExpectedData(List<LogInfo> logs, byte[] expectedData) {
        Optional<LogInfo> data = logs.stream()
            .filter(log -> Arrays.equals(log.getData(), expectedData))
            .findFirst();
        assertTrue(data.isPresent());
    }
}
