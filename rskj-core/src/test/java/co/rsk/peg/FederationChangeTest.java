package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.*;

import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FederationChangeTest {
    private static final RskAddress bridgeContractAddress = PrecompiledContracts.BRIDGE_ADDR;
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);

    private static final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainNetConstants.getBtcParams();
    private static final FederationConstants federationMainNetConstants = bridgeMainNetConstants.getFederationConstants();

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
        Coin feePerKb = Coin.valueOf(1000);
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
        private final List<LogInfo> logs = new ArrayList<>();
        private final CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();
        private final CallTransaction.Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();

        private BtcTransaction svpFundTransactionUnsigned;
        private Sha256Hash svpFundTransactionHashUnsigned;

        @BeforeEach
        void setUp() {
            SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());;
            BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeMainNetConstants,
                allActivations,
                logs,
                signatureCache
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
            assertLogReleaseRequested(rskTx.getHash(), svpFundTransactionHashUnsigned);
            assertLogPegoutTransactionCreated(svpFundTransactionUnsigned);
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

            Optional<TransactionOutput> outputToProposedFederationOpt = searchForOutput(svpFundTransactionUnsignedOutputs, proposedFederationScriptPubKey);
            assertTrue(outputToProposedFederationOpt.isPresent());

            TransactionOutput outputToProposedFederation = outputToProposedFederationOpt.get();
            assertEquals(spendableValueFromProposedFederation, outputToProposedFederation.getValue());
        }

        private void assertOneOutputIsToProposedFederationWithFlyoverPrefixWithExpectedAmount(List<TransactionOutput> svpFundTransactionUnsignedOutputs) {
            Script proposedFederationWithFlyoverPrefixScriptPubKey =
                PegUtils.getFlyoverScriptPubKey(bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederation.getRedeemScript());

            Optional<TransactionOutput> outputToProposedFederationWithFlyoverPrefixOpt = searchForOutput(
                svpFundTransactionUnsignedOutputs,
                proposedFederationWithFlyoverPrefixScriptPubKey
            );
            assertTrue(outputToProposedFederationWithFlyoverPrefixOpt.isPresent());

            TransactionOutput outputToProposedFederationWithFlyoverPrefix = outputToProposedFederationWithFlyoverPrefixOpt.get();
            assertEquals(spendableValueFromProposedFederation, outputToProposedFederationWithFlyoverPrefix.getValue());
        }

        private Optional<TransactionOutput> searchForOutput(List<TransactionOutput> transactionOutputs, Script outputScriptPubKey) {
            return transactionOutputs.stream()
                .filter(output -> output.getScriptPubKey().equals(outputScriptPubKey))
                .findFirst();
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

        private void assertLogReleaseRequested(Keccak256 releaseCreationTxHash, Sha256Hash pegoutTransactionHash) {
            byte[] releaseCreationTxHashSerialized = releaseCreationTxHash.getBytes();
            byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
            List<DataWord> encodedTopics = getEncodedTopics(releaseRequestedEvent, releaseCreationTxHashSerialized, pegoutTransactionHashSerialized);

            byte[] encodedData = getEncodedData(releaseRequestedEvent, spendableValueFromProposedFederation.getValue());

            assertEventWasEmittedWithExpectedTopics(encodedTopics);
            assertEventWasEmittedWithExpectedData(encodedData);
        }

        private void assertLogPegoutTransactionCreated(BtcTransaction pegoutTransaction) {
            Sha256Hash pegoutTransactionHash = pegoutTransaction.getHash();
            byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
            List<DataWord> encodedTopics = getEncodedTopics(pegoutTransactionCreatedEvent, (Object) pegoutTransactionHashSerialized);

            List<Coin> outpointValues = extractOutpointValues(pegoutTransaction);
            byte[] serializedOutpointValues = UtxoUtils.encodeOutpointValues(outpointValues);
            byte[] encodedData = getEncodedData(pegoutTransactionCreatedEvent, (Object) serializedOutpointValues);

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
            BtcTransaction svpFundTransaction = arrangeSvpFundTransactionWithChange();

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
            // make rsk execution block to be after validation period ended
            long validationPeriodEndBlock = proposedFederation.getCreationBlockNumber()
                + bridgeMainNetConstants.getFederationConstants().getValidationPeriodDurationInBlocks();
            long rskExecutionBlockTimestamp = 10L;
            BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
                .setNumber(validationPeriodEndBlock + 1) // adding one more block to ensure validation period is ended
                .setTimestamp(rskExecutionBlockTimestamp)
                .build();
            rskExecutionBlock = Block.createBlockFromHeader(blockHeader, true);

            BtcTransaction svpFundTransaction = arrangeSvpFundTransactionWithChange();
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
            arrangeSvpFundTransactionWithChange();

            BtcTransaction pegout = createPegout();
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
            BtcTransaction svpFundTransaction = recreateSvpFundTransaction();
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
            BtcTransaction svpFundTransaction = arrangeSvpFundTransactionWithChange();

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

        private BtcTransaction arrangeSvpFundTransactionWithChange() {
            BtcTransaction svpFundTransaction = recreateSvpFundTransaction();
            addOutputChange(svpFundTransaction);
            saveSvpFundTransactionUnsigned(svpFundTransaction);

            return svpFundTransaction;
        }

        private BtcTransaction recreateSvpFundTransaction() {
            BtcTransaction svpFundTransaction = new BtcTransaction(btcMainnetParams);

            Sha256Hash parentTxHash = BitcoinTestUtils.createHash(1);
            addInput(svpFundTransaction, parentTxHash);

            svpFundTransaction.addOutput(spendableValueFromProposedFederation, proposedFederation.getAddress());
            Address flyoverProposedFederationAddress =
                PegUtils.getFlyoverAddress(btcMainnetParams, bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederation.getRedeemScript());
            svpFundTransaction.addOutput(spendableValueFromProposedFederation, flyoverProposedFederationAddress);

            return svpFundTransaction;
        }

        private void saveSvpFundTransactionUnsigned(BtcTransaction svpFundTransaction) {
            savePegoutIndex(svpFundTransaction);
            saveSvpFundTransactionHashUnsigned(svpFundTransaction.getHash());
        }

        private BtcTransaction createPegout() {
            BtcTransaction pegout = new BtcTransaction(btcMainnetParams);
            Sha256Hash parentTxHash = BitcoinTestUtils.createHash(2);
            addInput(pegout, parentTxHash);
            addOutputChange(pegout);

            return pegout;
        }

        private void addInput(BtcTransaction transaction, Sha256Hash parentTxHash) {
            // we need to add an input that we can actually sign,
            // and we know the private keys for the following scriptSig
            Federation federation = P2shErpFederationBuilder.builder().build();
            Script scriptSig = federation.getP2SHScript().createEmptyInputScript(null, federation.getRedeemScript());

            transaction.addInput(parentTxHash, 0, scriptSig);
        }

        private void signInputs(BtcTransaction transaction) {
            List<BtcECKey> keysToSign =
                BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"member01", "member02", "member03", "member04", "member05"}, true);
            List<TransactionInput> inputs = transaction.getInputs();
            for (TransactionInput input : inputs) {
                BitcoinTestUtils.signTransactionInputFromP2shMultiSig(transaction, inputs.indexOf(input), keysToSign);
            }
        }

        private void addOutputChange(BtcTransaction transaction) {
            // add output to the active fed
            Script activeFederationP2SHScript = activeFederation.getP2SHScript();
            transaction.addOutput(Coin.COIN.multiply(10), activeFederationP2SHScript);
        }

        private void savePegoutIndex(BtcTransaction pegout) {
            BitcoinUtils.getFirstInputSigHash(pegout)
                .ifPresent(inputSigHash -> bridgeStorageProvider.setPegoutTxSigHash(inputSigHash));
        }

        private void saveSvpFundTransactionHashUnsigned(Sha256Hash svpFundTransactionHashUnsigned) {
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTransactionHashUnsigned);
            bridgeSupport.save();
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

        private void assertSvpFundTransactionValuesWereNotUpdated() {
            assertTrue(bridgeStorageProvider.getSvpFundTxHashUnsigned().isPresent());
            assertFalse(bridgeStorageProvider.getSvpFundTxSigned().isPresent());
        }

        private void assertSvpFundTransactionValuesWereUpdated() {
            Optional<BtcTransaction> svpFundTransactionSignedOpt = bridgeStorageProvider.getSvpFundTxSigned();
            assertTrue(svpFundTransactionSignedOpt.isPresent());

            assertFalse(bridgeStorageProvider.getSvpFundTxHashUnsigned().isPresent());
        }
    }
}
