package co.rsk.peg.federation;

import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.*;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
import co.rsk.peg.bitcoin.*;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.lockingcap.*;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;

class FederationChangeIT {
    private static final RskAddress BRIDGE_ADDRESS = PrecompiledContracts.BRIDGE_ADDR;
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMS = BRIDGE_CONSTANTS.getBtcParams();
    private static final List<BtcECKey> ORIGINAL_FEDERATION_MEMBERS_KEYS =
        BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09"}, true);
    private static final List<FederationMember> ORIGINAL_FEDERATION_MEMBERS = FederationTestUtils.getFederationMembersWithBtcKeys(ORIGINAL_FEDERATION_MEMBERS_KEYS);
    private static final List<BtcECKey> NEW_FEDERATION_MEMBERS_KEYS =
        BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "newMember01", "newMember02", "newMember03", "newMember04", "newMember05", "newMember06", "newMember07", "newMember08", "newMember09"}, true);
    private static final List<FederationMember> NEW_FEDERATION_MEMBERS = FederationTestUtils.getFederationMembersWithBtcKeys(NEW_FEDERATION_MEMBERS_KEYS);
    private static final SignatureCache SIGNATURE_CACHE = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    private static final Transaction UPDATE_COLLECTIONS_TX = buildUpdateCollectionsTx();
    private static final Transaction FIRST_AUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(SIGNATURE_CACHE, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
    private static final Transaction SECOND_AUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(SIGNATURE_CACHE, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());
    private static final ActivationConfig.ForBlock ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0);

    private Repository repository;
    private BridgeStorageProvider bridgeStorageProvider;
    private BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private BtcBlockStoreWithCache btcBlockStore;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;
    private List<LogInfo> logs;
    private BridgeEventLogger bridgeEventLogger;
    private FeePerKbSupport feePerKbSupport;
    private Block currentBlock;
    private StorageAccessor bridgeStorageAccessor;
    private FederationStorageProvider federationStorageProvider;
    private FederationSupport federationSupport;
    private LockingCapSupport lockingCapSupport;
    private BridgeSupport bridgeSupport;
    private PartialMerkleTree pmtWithTransactions;
    private int btcBlockWithPmtHeight;

    @Test
    void whenAllActivationsArePresentAndFederationChanges_shouldSuccessfullyChangeFederation() throws Exception {
        // Arrange
        setUp();

        // Create a default original federation using the list of UTXOs
        var originalFederation = createOriginalFederation();
        var originalUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMS, ACTIVATIONS);

        // Act & Assert
        assertPeginsShouldWorkToFed(originalFederation.getAddress(), federationSupport.getActiveFederationBtcUTXOs(), "sender0");
        // Create pending federation using the new federation keys
        voteToCreateEmptyPendingFederation();
        voteToAddFederatorPublicKeysToPendingFederation();

        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertPendingFederationIsBuiltAsExpected(pendingFederation);

        voteToCommitPendingFederation();
        var newFederationOpt = federationSupport.getProposedFederation();
        assertTrue(newFederationOpt.isPresent());
        var newFederation = newFederationOpt.get();
        var expectedProposedFederation = createExpectedProposedFederation();
        assertEquals(expectedProposedFederation, newFederation);

        assertPeginsShouldNotWorkToFed(newFederation.getAddress(), "sender1");

        // Proceed with SVP process
        callUpdateCollectionsAndAssertSvpFundTxIsCreated();
        registerSignedSvpFundTx();

        assertPeginsShouldWorkToFed(originalFederation.getAddress(), federationSupport.getActiveFederationBtcUTXOs(), "sender2");
        assertPeginsShouldNotWorkToFed(newFederation.getAddress(), "sender3");

        callUpdateCollectionsAndAssertSvpSpendTxIsCreated();
        addSignaturesToAndRegisterSvpSpendTx();

        // Validations post commit
        assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation(originalFederation);
        assertUTXOsReferenceMovedFromNewToOldFederation(originalUTXOs);
        assertNewAndOldFederationsReferences(newFederation, originalFederation);
        assertNextFederationCreationBlockHeight(newFederation.getCreationBlockNumber());

        assertPeginsShouldWorkToFed(originalFederation.getAddress(), federationSupport.getActiveFederationBtcUTXOs(), "sender4");
        assertPeginsShouldNotWorkToFed(newFederation.getAddress(), "sender5");

        // Move blockchain until the activation phase
        activateNewFederation();
        assertActiveAndRetiringFederationsHaveExpectedAddress(newFederation.getAddress(), originalFederation.getAddress());
        assertMigrationHasNotStarted();

        assertPeginsShouldWorkToFed(originalFederation.getAddress(), federationSupport.getRetiringFederationBtcUTXOs(), "sender6");
        assertPeginsShouldWorkToFed(newFederation.getAddress(), federationSupport.getActiveFederationBtcUTXOs(), "sender7");

        // Move blockchain until the migration phase
        activateMigration();

        // Calling update collections should start migration
        callUpdateCollections();
        assertMigrationHasStarted();
        assertPegoutTxSigHashesAreSaved();
        assertReleaseBtcRequestedEventEventWasEmitted();
        assertPegoutTransactionCreatedEventWasEmitted();
        verifyPegouts();

        // Check again live federations references are as expected
        assertNewAndOldFederationsReferences(newFederation, originalFederation);
        assertActiveAndRetiringFederationsHaveExpectedAddress(newFederation.getAddress(), originalFederation.getAddress());

        assertPeginsShouldWorkToFed(originalFederation.getAddress(), federationSupport.getRetiringFederationBtcUTXOs(), "sender8");
        assertPeginsShouldWorkToFed(newFederation.getAddress(), federationSupport.getActiveFederationBtcUTXOs(), "sender9");

        // Move blockchain until the end of the migration phase
        long migrationCreationRskBlockNumber = currentBlock.getNumber();
        endMigration();
        assertPegoutConfirmedEventEventWasEmitted(migrationCreationRskBlockNumber);

        assertOnlyActiveFedIsLive(newFederation);
        assertPeginsShouldNotWorkToFed(originalFederation.getAddress(), "sender10");
        assertPeginsShouldWorkToFed(newFederation.getAddress(), federationSupport.getActiveFederationBtcUTXOs(), "sender11");
    }

    private void setUp() throws Exception {
        repository = BridgeSupportTestUtil.createRepository();
        repository.addBalance(BRIDGE_ADDRESS, co.rsk.core.Coin.fromBitcoin(BRIDGE_CONSTANTS.getMaxRbtc()));

        bridgeStorageProvider =
            new BridgeStorageProvider(repository, BRIDGE_ADDRESS, NETWORK_PARAMS, ACTIVATIONS);

        btcBlockStoreFactory =
            new RepositoryBtcBlockStoreWithCache.Factory(NETWORK_PARAMS, 100, 100);
        btcBlockStore =
            btcBlockStoreFactory.newInstance(repository, BRIDGE_CONSTANTS, bridgeStorageProvider, ACTIVATIONS);
        // Setting a chain head different from genesis to avoid having to read the checkpoints file
        addNewBtcBlockOnTipOfChain(btcBlockStore);
        repository.save();

        peginInstructionsProvider = new PeginInstructionsProvider();
        btcLockSenderProvider = new BtcLockSenderProvider();

        logs = new ArrayList<>();
        bridgeEventLogger = new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ACTIVATIONS,
            logs
        );

        bridgeStorageAccessor = new InMemoryStorage();

        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        var blockNumber = 0L;
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(currentBlock)
            .withActivations(ACTIVATIONS)
            .build();

        var lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(
            lockingCapStorageProvider,
            ACTIVATIONS,
            BRIDGE_CONSTANTS.getLockingCapConstants(),
            SIGNATURE_CACHE);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.SATOSHI);

        bridgeSupport = BridgeSupportBuilder.builder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(currentBlock)
            .withActivations(ACTIVATIONS)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();
    }

    private Federation createOriginalFederation() {
        var originalFederationArgs = new FederationArgs(
            ORIGINAL_FEDERATION_MEMBERS,
            Instant.EPOCH,
            0,
            NETWORK_PARAMS);
        var erpPubKeys = FEDERATION_CONSTANTS.getErpFedPubKeysList();
        var activationDelay = FEDERATION_CONSTANTS.getErpFedActivationDelay();

        Federation originalFederation = FederationFactory.buildP2shErpFederation(originalFederationArgs, erpPubKeys, activationDelay);
        // Set original federation
        federationStorageProvider.setNewFederation(originalFederation);

        // Set new UTXOs
        var originalUTXOs = createUTXOs(originalFederation.getAddress());
        bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), originalUTXOs, BridgeSerializationUtils::serializeUTXOList);

        return originalFederation;
    }  

    private Federation createExpectedProposedFederation() {
        var expectedFederationArgs =
            new FederationArgs(NEW_FEDERATION_MEMBERS, Instant.EPOCH, 0, NETWORK_PARAMS);
        var erpPubKeys = FEDERATION_CONSTANTS.getErpFedPubKeysList();
        var activationDelay = FEDERATION_CONSTANTS.getErpFedActivationDelay();

        return FederationFactory.buildP2shErpFederation(expectedFederationArgs, erpPubKeys, activationDelay);
    }

    private int voteToCreatePendingFederation(Transaction tx) {
        var createFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});
        return federationSupport.voteFederationChange(tx, createFederationAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private int voteToAddFederatorPublicKeysToPendingFederation(Transaction tx, BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        ABICallSpec addFederatorAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(),
            new byte[][]{ btcPublicKey.getPubKey(), rskPublicKey.getPubKey(), mstPublicKey.getPubKey() }
        );

        return federationSupport.voteFederationChange(tx, addFederatorAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private int voteCommitPendingFederation(Transaction tx) {
        var pendingFederationHash = federationSupport.getPendingFederationHash();
        var commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{ pendingFederationHash.getBytes() });

        return federationSupport.voteFederationChange(tx, commitFederationAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }
  
    private void voteToCreateEmptyPendingFederation() {
        // Voting with enough authorizers to create the pending federation
        var resultFromFirstAuthorizer = voteToCreatePendingFederation(FIRST_AUTHORIZED_TX);
        var resultFromSecondAuthorizer = voteToCreatePendingFederation(SECOND_AUTHORIZED_TX);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);

        assertEquals(0, federationSupport.getPendingFederationSize());
        assertNotNull(federationSupport.getPendingFederationHash());
    }

    private void voteToAddFederatorPublicKeysToPendingFederation(BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        int resultFromFirstAuthorizer = voteToAddFederatorPublicKeysToPendingFederation(FIRST_AUTHORIZED_TX, btcPublicKey, rskPublicKey, mstPublicKey);
        int resultFromSecondAuthorizer = voteToAddFederatorPublicKeysToPendingFederation(SECOND_AUTHORIZED_TX, btcPublicKey, rskPublicKey, mstPublicKey);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);
    }

    private void voteToAddFederatorPublicKeysToPendingFederation() {
        var expectedPendingFederationSize = 0;

        for (FederationMember member : NEW_FEDERATION_MEMBERS) {
            var memberBtcKey = member.getBtcPublicKey();
            var memberRskKey = member.getRskPublicKey();
            var memberMstKey = member.getMstPublicKey();

            voteToAddFederatorPublicKeysToPendingFederation(memberBtcKey, memberRskKey, memberMstKey);

            assertEquals(++expectedPendingFederationSize, federationSupport.getPendingFederationSize());
            assertTrue(federationStorageProvider.getPendingFederation().getMembers().contains(member));
        }
    }

    private void voteToCommitPendingFederation() {
        // Pending Federation should exist
        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertNotNull(pendingFederation);

        var firstVoteResult = voteCommitPendingFederation(FIRST_AUTHORIZED_TX);
        var secondVoteResult = voteCommitPendingFederation(SECOND_AUTHORIZED_TX);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteResult);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteResult);

        // Since the proposed federation is committed, it should be null in storage
        assertNull(federationStorageProvider.getPendingFederation());
    }

    private void callUpdateCollectionsAndAssertSvpFundTxIsCreated() throws Exception {
        // Get UTXO size before creating fund tx
        var activeFederationUtxosSizeBeforeCreatingFundTx =
            federationSupport.getActiveFederationBtcUTXOs().size();

        // Next call to update collections will create svp fund tx
        bridgeSupport.updateCollections(UPDATE_COLLECTIONS_TX);
        bridgeSupport.save();

        var svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
        assertTrue(svpFundTxHashUnsigned.isPresent());
        assertEquals(activeFederationUtxosSizeBeforeCreatingFundTx - 1, federationSupport.getActiveFederationBtcUTXOs().size());
    }

    private void registerSignedSvpFundTx() throws Exception {
        var pegoutsTxs =
            bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().stream().toList();
        assertEquals(1, pegoutsTxs.size());
        var svpFundTx = new BtcTransaction(NETWORK_PARAMS, pegoutsTxs.get(0).getBtcTransaction().bitcoinSerialize());

        signInputs(svpFundTx, ORIGINAL_FEDERATION_MEMBERS_KEYS.subList(0, 5));

        int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
        registerTransaction(svpFundTx);

        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx + 1, federationSupport.getActiveFederationBtcUTXOs().size());
        var svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
        assertFalse(svpFundTxHashUnsigned.isPresent());
        var svpFundTransactionSigned = bridgeStorageProvider.getSvpFundTxSigned();
        assertTrue(svpFundTransactionSigned.isPresent());

        // simulate removal to leave state clean
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().removeEntry(pegoutsTxs.get(0)));
    }

    private void callUpdateCollectionsAndAssertSvpSpendTxIsCreated() throws Exception {
        // Next call to update collections will create svp spend tx
        bridgeSupport.updateCollections(UPDATE_COLLECTIONS_TX);
        bridgeSupport.save();

        var svpFundTransactionSigned = bridgeStorageProvider.getSvpFundTxSigned();
        assertFalse(svpFundTransactionSigned.isPresent());
        var svpSpendTransactionHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
        assertTrue(svpSpendTransactionHashUnsigned.isPresent());
        var svpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
        assertTrue(svpSpendTxWaitingForSignatures.isPresent());
    }

    private void addSignaturesToAndRegisterSvpSpendTx() throws Exception {
        var svpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
        assertTrue(svpSpendTxWaitingForSignatures.isPresent());

        // Add the signatures for the svp spend tx
        var svpSpendTxCreationHash = svpSpendTxWaitingForSignatures.get().getKey();
        var svpSpendTx = svpSpendTxWaitingForSignatures.get().getValue();
        var svpSpendTxSigHashes = IntStream.range(0, svpSpendTx.getInputs().size())
            .mapToObj(i -> BitcoinUtils.generateSigHashForP2SHTransactionInput(svpSpendTx, i))
            .toList();
        for (BtcECKey proposedFederatorSignerKey : NEW_FEDERATION_MEMBERS_KEYS.subList(0, 5)) {
            List<byte[]> signatures = BitcoinTestUtils.generateSignerEncodedSignatures(proposedFederatorSignerKey, svpSpendTxSigHashes);
            bridgeSupport.addSignature(proposedFederatorSignerKey, signatures, svpSpendTxCreationHash);
        }

        // Verify that the svp spend tx was released
        assertLogReleaseBtc(svpSpendTxCreationHash, svpSpendTx);
        svpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
        assertFalse(svpSpendTxWaitingForSignatures.isPresent());

        var activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
        // Register the svp spend tx
        registerTransaction(svpSpendTx);

        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx + 1, federationSupport.getActiveFederationBtcUTXOs().size());
        var svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
        assertFalse(svpSpendTxHashUnsigned.isPresent());
        var newFederationOpt = federationSupport.getProposedFederation();
        assertFalse(newFederationOpt.isPresent());
    }

    private void assertLogReleaseBtc(Keccak256 rskTxHash, BtcTransaction btcTx) {
        CallTransaction.Function releaseBtcEvent = BridgeEvents.RELEASE_BTC.getEvent();

        byte[] rskTxHashSerialized = rskTxHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(releaseBtcEvent, rskTxHashSerialized);

        byte[] btcTxSerialized = btcTx.bitcoinSerialize();
        byte[] encodedData = getEncodedData(releaseBtcEvent, btcTxSerialized);

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
    
    private void activateNewFederation() {
        // Move the required blocks ahead for the new powpeg to become active
        var blockNumber = 
            currentBlock.getNumber() + FEDERATION_CONSTANTS.getFederationActivationAge(ACTIVATIONS);
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        advanceBlockchainTo(currentBlock);
    }

    private void activateMigration() {
        // Move the required blocks ahead for the new federation to start migrating,
        // adding 1 as the migration is exclusive
        var blockNumber = 
            currentBlock.getNumber() + FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationBegin() + 1L;
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        advanceBlockchainTo(currentBlock);
    }

    private void endMigration() throws Exception {
        // Move the required blocks ahead for the new federation to finish migrating,
        // adding 1 as the migration is exclusive
        var blockNumber = 
            currentBlock.getNumber() + FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationEnd(ACTIVATIONS) + 1L;
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        advanceBlockchainTo(currentBlock);

        // The first update collections after the migration finished should get rid of the retiring powpeg
        bridgeSupport.updateCollections(UPDATE_COLLECTIONS_TX);
        bridgeSupport.save();
    }

    private void callUpdateCollections() throws Exception {
        bridgeSupport.updateCollections(UPDATE_COLLECTIONS_TX);
        bridgeSupport.save();
    }

    private void assertPeginsShouldWorkToFed(Address federationAddress, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        assertLegacyP2pkhPeginWorks(federationAddress, federationUtxosReference, senderSeed);
        assertLegacyP2shP2wpkhPeginWorks(federationAddress, federationUtxosReference, senderSeed);
        assertPeginV1Works(federationAddress, federationUtxosReference, senderSeed);
    }

    private void assertLegacyP2pkhPeginWorks(Address federationAddress, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        var legacyP2pkhPeginToFed = createLegacyP2pkhPegin(federationAddress, senderSeed);
        assertPeginWorks(legacyP2pkhPeginToFed, federationUtxosReference);
    }

    private void assertLegacyP2shP2wpkhPeginWorks(Address federationAddress, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        var legacyP2shP2wpkhPeginToFed = createLegacyP2shP2wpkhPegin(federationAddress, senderSeed);
        assertPeginWorks(legacyP2shP2wpkhPeginToFed, federationUtxosReference);
    }

    private void assertPeginV1Works(Address federationAddress, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        var peginV1ToFed = createPeginV1(federationAddress, senderSeed);
        assertPeginWorks(peginV1ToFed, federationUtxosReference);
    }

    private void assertPeginWorks(BtcTransaction pegin, List<UTXO> federationUtxosReference) throws Exception {
        int utxosSizeBeforeRegisteringPeginV1 = federationUtxosReference.size();
        registerTransaction(pegin);

        // assert pegin was processed
        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(pegin.getHash()));
        // assert utxo was registered
        assertEquals(utxosSizeBeforeRegisteringPeginV1 + 1, federationUtxosReference.size());
    }

    private void assertPeginsShouldNotWorkToFed(Address federationAddress, String senderSeed) throws Exception {
        assertLegacyP2pkhPeginDoesNotWork(federationAddress, senderSeed);
        assertLegacyP2shP2wpkhPeginDoesNotWork(federationAddress, senderSeed);
        assertPeginV1DoesNotWork(federationAddress, senderSeed);
    }

    private void assertLegacyP2pkhPeginDoesNotWork(Address federationAddress, String senderSeed) throws Exception {
        var legacyP2pkhPeginToFed = createLegacyP2pkhPegin(federationAddress, senderSeed);
        assertPeginDoesNotWork(legacyP2pkhPeginToFed);
    }

    private void assertLegacyP2shP2wpkhPeginDoesNotWork(Address federationAddress, String senderSeed) throws Exception {
        var legacyP2shP2wpkhPeginToFed = createLegacyP2shP2wpkhPegin(federationAddress, senderSeed);
        assertPeginDoesNotWork(legacyP2shP2wpkhPeginToFed);
    }

    private void assertPeginV1DoesNotWork(Address federationAddress, String senderSeed) throws Exception {
        var peginV1ToFed = createPeginV1(federationAddress, senderSeed);
        assertPeginDoesNotWork(peginV1ToFed);
    }

    private void assertPeginDoesNotWork(BtcTransaction pegin) throws Exception {
        int activeFederationUtxosSizeBeforeRegisteringPegin = federationSupport.getActiveFederationBtcUTXOs().size();
        int retiringFederationUtxosSizeBeforeRegisteringPegin = federationSupport.getRetiringFederationBtcUTXOs().size();
        registerTransaction(pegin);

        // assert pegin was not processed
        assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(pegin.getHash()));
        // assert no utxos were registered
        assertEquals(activeFederationUtxosSizeBeforeRegisteringPegin, federationSupport.getActiveFederationBtcUTXOs().size());
        assertEquals(retiringFederationUtxosSizeBeforeRegisteringPegin, federationSupport.getRetiringFederationBtcUTXOs().size());
    }

    private BtcTransaction createLegacyP2pkhPegin(Address federationAddress, String sender) {
        var peginBtcTx = new BtcTransaction(NETWORK_PARAMS);
        var senderPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed(sender);

        peginBtcTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, senderPublicKey));
        peginBtcTx.addOutput(Coin.COIN, federationAddress);

        return peginBtcTx;
    }

    private BtcTransaction createLegacyP2shP2wpkhPegin(Address federationAddress, String sender) {
        var peginBtcTx = new BtcTransaction(NETWORK_PARAMS);
        var senderPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed(sender);
        byte[] redeemScript = ByteUtil.merge(new byte[]{ 0x00, 0x14}, senderPublicKey.getPubKeyHash());
        Script witnessScript = new ScriptBuilder()
            .data(redeemScript)
            .build();

        peginBtcTx.addInput(BitcoinTestUtils.createHash(1), 0, witnessScript);
        TransactionWitness txWit = new TransactionWitness(2);
        txWit.setPush(0, new byte[72]); // push for signatures
        txWit.setPush(1, senderPublicKey.getPubKey());
        peginBtcTx.setWitness(0, txWit);

        peginBtcTx.addOutput(Coin.COIN, federationAddress);

        return peginBtcTx;
    }

    private BtcTransaction createPeginV1(Address federationAddress, String sender) {
        var peginBtcTx = new BtcTransaction(NETWORK_PARAMS);
        var senderPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed(sender);

        peginBtcTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, senderPublicKey));
        peginBtcTx.addOutput(Coin.COIN, federationAddress);
        // Adding OP_RETURN output to identify this peg-in as v1 and avoid sender identification
        Script opReturnOutputScript =
            PegTestUtils.createOpReturnScriptForRsk(1, BRIDGE_ADDRESS, Optional.empty());
        peginBtcTx.addOutput(Coin.ZERO, opReturnOutputScript);

        return peginBtcTx;
    }

    private void registerTransaction(BtcTransaction btcTx) throws Exception {
        setUpForTransactionRegistration(btcTx);

        var registrationTx = mock(Transaction.class);
        bridgeSupport.registerBtcTransaction(
            registrationTx,
            btcTx.bitcoinSerialize(),
            btcBlockWithPmtHeight,
            pmtWithTransactions.bitcoinSerialize()
        );
        bridgeSupport.save();
    }

    private void setUpForTransactionRegistration(BtcTransaction btcTx) throws Exception {
        pmtWithTransactions = createValidPmtForTransactions(List.of(btcTx), NETWORK_PARAMS);
        btcBlockWithPmtHeight = BRIDGE_CONSTANTS.getBtcHeightWhenPegoutTxIndexActivates() + BRIDGE_CONSTANTS.getPegoutTxIndexGracePeriodInBtcBlocks(); // we want pegout tx index to be activated
        var chainHeight = btcBlockWithPmtHeight + BRIDGE_CONSTANTS.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStore, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, NETWORK_PARAMS);
        bridgeStorageProvider.save();
    }

    private void advanceBlockchainTo(Block executionBlock) {
        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(ACTIVATIONS)
            .build();

        bridgeSupport = BridgeSupportBuilder.builder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(executionBlock)
            .withActivations(ACTIVATIONS)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();
    }

    private static void addNewBtcBlockOnTipOfChain(BtcBlockStore blockStore) throws Exception {
        var chainHead = blockStore.getChainHead();
        var btcBlock = new BtcBlock(
            NETWORK_PARAMS,
            1,
            chainHead.getHeader().getHash(),
            BitcoinTestUtils.createHash(chainHead.getHeight() + 1),
            0,
            0,
            0,
            List.of());
        var storedBlock = new StoredBlock(
            btcBlock,
            BigInteger.ZERO,
            chainHead.getHeight() + 1
        );

        blockStore.put(storedBlock);
        blockStore.setChainHead(storedBlock);
    }

    private List<UTXO> createUTXOs(Address owner) {
        Script outputScript = ScriptBuilder.createOutputScript(owner);
        List<UTXO> utxos = new ArrayList<>();

        int howMany = 50;
        for (int i = 1; i < howMany; i++) {
            Coin value = Coin.COIN;
            Sha256Hash utxoHash = BitcoinTestUtils.createHash(i);
            utxos.add(new UTXO(utxoHash, 0, value, 0, false, outputScript));
        }

        return utxos;
    }

    private Script getFederationDefaultRedeemScript(Federation federation) {
        return federation instanceof ErpFederation ?
            ((ErpFederation) federation).getDefaultRedeemScript() :
            federation.getRedeemScript();
    }
   
    private static Script getFederationDefaultP2SHScript(Federation federation) {
        return federation instanceof ErpFederation ?
            ((ErpFederation) federation).getDefaultP2SHScript() :
            federation.getP2SHScript();
    }

    private static Transaction buildUpdateCollectionsTx() {
        var nonce = 3;
        var value = 0;
        var gasPrice = BigInteger.valueOf(0);
        var gasLimit = BigInteger.valueOf(100000);
        var rskTx = CallTransaction.createCallTransaction(nonce, gasPrice.longValue(),
                gasLimit.longValue(), PrecompiledContracts.BRIDGE_ADDR, value,
                Bridge.UPDATE_COLLECTIONS, Constants.MAINNET_CHAIN_ID);
        var randomKey = BtcECKey.fromPrivate(Hex.decode("45c5b07fc1a6f58892615b7c31dca6c96db58c4bbc538a6b8a22999aaa860c32"));
        rskTx.sign(randomKey.getPrivKeyBytes());
        return rskTx;
    }

    private void signInputs(BtcTransaction transaction, List<BtcECKey> keysToSign) {
        List<TransactionInput> inputs = transaction.getInputs();
        IntStream.range(0, inputs.size()).forEach(i ->
            BitcoinTestUtils.signTransactionInputFromP2shMultiSig(transaction, i, keysToSign)
        );
    }
    
    // Assert federation change related methods
    private void assertUTXOsReferenceMovedFromNewToOldFederation(List<UTXO> utxos) {
        // Assert old federation exists in storage
        assertNotNull(
            federationStorageProvider.getOldFederation(FEDERATION_CONSTANTS, ACTIVATIONS));
        // Assert new federation exists in storage
        assertNotNull(
            federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, ACTIVATIONS));
        // Assert old federation holds the original utxos
        List<UTXO> utxosToMigrate = federationStorageProvider.getOldFederationBtcUTXOs();
        assertTrue(utxosToMigrate.containsAll(utxos));
        // Assert the new federation does not have any utxos yet
        assertTrue(federationStorageProvider
            .getNewFederationBtcUTXOs(NETWORK_PARAMS, ACTIVATIONS)
            .isEmpty());
    }

    private void assertNewAndOldFederationsReferences(Federation expectedNewFederation, Federation expectedOldFederation) {
        FederationConstants federationConstants = FEDERATION_CONSTANTS;
        assertEquals(expectedNewFederation, federationStorageProvider.getNewFederation(federationConstants, ACTIVATIONS));
        assertEquals(expectedOldFederation, federationStorageProvider.getOldFederation(federationConstants, ACTIVATIONS));
    }

    private void assertActiveAndRetiringFederationsHaveExpectedAddress(Address expectedNewFederationAddress, Address expectedOldFederationAddress) {
        assertEquals(expectedNewFederationAddress, bridgeSupport.getActiveFederationAddress());
        assertEquals(expectedOldFederationAddress, bridgeSupport.getRetiringFederationAddress());
    }

    private void assertNextFederationCreationBlockHeight(long newFederationCreationBlockNumber) {
        Optional<Long> nextFederationCreationBlockHeight = federationStorageProvider.getNextFederationCreationBlockHeight(ACTIVATIONS);
        assertTrue(nextFederationCreationBlockHeight.isPresent());
        assertEquals(newFederationCreationBlockNumber, nextFederationCreationBlockHeight.get());
    }

    private void assertMigrationHasNotStarted() throws Exception {
        // Current block is behind fedActivationAge + fundsMigrationAgeBegin
        var blockNumber = FEDERATION_CONSTANTS.getFederationActivationAge(ACTIVATIONS) +
            FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationBegin();
        assertTrue(currentBlock.getNumber() <= blockNumber);

        // Pegouts waiting for confirmations should be empty
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());
    }
     
    private void assertMigrationHasStarted() throws Exception {
        // Pegouts waiting for confirmations should not be empty
        // Expecting only one element since the retiring federation had less than 50 UTXOs
        assertEquals(1, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    private void assertOnlyActiveFedIsLive(Federation newFederation) {
        // New active federation still there, retiring federation no longer there
        assertEquals(newFederation, bridgeSupport.getActiveFederation());
        assertNull(bridgeSupport.getRetiringFederationAddress());
    }
    
    private void assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation(
          Federation originalFederation) {
        var lastRetiredFederationP2SHScriptOptional = 
            federationStorageProvider.getLastRetiredFederationP2SHScript(ACTIVATIONS);
        assertTrue(lastRetiredFederationP2SHScriptOptional.isPresent());
        Script lastRetiredFederationP2SHScript = lastRetiredFederationP2SHScriptOptional.get();

        assertNotEquals(lastRetiredFederationP2SHScript, originalFederation.getP2SHScript());
        assertEquals(lastRetiredFederationP2SHScript, getFederationDefaultP2SHScript(originalFederation));
    }

    private void assertPendingFederationIsBuiltAsExpected(PendingFederation pendingFederation) {
        assertNotNull(pendingFederation);
        assertEquals(9, pendingFederation.getSize());
        assertTrue(pendingFederation.getMembers().containsAll(NEW_FEDERATION_MEMBERS));
    }

    private void assertPegoutTransactionCreatedEventWasEmitted() throws Exception {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries().stream()
            .map(Entry::getBtcTransaction)
            .toList();

        assertEquals(1, pegoutsTxs.size());
        assertLogPegoutTransactionCreated(pegoutsTxs.get(0));
    }

    private void assertLogPegoutTransactionCreated(BtcTransaction pegoutTransaction) {
        CallTransaction.Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();

        Sha256Hash pegoutTransactionHash = pegoutTransaction.getHash();
        byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(pegoutTransactionCreatedEvent, pegoutTransactionHashSerialized);

        List<Coin> outpointValues = extractOutpointValues(pegoutTransaction);
        byte[] serializedOutpointValues = UtxoUtils.encodeOutpointValues(outpointValues);
        byte[] encodedData = getEncodedData(pegoutTransactionCreatedEvent, serializedOutpointValues);

        assertEventWasEmittedWithExpectedTopics(encodedTopics);
        assertEventWasEmittedWithExpectedData(encodedData);
    }

    private void assertReleaseBtcRequestedEventEventWasEmitted() throws Exception {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries().stream()
            .toList();
        
        assertEquals(1, pegoutsTxs.size());

        var releaseCreationTxHash = pegoutsTxs.get(0).getPegoutCreationRskTxHash();
        var btcTx = pegoutsTxs.get(0).getBtcTransaction();
        var amount = btcTx.getFee().add(btcTx.getOutputSum());
        assertLogReleaseRequested(releaseCreationTxHash, btcTx.getHash(), amount);
    }

    private void assertLogReleaseRequested(Keccak256 releaseCreationTxHash, Sha256Hash pegoutTransactionHash, Coin requestedAmount) {
        CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();

        byte[] releaseCreationTxHashSerialized = releaseCreationTxHash.getBytes();
        byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(releaseRequestedEvent, releaseCreationTxHashSerialized, pegoutTransactionHashSerialized);

        byte[] encodedData = getEncodedData(releaseRequestedEvent, requestedAmount.getValue());

        assertEventWasEmittedWithExpectedTopics(encodedTopics);
        assertEventWasEmittedWithExpectedData(encodedData);
    }

    private void assertPegoutConfirmedEventEventWasEmitted(long pegoutCreationRskBlockNumber) throws Exception {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForSignatures()
            .entrySet().stream()
            .toList();
        
        assertEquals(1, pegoutsTxs.size());

        var btcTx = pegoutsTxs.get(0).getValue();
        assertLogPegoutConfirmed(btcTx.getHash(), pegoutCreationRskBlockNumber);
    }

    private void assertLogPegoutConfirmed(Sha256Hash btcTxHash, long pegoutCreationRskBlockNumber) {
        CallTransaction.Function pegoutConfirmedEvent = BridgeEvents.PEGOUT_CONFIRMED.getEvent();

        byte[] btcTxHashSerialized = btcTxHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(pegoutConfirmedEvent, btcTxHashSerialized);

        byte[] encodedData = getEncodedData(pegoutConfirmedEvent, pegoutCreationRskBlockNumber);

        assertEventWasEmittedWithExpectedTopics(encodedTopics);
        assertEventWasEmittedWithExpectedData(encodedData);
    }
    
    private void verifyPegouts() throws Exception {
        var activeFederation = federationStorageProvider.getNewFederation(
            FEDERATION_CONSTANTS, ACTIVATIONS);
        var retiringFederation = federationStorageProvider.getOldFederation(
            FEDERATION_CONSTANTS, ACTIVATIONS);

        for (PegoutsWaitingForConfirmations.Entry pegoutEntry : bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries()) {
            var pegoutBtcTransaction = pegoutEntry.getBtcTransaction();

            for (TransactionInput input : pegoutBtcTransaction.getInputs()) {
                // Each input should contain the right scriptSig
                Script inputRedeemScript = BitcoinUtils.extractRedeemScriptFromInput(input).orElseThrow();

                // Get the standard redeem script to compare against, since it could be a flyover redeem script
                var redeemScriptChunks = ScriptParser.parseScriptProgram(
                    inputRedeemScript.getProgram());

                var redeemScriptParser = RedeemScriptParserFactory.get(redeemScriptChunks);
                var inputStandardRedeemScriptChunks = redeemScriptParser.extractStandardRedeemScriptChunks();
                var inputStandardRedeemScript = new ScriptBuilder().addChunks(inputStandardRedeemScriptChunks).build();

                Optional<Federation> spendingFederationOptional = Optional.empty();
                if (inputStandardRedeemScript.equals(getFederationDefaultRedeemScript(activeFederation))) {
                    spendingFederationOptional = Optional.of(activeFederation);
                } else if (retiringFederation != null &&
                    inputStandardRedeemScript.equals(getFederationDefaultRedeemScript(retiringFederation))) {
                    spendingFederationOptional = Optional.of(retiringFederation);
                } else {
                    fail("Pegout scriptsig does not match any Federation");
                }

                // Check the script sig composition
                Federation spendingFederation = spendingFederationOptional.get();
                var inputScriptChunks = input.getScriptSig().getChunks();
                assertEquals(ScriptOpCodes.OP_0, inputScriptChunks.get(0).opcode);
                for (int i = 1; i <= spendingFederation.getNumberOfSignaturesRequired(); i++) {
                    assertEquals(ScriptOpCodes.OP_0, inputScriptChunks.get(i).opcode);
                }

                int index = spendingFederation.getNumberOfSignaturesRequired() + 1;
                if (spendingFederation instanceof ErpFederation) {
                    // Should include an additional OP_0
                    assertEquals(ScriptOpCodes.OP_0, inputScriptChunks.get(index).opcode);
                }
            }
        }
    }

    private void assertPegoutTxSigHashesAreSaved() throws IOException {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries().stream()
            .map(Entry::getBtcTransaction)
            .toList();

        for (var pegoutTx : pegoutsTxs) {
            var lastPegoutSigHash = BitcoinUtils.getFirstInputSigHash(pegoutTx);
            assertTrue(lastPegoutSigHash.isPresent());
            assertTrue(bridgeStorageProvider.hasPegoutTxSigHash(lastPegoutSigHash.get()));
        }
    }
}
