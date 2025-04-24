package co.rsk.peg;

import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeEventsTestUtils.*;
import static co.rsk.peg.BridgeStorageIndexKey.*;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.PegUtils.getFlyoverFederationRedeemScript;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.*;
import static co.rsk.peg.bitcoin.BitcoinUtils.*;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static co.rsk.peg.pegin.RejectedPeginReason.INVALID_AMOUNT;
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
import co.rsk.peg.btcLockSender.*;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.pegininstructions.PeginInstructions;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.utils.NonRefundablePeginReason;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.core.CallTransaction.Function;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.*;

class BridgeSupportSvpTest {
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);
    private static final RskAddress bridgeContractAddress = PrecompiledContracts.BRIDGE_ADDR;
    private static final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainNetConstants.getBtcParams();
    private static final FederationConstants federationMainNetConstants = bridgeMainNetConstants.getFederationConstants();
    private static final List<Coin> svpFundTxOutpointsValues = Arrays.asList(
        Coin.valueOf(1_000_000),
        Coin.valueOf(500_000),
        Coin.valueOf(300_000)
    );
    private static final Coin svpFundTxOutputsValue = bridgeMainNetConstants.getSvpFundTxOutputsValue();
    private static final Coin totalValueSentToProposedFederation = svpFundTxOutputsValue.multiply(2);
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
    private StorageAccessor bridgeStorageAccessor;
    private FederationStorageProvider federationStorageProvider;

    private BridgeSupport bridgeSupport;
    private FederationSupport federationSupport;
    private FeePerKbSupport feePerKbSupport;

    private Federation activeFederation;
    private Federation proposedFederation;

    private BtcTransaction svpFundTransaction;
    private BtcTransaction svpSpendTransaction;

    private PartialMerkleTree pmtWithTransactions;
    private int btcBlockWithPmtHeight;

    @BeforeEach
    void setUp() {
        // rsk execution block
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

        ECKey key = RskTestUtils.getEcKeyFromSeed("key");
        RskAddress address = new RskAddress(key.getAddress());
        when(rskTx.getSender(any())).thenReturn(address); // to not throw when logging update collections after calling it

        // federation support
        bridgeStorageAccessor = new InMemoryStorage();
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        activeFederation = FederationTestUtils.getErpFederation(federationMainNetConstants.getBtcParams());
        federationStorageProvider.setNewFederation(activeFederation);

        List<UTXO> activeFederationUTXOs = Arrays.asList(
            BitcoinTestUtils.createUTXO(1, 0, svpFundTxOutpointsValues.get(0), activeFederation.getAddress()),
            BitcoinTestUtils.createUTXO(2, 0, svpFundTxOutpointsValues.get(1), activeFederation.getAddress()),
            BitcoinTestUtils.createUTXO(3, 0, svpFundTxOutpointsValues.get(2), activeFederation.getAddress())
        );
        bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), activeFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

        proposedFederation = P2shErpFederationBuilder.builder().build();
        federationStorageProvider.setProposedFederation(proposedFederation);

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainNetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(allActivations)
            .build();

        // fee per kb support
        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKb);

        // bridge storage provider
        repository = createRepository();
        bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            bridgeContractAddress,
            btcMainnetParams,
            allActivations
        );

        // logs
        logs = new ArrayList<>();
        bridgeEventLogger = new BridgeEventLoggerImpl(
            bridgeMainNetConstants,
            allActivations,
            logs
        );

        // bridge support
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
    @Tag("Failure tests")
    class FailureTests {

        @BeforeEach
        void setUp() {
            arrangeExecutionBlockIsAfterValidationPeriodEnded();

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
        void updateCollections_whenSvpFundTxHashUnsignedIsSet_shouldLogValidationFailureAndClearValue() throws IOException {
            // arrange
            Sha256Hash svpFundTransactionHashUnsigned = BitcoinTestUtils.createHash(1);
            bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTransactionHashUnsigned);

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertLogCommitFederationFailed();
            assertNoProposedFederation();
            assertNoSVPValues();
        }

        @Test
        void updateCollections_whenSvpFundTxHashUnsignedSavedInStorage_shouldLogValidationFailureAndClearValue() throws IOException {
            // arrange
            Sha256Hash svpFundTxHashUnsigned = BitcoinTestUtils.createHash(1);
            byte[] svpFundTxHashUnsignedSerialized = BridgeSerializationUtils.serializeSha256Hash(svpFundTxHashUnsigned);
            DataWord storageKey = SVP_FUND_TX_HASH_UNSIGNED.getKey();

            repository.addStorageBytes(bridgeContractAddress, storageKey, svpFundTxHashUnsignedSerialized);
            assertNotNull(repository.getStorageBytes(bridgeContractAddress, storageKey));

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertLogCommitFederationFailed();
            assertNoProposedFederation();
            assertNoSVPValues();
        }

        @Test
        void updateCollections_whenSvpFundTxSignedIsSet_shouldLogValidationFailureAndClearValue() throws IOException {
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
        void updateCollections_whenSvpFundTxSignedSavedInStorage_shouldLogValidationFailureAndClearValue() throws IOException {
            // arrange
            svpFundTransaction = new BtcTransaction(btcMainnetParams);
            byte[] svpFundTxSerialized = BridgeSerializationUtils.serializeBtcTransaction(svpFundTransaction);
            DataWord storageKey = SVP_FUND_TX_SIGNED.getKey();

            repository.addStorageBytes(bridgeContractAddress, storageKey, svpFundTxSerialized);
            assertNotNull(repository.getStorageBytes(bridgeContractAddress, storageKey));

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertLogCommitFederationFailed();
            assertNoProposedFederation();
            assertNoSVPValues();
        }

        @Test
        void updateCollections_whenSvpSpendTxWFSIsSet_shouldLogValidationFailureAndClearSpendTxValues() throws IOException {
            // arrange
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
        void updateCollections_whenSvpSpendTxWFSSavedInStorage_shouldLogValidationFailureAndClearSpendTxValues() throws IOException {
            // arrange
            svpSpendTransaction = new BtcTransaction(btcMainnetParams);
            Map.Entry<Keccak256, BtcTransaction> svpSpendTxWFS = new AbstractMap.SimpleEntry<>(svpSpendTxCreationHash, svpSpendTransaction);
            byte[] svpSpendTxWfsSerialized = BridgeSerializationUtils.serializeRskTxWaitingForSignatures(svpSpendTxWFS);
            DataWord storageKey = SVP_SPEND_TX_WAITING_FOR_SIGNATURES.getKey();

            repository.addStorageBytes(bridgeContractAddress, storageKey, svpSpendTxWfsSerialized);
            assertNotNull(repository.getStorageBytes(bridgeContractAddress, storageKey));

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertLogCommitFederationFailed();
            assertNoProposedFederation();
            assertNoSVPValues();
        }

        @Test
        void updateCollections_whenSvpSpendTxHashUnsignedIsSet_shouldLogValidationFailureAndClearValue() throws IOException {
            // arrange
            Sha256Hash svpSpendTransactionHashUnsigned = BitcoinTestUtils.createHash(2);
            bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTransactionHashUnsigned);

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertLogCommitFederationFailed();
            assertNoProposedFederation();
            assertNoSVPValues();
        }

        @Test
        void updateCollections_whenSvpSpendTxHashUnsignedSavedInStorage_shouldLogValidationFailureAndClearValue() throws IOException {
            // arrange
            Sha256Hash svpSpendTransactionHashUnsigned = BitcoinTestUtils.createHash(2);
            byte[] svpSpendTxHashUnsignedSerialized = BridgeSerializationUtils.serializeSha256Hash(svpSpendTransactionHashUnsigned);
            DataWord storageKey = SVP_SPEND_TX_HASH_UNSIGNED.getKey();

            repository.addStorageBytes(bridgeContractAddress, storageKey, svpSpendTxHashUnsignedSerialized);
            assertNotNull(repository.getStorageBytes(bridgeContractAddress, storageKey));

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
            federationStorageProvider.setProposedFederation(null);

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertNoSvpFundTxHashUnsigned();
        }

        @Test
        void updateCollections_whenThereAreNoEnoughUTXOs_shouldNotCreateFundTransaction() throws IOException {
            // arrange
            List<UTXO> insufficientUtxos = new ArrayList<>();
            bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), insufficientUtxos, BridgeSerializationUtils::serializeUTXOList);

            // act
            bridgeSupport.updateCollections(rskTx);

            // assert
            assertNoSvpFundTxHashUnsigned();
        }

        @Test
        void updateCollections_whenFundTxCanBeCreated_createsExpectedFundTxAndSavesTheHashInStorageEntryAndPerformsPegoutActions() throws Exception {
            // arrange
            int activeFederationUtxosSizeBeforeCreatingFundTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.updateCollections(rskTx);
            bridgeStorageProvider.save();

            // assert
            Optional<Sha256Hash> svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
            assertTrue(svpFundTxHashUnsigned.isPresent());
            assertSvpFundTxReleaseWasSettled(svpFundTxHashUnsigned.get());

            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeCreatingFundTx - svpFundTxOutpointsValues.size()); // using all outpoints

            assertSvpFundTransactionHasExpectedInputsAndOutputs();
        }

        private void assertSvpFundTransactionHasExpectedInputsAndOutputs() {
            assertInputsAreFromActiveFederation();

            int svpFundTransactionUnsignedOutputsExpectedSize = 3;
            assertEquals(svpFundTransactionUnsignedOutputsExpectedSize, svpFundTransaction.getOutputs().size());

            // assert outputs are the expected ones in the expected order
            TransactionOutput outputToProposedFed = svpFundTransaction.getOutput(0);
            assertEquals(outputToProposedFed.getScriptPubKey(), proposedFederation.getP2SHScript());
            assertEquals(outputToProposedFed.getValue(), bridgeMainNetConstants.getSvpFundTxOutputsValue());

            TransactionOutput outputToFlyoverProposedFed = svpFundTransaction.getOutput(1);
            Script proposedFederationWithFlyoverPrefixScriptPubKey =
                PegUtils.getFlyoverFederationScriptPubKey(bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederation);
            assertEquals(outputToFlyoverProposedFed.getScriptPubKey(), proposedFederationWithFlyoverPrefixScriptPubKey);
            assertEquals(outputToFlyoverProposedFed.getValue(), bridgeMainNetConstants.getSvpFundTxOutputsValue());

            TransactionOutput changeOutput = svpFundTransaction.getOutput(2);
            assertEquals(changeOutput.getScriptPubKey(), activeFederation.getP2SHScript());
        }

        private void assertInputsAreFromActiveFederation() {
            Script activeFederationScriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(activeFederation);
            List<TransactionInput> inputs = svpFundTransaction.getInputs();

            for (TransactionInput input : inputs) {
                assertEquals(activeFederationScriptSig, input.getScriptSig());
            }
        }

        @Test
        void updateCollections_whenWaitingForFundTxToBeRegistered_shouldNotCreateFundTransactionAgain() throws Exception {
            // arrange
            arrangeSvpFundTransactionUnsigned();

            // act
            bridgeSupport.updateCollections(rskTx);
            bridgeStorageProvider.save();

            // assert
            assertJustUpdateCollectionsWasLogged();
        }

        @Test
        void updateCollections_whenFundTxSignedIsSaved_shouldNotCreateFundTransaction() throws Exception {
            // arrange
            arrangeSvpFundTransactionSigned();

            // act
            bridgeSupport.updateCollections(rskTx);
            bridgeStorageProvider.save();

            // assert
            assertNoSvpFundTxHashUnsigned();
        }

        @Test
        void updateCollections_whenWaitingForSpendTxToBeRegistered_shouldNotCreateFundTransaction() throws Exception {
            // arrange
            arrangeSvpSpendTransaction();

            // act
            bridgeSupport.updateCollections(rskTx);
            bridgeStorageProvider.save();

            // assert
            assertNoSvpFundTxHashUnsigned();
            assertJustUpdateCollectionsWasLogged();
        }

        private void assertJustUpdateCollectionsWasLogged() {
            assertEquals(1, logs.size());

            CallTransaction.Function updateCollectionsEvent = BridgeEvents.UPDATE_COLLECTIONS.getEvent();
            List<DataWord> encodedTopics = getEncodedTopics(updateCollectionsEvent);
            assertEventWasEmittedWithExpectedTopics(encodedTopics);

            String senderData = rskTx.getSender(mock(SignatureCache.class)).toHexString();
            byte[] encodedData = getEncodedData(updateCollectionsEvent, senderData);
            assertEventWasEmittedWithExpectedData(encodedData);
        }
    }

    private void arrangeSvpFundTransactionUnsigned() {
        recreateSvpFundTransactionUnsigned();
        addOutputChange(svpFundTransaction);

        saveSvpFundTransactionUnsigned();
        Optional<Sha256Hash> svpFundTransactionHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
        assertTrue(svpFundTransactionHashUnsigned.isPresent());
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


    private void assertSvpFundTxReleaseWasSettled(Sha256Hash svpFundTransactionHashUnsigned) throws IOException {
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = bridgeStorageProvider.getPegoutsWaitingForConfirmations();
        assertPegoutWasAddedToPegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations, svpFundTransactionHashUnsigned, rskTx.getHash());

        svpFundTransaction = getSvpFundTransactionFromPegoutsMap(pegoutsWaitingForConfirmations);

        assertPegoutTxSigHashWasSaved(svpFundTransaction);
        assertLogReleaseRequested(rskTx.getHash(), svpFundTransactionHashUnsigned, totalValueSentToProposedFederation);
        assertReleaseTransactionInfoWasProcessed(svpFundTransaction, svpFundTxOutpointsValues);
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

        @Test
        void registerBtcTransaction_forSvpFundTransactionChange_whenProposedFederationDoesNotExist_shouldRegisterTransactionButNotUpdateSvpFundTransactionValues() throws Exception {
            // arrange
            arrangeSvpFundTransactionUnsigned();

            signInputs(svpFundTransaction); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(svpFundTransaction);

            federationStorageProvider.setProposedFederation(null);

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

            BtcTransaction pegout = createPegout(proposedFederation);
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
            Optional<Sha256Hash> svpSpendTransactionHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
            assertTrue(svpSpendTransactionHashUnsigned.isPresent());

            Optional<Map.Entry<Keccak256, BtcTransaction>> svpSpendTxWaitingForSignaturesOpt = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
            assertTrue(svpSpendTxWaitingForSignaturesOpt.isPresent());
            Map.Entry<Keccak256, BtcTransaction> svpSpendTxWaitingForSignatures = svpSpendTxWaitingForSignaturesOpt.get();
            svpSpendTransaction = svpSpendTxWaitingForSignatures.getValue();

            assertSvpSpendTxHasExpectedInputsAndOutputs();

            assertSvpFundTxSignedWasRemovedFromStorage();

            List<Coin> expectedOutpointsValues =
                List.of(bridgeMainNetConstants.getSvpFundTxOutputsValue(), bridgeMainNetConstants.getSvpFundTxOutputsValue());
            assertReleaseTransactionInfoWasProcessed(svpSpendTransaction, expectedOutpointsValues);

            TransactionOutput outputToActiveFed = svpSpendTransaction.getOutput(0);
            assertLogReleaseRequested(rskTx.getHash(), svpSpendTransactionHashUnsigned.get(), outputToActiveFed.getValue());
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
            Coin fees = feePerKb
                .multiply(calculatedTransactionSize * 12L / 10L) // back up calculation
                .divide(1000);

            Coin valueToSendBackToActiveFed = totalValueSentToProposedFederation
                .minus(fees);
            assertOutputWasSentToExpectedScriptWithExpectedAmount(outputs, activeFederation.getP2SHScript(), valueToSendBackToActiveFed);
        }

        private void assertInputsHaveExpectedScriptSig(List<TransactionInput> inputs) {
            TransactionInput inputToProposedFederation = inputs.get(0);
            Script proposedFederationRedeemScript = proposedFederation.getRedeemScript();
            assertInputHasExpectedScriptSig(inputToProposedFederation, proposedFederationRedeemScript);

            TransactionInput inputToFlyoverProposedFederation = inputs.get(1);
            Script flyoverRedeemScript =
                getFlyoverFederationRedeemScript(bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederationRedeemScript);
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

    private void assertSvpSpendTxHashUnsignedIsInStorage() {
        Optional<Sha256Hash> svpSpendTransactionHashUnsignedOpt = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
        assertTrue(svpSpendTransactionHashUnsignedOpt.isPresent());
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("Spend transaction signing tests")
    class SpendTxSigning {
        private final List<BtcECKey> proposedFederationSignersKeys =
            BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"member01", "member02", "member03", "member04", "member05"}, true); // this is needed to have the private keys too
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
            federationStorageProvider.setProposedFederation(null);

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

            BtcTransaction pegout = createPegout(activeFederation);
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

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("Spend transaction registration tests")
    class SpendTxRegistrationTests {
        @Test
        void registerBtcTransaction_forPegout_whenWaitingForSvpSpendTx_shouldProcessAndRegisterPegout_shouldNotProcessNorRegisterSpendTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            // arrange
            arrangeSvpSpendTransaction();
            setUpForTransactionRegistration(svpSpendTransaction);

            BtcTransaction pegout = createPegout(proposedFederation);
            savePegoutIndex(pegout);
            signInputs(pegout);
            setUpForTransactionRegistration(pegout);

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                pegout.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            // pegout was registered and processed
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(pegout.getHash());

            // spend tx was not registered nor processed
            assertTransactionWasNotProcessed(svpSpendTransaction.getHash());
            // svp success was not processed
            assertSvpSpendTxHashUnsignedIsInStorage();
            assertNoHandoverToNewFederation();
            assertProposedFederationExists();
        }

        @Test
        void registerBtcTransaction_forLegacyPeginFromP2pkh_whenWaitingForSvpSpendTx_shouldProcessAndRegisterPegin_shouldNotProcessNorRegisterSpendTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            // arrange
            arrangeSvpSpendTransaction();
            setUpForTransactionRegistration(svpSpendTransaction);

            BtcTransaction pegin = new BtcTransaction(btcMainnetParams);
            BtcECKey senderPubKey = getBtcEcKeyFromSeed("legacy_pegin_sender");
            Script scriptSig = ScriptBuilder.createInputScript(null, senderPubKey);
            pegin.addInput(BitcoinTestUtils.createHash(1), 0, scriptSig);
            Coin amountToSend = Coin.COIN;
            pegin.addOutput(amountToSend, activeFederation.getAddress());
            setUpForTransactionRegistration(pegin);

            // recreate bridge support with real btcLockSenderProvider instead of a mock to be able to parse pegin
            BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainNetConstants)
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .build();

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                pegin.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            // pegin is legacy
            Optional<BtcLockSender> btcLockSender = btcLockSenderProvider.tryGetBtcLockSender(pegin);
            assertTrue(btcLockSender.isPresent());
            // and sender is p2pkh
            assertEquals(P2pkhBtcLockSender.class, btcLockSender.get().getClass());
            // pegin was registered and processed
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(pegin.getHash());

            // spend tx was not registered nor processed
            assertTransactionWasNotProcessed(svpSpendTransaction.getHash());
            // svp success was not processed
            assertSvpSpendTxHashUnsignedIsInStorage();
            assertNoHandoverToNewFederation();
            assertProposedFederationExists();
        }

        @Test
        void registerBtcTransaction_forLegacyPeginFromP2shP2wpkh_whenWaitingForSvpSpendTx_shouldProcessAndRegisterPegin_shouldNotProcessNorRegisterSpendTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            // arrange
            arrangeSvpSpendTransaction();
            setUpForTransactionRegistration(svpSpendTransaction);

            BtcTransaction pegin = new BtcTransaction(btcMainnetParams);
            BtcECKey senderPubKey = getBtcEcKeyFromSeed("legacy_pegin_p2shp2wpkh_sender");
            // we need to only have one chunk in the scriptSig for p2sh-p2wpkh
            byte[] redeemScript = ByteUtil.merge(new byte[]{ 0x00, 0x14}, senderPubKey.getPubKeyHash());
            Script witnessScript = new ScriptBuilder()
                .data(redeemScript)
                .build();
            pegin.addInput(BitcoinTestUtils.createHash(1), 0, witnessScript);

            TransactionWitness txWit = new TransactionWitness(2);
            txWit.setPush(0, new byte[72]); // push for signatures
            txWit.setPush(1, senderPubKey.getPubKey());
            pegin.setWitness(0, txWit);

            Coin amountToSend = Coin.COIN;
            pegin.addOutput(amountToSend, activeFederation.getAddress());
            setUpForTransactionRegistration(pegin);

            // recreate bridge support with real btcLockSenderProvider instead of a mock to be able to parse pegin
            BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainNetConstants)
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .build();

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                pegin.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            // pegin is legacy
            Optional<BtcLockSender> btcLockSender = btcLockSenderProvider.tryGetBtcLockSender(pegin);
            assertTrue(btcLockSender.isPresent());
            // and sender is p2sh-p2wpkh
            assertEquals(P2shP2wpkhBtcLockSender.class, btcLockSender.get().getClass());
            // pegin was registered and processed
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(pegin.getHash());

            // spend tx was not registered nor processed
            assertTransactionWasNotProcessed(svpSpendTransaction.getHash());
            // svp success was not processed
            assertSvpSpendTxHashUnsignedIsInStorage();
            assertNoHandoverToNewFederation();
            assertProposedFederationExists();
        }

        @Test
        void registerBtcTransaction_forLegacyPeginFromP2shMultisig_whenWaitingForSvpSpendTx_shouldProcessButNotRegisterPegin_shouldNotProcessNorRegisterSpendTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            // arrange
            arrangeSvpSpendTransaction();
            setUpForTransactionRegistration(svpSpendTransaction);

            BtcTransaction pegin = new BtcTransaction(btcMainnetParams);
            List<BtcECKey> pubKeys = Arrays.asList(
                getBtcEcKeyFromSeed("legacy_pegin_p2sh_multisig_key_1"),
                getBtcEcKeyFromSeed("legacy_pegin_p2sh_multisig_key_2")
            );
            Script redeemScript = ScriptBuilder.createRedeemScript(2, pubKeys);
            Script scriptSig = BitcoinUtils.createBaseInputScriptThatSpendsFromRedeemScript(redeemScript);
            pegin.addInput(BitcoinTestUtils.createHash(1), 0, scriptSig);

            Coin amountToSend = Coin.COIN;
            pegin.addOutput(amountToSend, activeFederation.getAddress());
            setUpForTransactionRegistration(pegin);

            // recreate bridge support with real btcLockSenderProvider instead of a mock to be able to parse pegin
            BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainNetConstants)
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .build();

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                pegin.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            // pegin is legacy
            Optional<BtcLockSender> btcLockSender = btcLockSenderProvider.tryGetBtcLockSender(pegin);
            assertTrue(btcLockSender.isPresent());
            // and sender is p2sh-multisig
            assertEquals(P2shMultisigBtcLockSender.class, btcLockSender.get().getClass());
            // pegin was not registered
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx);
            // but was marked as processed
            assertTransactionWasProcessed(pegin.getHash());

            // spend tx was not registered nor processed
            assertTransactionWasNotProcessed(svpSpendTransaction.getHash());
            // svp success was not processed
            assertSvpSpendTxHashUnsignedIsInStorage();
            assertNoHandoverToNewFederation();
            assertProposedFederationExists();
        }

        @Test
        void registerBtcTransaction_forLegacyPeginFromP2shP2wsh_whenWaitingForSvpSpendTx_shouldProcessButNotRegisterPegin_shouldNotProcessNorRegisterSpendTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            // arrange
            arrangeSvpSpendTransaction();
            setUpForTransactionRegistration(svpSpendTransaction);

            BtcTransaction pegin = new BtcTransaction(btcMainnetParams);
            List<BtcECKey> pubKeys = Arrays.asList(
                getBtcEcKeyFromSeed("legacy_pegin_p2sh_multisig_key_1"),
                getBtcEcKeyFromSeed("legacy_pegin_p2sh_multisig_key_2")
            );
            Script redeemScript = ScriptBuilder.createRedeemScript(2, pubKeys);
            // we need to only have one chunk in the scriptSig for p2sh-p2wsh
            Script witnessScript = new ScriptBuilder()
                .data(redeemScript.getProgram())
                .build();
            pegin.addInput(BitcoinTestUtils.createHash(1), 0, witnessScript);

            TransactionWitness txWit = new TransactionWitness(3);
            txWit.setPush(0, new byte[] { 0 });
            txWit.setPush(1, new byte[72]); // push for signatures
            txWit.setPush(2, redeemScript.getProgram());
            pegin.setWitness(0, txWit);

            Coin amountToSend = Coin.COIN;
            pegin.addOutput(amountToSend, activeFederation.getAddress());
            setUpForTransactionRegistration(pegin);

            // recreate bridge support with real btcLockSenderProvider instead of a mock to be able to parse pegin
            BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainNetConstants)
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .build();

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                pegin.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            // pegin is legacy
            Optional<BtcLockSender> btcLockSender = btcLockSenderProvider.tryGetBtcLockSender(pegin);
            assertTrue(btcLockSender.isPresent());
            // and sender is p2sh-p2wsh
            assertEquals(P2shP2wshBtcLockSender.class, btcLockSender.get().getClass());
            // pegin was not registered
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx);
            // but was marked as processed
            assertTransactionWasProcessed(pegin.getHash());

            // spend tx was not registered nor processed
            assertTransactionWasNotProcessed(svpSpendTransaction.getHash());
            // svp success was not processed
            assertSvpSpendTxHashUnsignedIsInStorage();
            assertNoHandoverToNewFederation();
            assertProposedFederationExists();
        }

        @Test
        void registerBtcTransaction_forPeginV1_whenWaitingForSvpSpendTx_shouldProcessAndRegisterPegin_shouldNotProcessNorRegisterSpendTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException, PeginInstructionsException {
            // arrange
            arrangeSvpSpendTransaction();
            setUpForTransactionRegistration(svpSpendTransaction);

            BtcTransaction pegin = new BtcTransaction(btcMainnetParams);
            BtcECKey senderPubKey = getBtcEcKeyFromSeed("legacy_pegin_sender");
            Script scriptSig = ScriptBuilder.createInputScript(null, senderPubKey);
            pegin.addInput(BitcoinTestUtils.createHash(1), 0, scriptSig);
            Coin amountToSend = Coin.COIN;
            pegin.addOutput(amountToSend, activeFederation.getAddress());
            Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(1, bridgeContractAddress, Optional.empty());
            pegin.addOutput(Coin.ZERO, opReturnScript);
            setUpForTransactionRegistration(pegin);

            // recreate bridge support with real btcLockSenderProvider and peginInstructionsProvider instead of a mock to be able to parse pegin v1
            BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
            PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainNetConstants)
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .withPeginInstructionsProvider(peginInstructionsProvider)
                .build();

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                pegin.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            // pegin is v1
            Optional<PeginInstructions> peginInstructions = peginInstructionsProvider.buildPeginInstructions(pegin);
            assertTrue(peginInstructions.isPresent());
            assertEquals(1, peginInstructions.get().getProtocolVersion());
            // pegin was registered and processed
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(pegin.getHash());

            // spend tx was not registered nor processed
            assertTransactionWasNotProcessed(svpSpendTransaction.getHash());
            // svp success was not processed
            assertSvpSpendTxHashUnsignedIsInStorage();
            assertNoHandoverToNewFederation();
            assertProposedFederationExists();
        }

        /*
         This is a hypothetical case, which is not realistic with the implementation of the svp.
         A btc tx hash that is not saved as a spend tx, is identified as a pegin, and will be
         rejected due to invalid amount, since the pegin amount is below the minimum.
         Therefore, this tx should be rejected as pegin and mark as processed
         */
        @Test
        void registerBtcTransaction_whenSpendTransactionHashIsNotSaved_shouldBeIdentifiedAsRejectedPeginAndMarkAsProcessed() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            // arrange
            recreateSvpSpendTransactionUnsigned();
            setUpForTransactionRegistration(svpSpendTransaction);

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpSpendTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            // spend tx was not registered
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx);

            assertTxIsRejectedPeginAndMarkedAsProcessed(svpSpendTransaction);

            // svp success was not processed
            assertNoHandoverToNewFederation();
            assertProposedFederationExists();
        }

        private void assertProposedFederationExists() {
            assertTrue(federationSupport.getProposedFederation().isPresent());
        }

        @Test
        void registerBtcTransaction_whenIsTheSpendTransaction_shouldProcessSpendTxAndSvpSuccess() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            // arrange
            arrangeSvpSpendTransaction();
            setUpForTransactionRegistration(svpSpendTransaction);

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpSpendTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            assertSvpSuccess(activeFederationUtxosSizeBeforeRegisteringTx + 1);
        }

        @Test
        void registerBtcTransaction_evenIfValidationPeriodEnded_shouldRegisterSpendTxAndProcessSvpSuccess() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            // arrange
            arrangeExecutionBlockIsAfterValidationPeriodEnded();

            arrangeSvpSpendTransaction();
            setUpForTransactionRegistration(svpSpendTransaction);

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpSpendTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            assertSvpSuccess(activeFederationUtxosSizeBeforeRegisteringTx + 1);
        }

        @Test
        void registerBtcTransaction_whenIsTheSpendTransaction_withAmountBelowMinPeginValue_shouldProcessSpendTxAndSvpSuccess() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            // arrange
            createSpendTransaction();
            addSpendTransactionInputs();
            Coin amountToSend = bridgeMainNetConstants.getMinimumPeginTxValue(allActivations).minus(Coin.valueOf(1));
            addSpendTransactionOutput(amountToSend);
            saveSvpSpendTransactionValues();

            setUpForTransactionRegistration(svpSpendTransaction);

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpSpendTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // assert
            assertSvpSuccess(activeFederationUtxosSizeBeforeRegisteringTx + 1);
        }

        @Test
        void registerBtcTransaction_twice_whenIsTheSpendTransaction_shouldRegisterItJustOnce() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            // arrange
            arrangeSvpSpendTransaction();
            setUpForTransactionRegistration(svpSpendTransaction);

            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();

            // register spend tx for the first time
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpSpendTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeStorageProvider.save();

            // act
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpSpendTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );

            // assert utxo was registered just once
            assertSvpSuccess(activeFederationUtxosSizeBeforeRegisteringTx + 1);
        }

        private void assertSvpSuccess(int expectedActiveFederationUtxosSize) throws IOException {
            List<UTXO> activeFederationUtxosAfterRegisteringTx = Collections.unmodifiableList(federationSupport.getActiveFederationBtcUTXOs());

            // tx registration
            assertActiveFederationUtxosSize(expectedActiveFederationUtxosSize);
            assertTransactionWasProcessed(svpSpendTransaction.getHash());
            assertTrue(activeFederationUtxosAfterRegisteringTx.stream().anyMatch(
                utxo -> utxo.getHash().equals(svpSpendTransaction.getHash()) && utxo.getIndex() == 0)
            );

            // svp success
            assertNoSvpSpendTxHash();
            assertNoProposedFederation();

            assertHandoverToNewFederation(activeFederationUtxosAfterRegisteringTx);
        }

        private void assertHandoverToNewFederation(List<UTXO> utxosToMove) {
            assertUTXOsWereMovedFromNewToOldFederation(utxosToMove);
            assertNewAndOldFederationsWereSet();
            assertLastRetiredFederationScriptWasSet();
            assertNewActiveFederationCreationBlockHeightWasSet();
        }

        private void assertNoHandoverToNewFederation() {
            assertUTXOsWereNotMovedToOldFederation();
            assertNewAndOldFederationsWereNotSet();
            assertLastRetiredFederationScriptWasNotSet();
            assertNewActiveFederationCreationBlockHeightWasNotSet();
        }

        private void assertUTXOsWereMovedFromNewToOldFederation(List<UTXO> utxosToMove) {
            // assert utxos were moved from new federation to old federation
            List<UTXO> oldFederationUTXOs = federationStorageProvider.getOldFederationBtcUTXOs();
            assertEquals(utxosToMove, oldFederationUTXOs);

            // assert new federation utxos were cleaned
            List<UTXO> newFederationUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(btcMainnetParams, allActivations);
            assertTrue(newFederationUTXOs.isEmpty());
        }

        private void assertUTXOsWereNotMovedToOldFederation() {
            // assert old federation utxos are still empty
            List<UTXO> oldFederationUTXOs = federationStorageProvider.getOldFederationBtcUTXOs();
            assertTrue(oldFederationUTXOs.isEmpty());

            // assert new federation utxos are not empty
            List<UTXO> newFederationUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(btcMainnetParams, allActivations);
            assertFalse(newFederationUTXOs.isEmpty());
        }

        private void assertNewAndOldFederationsWereSet() {
            // assert old federation was set as the active federation
            Federation oldFederation = federationStorageProvider.getOldFederation(federationMainNetConstants, allActivations);
            assertEquals(federationSupport.getActiveFederation(), oldFederation);

            // assert new federation was set as the proposed federation
            Federation newFederation = federationStorageProvider.getNewFederation(federationMainNetConstants, allActivations);
            assertEquals(proposedFederation, newFederation);
        }

        private void assertNewAndOldFederationsWereNotSet() {
            // assert old federation is still null
            Federation oldFederation = federationStorageProvider.getOldFederation(federationMainNetConstants, allActivations);
            assertNull(oldFederation);

            // assert new federation is still the active federation
            Federation newFederation = federationStorageProvider.getNewFederation(federationMainNetConstants, allActivations);
            assertEquals(activeFederation, newFederation);
        }

        private void assertLastRetiredFederationScriptWasSet() {
            ErpFederation activeFederationCasted = (ErpFederation) federationSupport.getActiveFederation();
            Script activeFederationMembersP2SHScript = activeFederationCasted.getDefaultP2SHScript();
            Optional<Script> lastRetiredFederationP2SHScript = federationStorageProvider.getLastRetiredFederationP2SHScript(allActivations);
            assertTrue(lastRetiredFederationP2SHScript.isPresent());
            assertEquals(activeFederationMembersP2SHScript, lastRetiredFederationP2SHScript.get());
        }

        private void assertLastRetiredFederationScriptWasNotSet() {
            Optional<Script> lastRetiredFederationP2SHScript = federationStorageProvider.getLastRetiredFederationP2SHScript(allActivations);
            assertFalse(lastRetiredFederationP2SHScript.isPresent());
        }

        private void assertNewActiveFederationCreationBlockHeightWasSet() {
            Optional<Long> nextFederationCreationBlockHeight = federationStorageProvider.getNextFederationCreationBlockHeight(allActivations);
            assertTrue(nextFederationCreationBlockHeight.isPresent());
            assertEquals(proposedFederation.getCreationBlockNumber(), nextFederationCreationBlockHeight.get());
        }

        private void assertNewActiveFederationCreationBlockHeightWasNotSet() {
            Optional<Long> nextFederationCreationBlockHeight = federationStorageProvider.getNextFederationCreationBlockHeight(allActivations);
            assertFalse(nextFederationCreationBlockHeight.isPresent());
        }
    }

    private void arrangeExecutionBlockIsAfterValidationPeriodEnded() {
        long validationPeriodEndBlock = proposedFederation.getCreationBlockNumber()
            + bridgeMainNetConstants.getFederationConstants().getValidationPeriodDurationInBlocks();
        long rskExecutionBlockTimestamp = 10L;

        rskExecutionBlock = createRskBlock(validationPeriodEndBlock, rskExecutionBlockTimestamp);
    }

    private void arrangeSvpFundTransactionSigned() {
        recreateSvpFundTransactionSigned();

        bridgeStorageProvider.setSvpFundTxSigned(svpFundTransaction);
        bridgeStorageProvider.save();
    }

    private void recreateSvpFundTransactionSigned() {
        recreateSvpFundTransactionUnsigned();
        signInputs(svpFundTransaction);
    }

    private void recreateSvpFundTransactionUnsigned() {
        svpFundTransaction = new BtcTransaction(btcMainnetParams);

        addInput(svpFundTransaction, proposedFederation);

        svpFundTransaction.addOutput(svpFundTxOutputsValue, proposedFederation.getAddress());
        Address flyoverProposedFederationAddress = PegUtils.getFlyoverFederationAddress(
            btcMainnetParams,
            bridgeMainNetConstants.getProposedFederationFlyoverPrefix(),
            proposedFederation
        );
        svpFundTransaction.addOutput(svpFundTxOutputsValue, flyoverProposedFederationAddress);
    }

    private BtcTransaction createPegout(Federation federation) {
        BtcTransaction pegout = new BtcTransaction(btcMainnetParams);
        addInput(pegout, federation);
        addOutputChange(pegout);

        return pegout;
    }

    private void addInput(BtcTransaction transaction, Federation federation) {
        // we need to add an input that we can actually sign
        var prevTx = new BtcTransaction(btcMainnetParams);
        prevTx.addOutput(Coin.COIN, federation.getAddress());
        TransactionOutput outpoint = prevTx.getOutput(0);

        int inputIndex = 0;
        transaction.addInput(outpoint);
        addSpendingFederationBaseScript(transaction, inputIndex, federation.getRedeemScript(), federation.getFormatVersion());
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
        saveSvpSpendTransactionValues();
    }

    private void recreateSvpSpendTransactionUnsigned() {
        createSpendTransaction();
        addSpendTransactionInputs();

        Coin amountToSend = Coin.valueOf(1762);
        addSpendTransactionOutput(amountToSend);
    }

    private void createSpendTransaction() {
        svpSpendTransaction = new BtcTransaction(btcMainnetParams);
        svpSpendTransaction.setVersion(BTC_TX_VERSION_2);
    }

    private void addSpendTransactionInputs() {
        recreateSvpFundTransactionSigned();
        // add inputs
        addInputFromMatchingOutputScript(svpSpendTransaction, svpFundTransaction, proposedFederation.getP2SHScript());
        Script proposedFederationRedeemScript = proposedFederation.getRedeemScript();
        addSpendingFederationBaseScript(svpSpendTransaction, 0, proposedFederationRedeemScript, proposedFederation.getFormatVersion());

        Script flyoverRedeemScript = getFlyoverFederationRedeemScript(bridgeMainNetConstants.getProposedFederationFlyoverPrefix(), proposedFederationRedeemScript);
        addInputFromMatchingOutputScript(svpSpendTransaction, svpFundTransaction, ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript));
        addSpendingFederationBaseScript(svpSpendTransaction, 1, flyoverRedeemScript, proposedFederation.getFormatVersion());
    }

    private void addSpendTransactionOutput(Coin amountToSend) {
        svpSpendTransaction.addOutput(amountToSend, federationSupport.getActiveFederationAddress());
    }

    private void saveSvpSpendTransactionValues() {
        bridgeStorageProvider.setSvpSpendTxHashUnsigned(svpSpendTransaction.getHash());

        Map.Entry<Keccak256, BtcTransaction> svpSpendTxWaitingForSignatures = new AbstractMap.SimpleEntry<>(svpSpendTxCreationHash, svpSpendTransaction);
        bridgeStorageProvider.setSvpSpendTxWaitingForSignatures(svpSpendTxWaitingForSignatures);

        bridgeStorageProvider.save();
    }

    private void setUpForTransactionRegistration(BtcTransaction transaction) throws BlockStoreException {
        // recreate a valid chain that has the tx, so it passes the previous checks in registerBtcTransaction
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(btcMainnetParams, 100, 100);
        BtcBlockStoreWithCache btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(repository, bridgeMainNetConstants, bridgeStorageProvider, allActivations);

        pmtWithTransactions = createValidPmtForTransactions(List.of(transaction), btcMainnetParams);
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

    private void assertNoProposedFederation() {
        assertFalse(federationSupport.getProposedFederation().isPresent());
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

    private void assertTransactionWasNotProcessed(Sha256Hash transactionHash) throws IOException {
        Optional<Long> rskBlockHeightAtWhichBtcTxWasProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(transactionHash);
        assertFalse(rskBlockHeightAtWhichBtcTxWasProcessed.isPresent());
    }

    private void assertTxIsRejectedPeginAndMarkedAsProcessed(BtcTransaction rejectedPegin)
        throws IOException {
        Optional<Long> rskBlockHeightAtWhichBtcTxWasProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(rejectedPegin.getHash());
        assertTrue(rskBlockHeightAtWhichBtcTxWasProcessed.isPresent());

        byte[] btcTxHashSerialized = rejectedPegin.getHash().getBytes();

        Function rejectedPeginEvent = BridgeEvents.REJECTED_PEGIN.getEvent();
        List<DataWord> rejectedPeginEncodedTopics = getEncodedTopics(rejectedPeginEvent, btcTxHashSerialized);
        byte[] rejectedPeginEncodedData = getEncodedData(rejectedPeginEvent, INVALID_AMOUNT.getValue());

        assertEventWasEmittedWithExpectedTopics(rejectedPeginEncodedTopics);
        assertEventWasEmittedWithExpectedData(rejectedPeginEncodedData);

        Function unrefundablePeginEvent = BridgeEvents.UNREFUNDABLE_PEGIN.getEvent();
        List<DataWord> encodedTopics = getEncodedTopics(unrefundablePeginEvent, btcTxHashSerialized);
        byte[] encodedData = getEncodedData(unrefundablePeginEvent, NonRefundablePeginReason.INVALID_AMOUNT.getValue());

        assertEventWasEmittedWithExpectedTopics(encodedTopics);
        assertEventWasEmittedWithExpectedData(encodedData);
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

    private void assertReleaseTransactionInfoWasProcessed(BtcTransaction releaseTransaction, List<Coin> expectedOutpointsValues) {
        assertLogPegoutTransactionCreated(releaseTransaction);
        assertReleasesOutpointsValuesWereSaved(releaseTransaction, expectedOutpointsValues);
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

    private void assertReleasesOutpointsValuesWereSaved(BtcTransaction releaseTransaction, List<Coin> expectedOutpointsValues) {
        Sha256Hash releaseTransactionHash = releaseTransaction.getHash();
        // assert entry was saved in storage
        byte[] savedReleaseOutpointsValues = repository.getStorageBytes(
            bridgeContractAddress,
            getStorageKeyForReleaseOutpointsValues(releaseTransaction.getHash())
        );
        assertNotNull(savedReleaseOutpointsValues);

        // assert saved values are the expected ones
        Optional<List<Coin>> releaseOutpointsValues = bridgeStorageProvider.getReleaseOutpointsValues(releaseTransactionHash);
        assertTrue(releaseOutpointsValues.isPresent());
        assertEquals(expectedOutpointsValues, releaseOutpointsValues.get());
    }

    private void assertEventWasEmittedWithExpectedTopics(List<DataWord> expectedTopics) {
        Optional<LogInfo> topicOpt = getLogsTopics(logs, expectedTopics);
        assertTrue(topicOpt.isPresent());
    }

    private void assertEventWasEmittedWithExpectedData(byte[] expectedData) {
        Optional<LogInfo> data = getLogsData(logs, expectedData);
        assertTrue(data.isPresent());
    }
}
