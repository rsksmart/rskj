package co.rsk.peg.federation;

import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.flatKeysAsByteArray;
import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.test.builders.UTXOBuilder;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

class VoteFederationChangeTest {
    private static final FederationConstants federationMainnetConstants = FederationMainNetConstants.getInstance();
    private static final BtcECKey federatorBtcKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));
    private static final ECKey federatorRskKey = ECKey.fromPrivate(BigInteger.valueOf(200));
    private static final ECKey federatorMstKey = ECKey.fromPrivate(BigInteger.valueOf(300));
    private static final SignatureCache signatureCache = mock(SignatureCache.class);
    private static final Transaction firstAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
    private static final Transaction secondAuthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());
    private static final CallTransaction.Function commitFederationEvent = BridgeEvents.COMMIT_FEDERATION.getEvent();
    private static final long RSK_EXECUTION_BLOCK_NUMBER = 1000L;
    private static final long RSK_EXECUTION_BLOCK_TIMESTAMP = 10L;
    private static final PendingFederation pendingFederationToBe = new PendingFederation(FederationTestUtils.getFederationMembers(9));
    private static final Federation activeFederation = FederationTestUtils.getErpFederation(federationMainnetConstants.getBtcParams());

    private final FederationSupportBuilder federationSupportBuilder = FederationSupportBuilder.builder();
    private FederationSupport federationSupport;
    private ActivationConfig.ForBlock activations;
    private List<LogInfo> logs;
    private BridgeEventLogger bridgeEventLogger;
    private StorageAccessor bridgeStorageAccessor;
    private FederationStorageProvider storageProvider;
    private Block rskExecutionBlock;

    @BeforeEach
    void setUp() {
        activations = ActivationConfigsForTest.all().forBlock(0L);

        logs = new ArrayList<>();
        bridgeEventLogger = new BridgeEventLoggerImpl(BridgeMainNetConstants.getInstance(), activations, logs);

        bridgeStorageAccessor = new InMemoryStorage();
        storageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        storageProvider.setNewFederation(activeFederation);

        BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(RSK_EXECUTION_BLOCK_NUMBER)
            .setTimestamp(RSK_EXECUTION_BLOCK_TIMESTAMP)
            .build();
        rskExecutionBlock = Block.createBlockFromHeader(blockHeader, true);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .withRskExecutionBlock(rskExecutionBlock)
            .withActivations(activations)
            .build();
    }

    @Test
    void voteFederationChange_withNonExistingFunction_returnsNonExistingResponseCode() {
        // Arrange
        ABICallSpec nonExistingFunctionCallSpec = new ABICallSpec("nonExistingFunctionName", new byte[][]{});

        // Act
        int result = federationSupport.voteFederationChange(firstAuthorizedTx, nonExistingFunctionCallSpec, signatureCache, bridgeEventLogger);

        // Assert
        assertEquals(FederationChangeResponseCode.NON_EXISTING_FUNCTION_CALLED.getCode(), result);
    }

    // vote create federation tests
    @Test
    void voteCreateFederation_withUnauthorizedCaller_returnsUnauthorizedResponseCode() {
        // Act
        Transaction unauthorizedTx = TransactionUtils.getTransactionFromCaller(signatureCache, FederationChangeCaller.UNAUTHORIZED.getRskAddress());
        int result = voteToCreatePendingFederation(unauthorizedTx);

        // Assert
        assertEquals(FederationChangeResponseCode.UNAUTHORIZED_CALLER.getCode(), result);
    }

    @Test
    void voteCreateFederation_withoutEnoughVotes_returnsSuccessfulButDoesNotCreatePendingFederation() {
        // Act
        int result = voteToCreatePendingFederation(firstAuthorizedTx);

        // Assert
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), result);
        assertNull(federationSupport.getPendingFederationHash());
    }

    @Test
    void voteCreateFederation_whenVotingTwiceWithSameAuthorizer_returnsGenericErrorResponseCodeInSecondCall() {
        // Act
        // First create call
        int firstCall = voteToCreatePendingFederation(firstAuthorizedTx);

        // Second create call
        int secondCall = voteToCreatePendingFederation(firstAuthorizedTx);

        // Assert
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstCall);
        assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), secondCall);

    }

    @Test
    void voteCreateFederation_withEnoughVotes_returnsSuccessfulResponseCodeAndPendingFederationCreated() {
        // Act and assert
        voteAndAssertCreateEmptyPendingFederation();
        assertNotNull(federationSupport.getPendingFederationHash());
    }


    // vote add federator public keys tests
    @Test
    void voteAddFederatorPublicKeys_withOnlyOneKey_throwsArrayIndexOutOfBoundsException() {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();
        ABICallSpec addOnlyBtcFederatorPublicKeyAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(), new byte[][]{ federatorBtcKey.getPubKey() });

        // Act and assert
        assertThrows(ArrayIndexOutOfBoundsException.class,
            () -> federationSupport.voteFederationChange(firstAuthorizedTx, addOnlyBtcFederatorPublicKeyAbiCallSpec, signatureCache, bridgeEventLogger)
        );
    }

    @Test
    void voteAddFederatorPublicKeys_withoutEnoughVotes_returnsSuccessfulButDoesNotAddFederator() {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();

        // Act
        int result = voteToAddFederatorPublicKeysToPendingFederation(firstAuthorizedTx, federatorBtcKey, federatorRskKey, federatorMstKey);

        // Assert
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), result);
        assertEquals(0, federationSupport.getPendingFederationSize());
    }

    @Test
    void voteAddFederatorPublicKeys_withEnoughVotes_returnsSuccessfulAndAddsFederatorPublicKeys() {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();

        // Act
        voteAndAssertAddFederatorPublicKeysToPendingFederation(federatorBtcKey, federatorRskKey, federatorMstKey);
        assertEquals(1, federationSupport.getPendingFederationSize());

        byte[] actualBtcECkey = federationSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC);
        byte[] actualRskKey = federationSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK);
        byte[] actualMstKey = federationSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.MST);
        assertArrayEquals(federatorBtcKey.getPubKey(), actualBtcECkey);
        assertArrayEquals(federatorRskKey.getPubKey(true), actualRskKey);
        assertArrayEquals(federatorMstKey.getPubKey(true), actualMstKey);
    }

    @ParameterizedTest
    @MethodSource("keysWithOneInvalidKeyArgProvider")
    void voteAddFederatorPublicKeys_withOneInvalidPublicKey_returnsGenericErrorResponseCode(
        byte[] federatorBtcKeySerialized, byte[] federatorRskKeySerialized, byte[] federatorMstKeySerialized
    ) {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();

        // Act
        ABICallSpec addFederatorAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(),
            new byte[][]{ federatorBtcKeySerialized, federatorRskKeySerialized, federatorMstKeySerialized }
        );
        int voteAddMultiKeyResult = federationSupport.voteFederationChange(firstAuthorizedTx, addFederatorAbiCallSpec, signatureCache, bridgeEventLogger);

        // Assert
        assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), voteAddMultiKeyResult);
    }

    private static Stream<Arguments> keysWithOneInvalidKeyArgProvider() {
        byte[] invalidKey = TestUtils.generateBytes(1, 30);

        return Stream.of(
            Arguments.of(federatorBtcKey.getPubKey(), federatorRskKey.getPubKey(), invalidKey),
            Arguments.of(federatorBtcKey.getPubKey(), invalidKey, federatorMstKey.getPubKey()),
            Arguments.of(invalidKey, federatorRskKey.getPubKey(), federatorMstKey.getPubKey())
        );
    }

    @Test
    void voteAddFederatorPublicKeys_whenSameAuthorizerVotesTwice_returnsGenericErrorResponseCode() {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();

        // Act
        // Voting add public key twice with same authorizer
        int firstVoteAddFederationResult = voteToAddFederatorPublicKeysToPendingFederation(firstAuthorizedTx, federatorBtcKey, federatorRskKey, federatorMstKey);
        int secondVoteAddFederationResult = voteToAddFederatorPublicKeysToPendingFederation(firstAuthorizedTx, federatorBtcKey, federatorRskKey, federatorMstKey);

        // Assert
        // First call is successful
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteAddFederationResult);
        // Second call fails
        assertEquals(FederationChangeResponseCode.GENERIC_ERROR.getCode(), secondVoteAddFederationResult);

        assertEquals(0, federationSupport.getPendingFederationSize());
    }

    @ParameterizedTest
    @MethodSource("keysWithOneDifferentKeyArgProvider")
    void voteAddFederatorPublicKeys_whenVotingAnotherFederatorThatSharesOneKeyWithExistingFederator_returnsFederatorAlreadyPresentResponseCode(
        BtcECKey federator2BtcPublicKey, ECKey federator2RskPublicKey, ECKey federator2MstPublicKey
    ) {
        voteAndAssertCreateEmptyPendingFederation();

        // Act
        // Add first federator to pending federation
        voteAndAssertAddFederatorPublicKeysToPendingFederation(federatorBtcKey, federatorRskKey, federatorMstKey);
        // Voting new federator public keys,
        // that will be considered the same as the previous one
        // because they share at least one key
        int firstVoteAddMultiFederator2KeysResult = voteToAddFederatorPublicKeysToPendingFederation(firstAuthorizedTx, federator2BtcPublicKey, federator2RskPublicKey, federator2MstPublicKey);

        // Assert
        assertEquals(FederationChangeResponseCode.FEDERATOR_ALREADY_PRESENT.getCode(), firstVoteAddMultiFederator2KeysResult);

        // Pending federation size is 1, because authorizers first voted for the same federator and the second was ignored
        assertEquals(1, federationSupport.getPendingFederationSize());
    }

    @ParameterizedTest
    @MethodSource("keysWithOneDifferentKeyArgProvider")
    void voteAddFederatorPublicKeys_whenVotingFederatorsWithOneDifferentKey_returnsSuccessfulButDoesNotAddFederator(
        BtcECKey federatorBtcPublicKey, ECKey federatorRskPublicKey, ECKey federatorMstPublicKey
    ) {
        voteAndAssertCreateEmptyPendingFederation();

        // Act
        int firstVoteResult = voteToAddFederatorPublicKeysToPendingFederation(firstAuthorizedTx, federatorBtcKey, federatorRskKey, federatorMstKey);
        // Voting a federator with just one different key from previous one.
        // It will be considered a different federator,
        // so second vote won't add it to the pending federation
        int secondVoteResult = voteToAddFederatorPublicKeysToPendingFederation(secondAuthorizedTx, federatorBtcPublicKey, federatorRskPublicKey, federatorMstPublicKey);

        // Assert
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteResult);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteResult);

        // Pending federation size is 0, because we voted for different federators
        assertEquals(0, federationSupport.getPendingFederationSize());
    }

    private static Stream<Arguments> keysWithOneDifferentKeyArgProvider() {
        BtcECKey differentBtcKey = BtcECKey.fromPrivate(BigInteger.valueOf(400));
        ECKey differentRskKey = ECKey.fromPrivate(BigInteger.valueOf(500));
        ECKey differentMstKey = ECKey.fromPrivate(BigInteger.valueOf(600));

        return Stream.of(
            Arguments.of(differentBtcKey, federatorRskKey, federatorMstKey),
            Arguments.of(federatorBtcKey, differentRskKey, federatorMstKey),
            Arguments.of(federatorBtcKey, federatorRskKey, differentMstKey)
        );
    }

    @Test
    void voteAddFederatorPublicKey_whenAdding100Federators_returnsSuccessfulResponseCodeAndFedSize100() {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();

        // Act
        // Voting to add 100 federators to pending federation
        int expectedCountOfMembers = 100;
        voteAndAssertAddFederatorPublicKeysToPendingFederation(expectedCountOfMembers);

        // Assert
        assertEquals(expectedCountOfMembers, federationSupport.getPendingFederationSize());
    }


    // vote commit federation tests
    @Test
    void voteCommitFederation_withEmptyFederation_returnsInsufficientMembersResponseCode() {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();

        // Act
        int result = voteToCommitPendingFederation(firstAuthorizedTx);

        // Assert
        assertEquals(FederationChangeResponseCode.INSUFFICIENT_MEMBERS.getCode(), result);
    }

    @Test
    void voteCommitFederation_commit1MemberFederation_returnsInsufficientMembersResponseCode() {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();
        voteAndAssertAddFederatorPublicKeysToPendingFederation(federatorBtcKey, federatorRskKey, federatorMstKey);

        // Act
        int firstVoteResult = voteToCommitPendingFederation(firstAuthorizedTx);

        // Assert
        assertEquals(FederationChangeResponseCode.INSUFFICIENT_MEMBERS.getCode(), firstVoteResult);
    }

    @Test
    void voteCommitFederation_commit10MembersFederation_returnsSuccessfulResponseCode() {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();

        // Voting to add 10 federators to pending federation
        final int EXPECTED_COUNT_OF_MEMBERS = 10;
        voteAndAssertAddFederatorPublicKeysToPendingFederation(EXPECTED_COUNT_OF_MEMBERS);

        voteAndAssertCommitPendingFederation();
    }

    @Test
    void voteCommitFederation_preRSKIP305_commitFederationWithOver10Members_throwsException() {
        // Arrange
        activations = ActivationConfigsForTest.lovell700().forBlock(0L);
        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .withRskExecutionBlock(rskExecutionBlock)
            .withActivations(activations)
            .build();
        voteAndAssertCreateEmptyPendingFederation();

        // Voting to add 11 federators to pending federation
        final int EXPECTED_COUNT_OF_MEMBERS = 11;
        voteAndAssertAddFederatorPublicKeysToPendingFederation(EXPECTED_COUNT_OF_MEMBERS);

        // Act and assert
        voteToCommitPendingFederation(firstAuthorizedTx);
        // second one is which throws the exception
        Exception exception = assertThrows(Exception.class,
            () -> voteToCommitPendingFederation(secondAuthorizedTx));
        // 525 is the size of the redeem script for 11 members + 34 flyover script bytes
        String expectedMessage = "The size of the redeem script for scriptSig is 525, which is above the maximum allowed (486).";
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void voteCommitFederation_postRSKIP305_commitFederationWith20Members_shouldNotThrow() {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();

        // Voting to add 20 federators to pending federation
        final int EXPECTED_COUNT_OF_MEMBERS = 20;
        voteAndAssertAddFederatorPublicKeysToPendingFederation(EXPECTED_COUNT_OF_MEMBERS);

        // Act and assert
        voteToCommitPendingFederation(firstAuthorizedTx);
        voteToCommitPendingFederation(secondAuthorizedTx);

        Optional<Federation> proposedFederation = storageProvider.getProposedFederation(federationMainnetConstants, activations);
        assertTrue(proposedFederation.isPresent());

        List<BtcECKey> membersPubKeys = proposedFederation.get().getBtcPublicKeys();
        assertEquals(EXPECTED_COUNT_OF_MEMBERS, membersPubKeys.size());
    }

    @Test
    void voteCommitFederation_preRSKIP186_whenPendingFederationIsSet_shouldPerformLegacyCommitFederationActionsButNotSetFederationChangeInfo() {
        // arrange
        activations = ActivationConfigsForTest.papyrus200().forBlock(0L);
        bridgeEventLogger = new BridgeEventLoggerImpl(BridgeMainNetConstants.getInstance(), activations, logs);
        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .withRskExecutionBlock(rskExecutionBlock)
            .withActivations(activations)
            .build();

        voteAndAssertCreateEmptyPendingFederation();
        voteAndAssertAddFederationMembersPublicKeysToPendingFederation(pendingFederationToBe.getMembers());

        // act
        voteAndAssertCommitPendingFederation();

        // assertions
        Federation newFederation = storageProvider.getNewFederation(federationMainnetConstants, activations);
        long expectedNewFederationCreationTimeValue = newFederation.getCreationTime().toEpochMilli();
        Instant expectedNewFederationCreationTime = Instant.ofEpochMilli(RSK_EXECUTION_BLOCK_TIMESTAMP);
        assertIsTheExpectedFederation(newFederation, expectedNewFederationCreationTimeValue, expectedNewFederationCreationTime);

        List<UTXO> utxosToMove = new ArrayList<>(storageProvider.getNewFederationBtcUTXOs(federationMainnetConstants.getBtcParams(), activations));
        assertUTXOsWereMovedFromNewToOldFederation(utxosToMove);

        // pre rskip186 these values are not being set
        assertNewActiveFederationCreationBlockHeightWasNotSet();
        assertLastRetiredFederationScriptWasNotSet();

        assertPendingFederationVotingWasCleaned();

        Federation oldFederation = storageProvider.getOldFederation(federationMainnetConstants, activations);
        assertLogCommitFederation(oldFederation, newFederation);
    }

    @Test
    void voteCommitFederation_postRSKIP186_preRSKIP419_whenPendingFederationIsSet_shouldPerformLegacyCommitFederationActions() {
        // arrange
        activations = ActivationConfigsForTest.arrowhead631().forBlock(0L);
        bridgeEventLogger = new BridgeEventLoggerImpl(BridgeMainNetConstants.getInstance(), activations, logs);
        federationSupport = federationSupportBuilder
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(storageProvider)
            .withRskExecutionBlock(rskExecutionBlock)
            .withActivations(activations)
            .build();

        voteAndAssertCreateEmptyPendingFederation();
        voteAndAssertAddFederationMembersPublicKeysToPendingFederation(pendingFederationToBe.getMembers());

        // act
        voteAndAssertCommitPendingFederation();

        // assertions
        Federation newFederation = storageProvider.getNewFederation(federationMainnetConstants, activations);
        long expectedNewFederationCreationTimeValue = newFederation.getCreationTime().toEpochMilli();
        Instant expectedNewFederationCreationTime = Instant.ofEpochMilli(RSK_EXECUTION_BLOCK_TIMESTAMP);
        assertIsTheExpectedFederation(newFederation, expectedNewFederationCreationTimeValue, expectedNewFederationCreationTime);

        List<UTXO> utxosToMove = List.copyOf(storageProvider.getNewFederationBtcUTXOs(federationMainnetConstants.getBtcParams(), activations));
        assertHandoverToNewFederation(utxosToMove, newFederation);

        assertPendingFederationVotingWasCleaned();

        Federation oldFederation = storageProvider.getOldFederation(federationMainnetConstants, activations);
        assertLogCommitFederation(oldFederation, newFederation);
    }

    @Test
    void voteCommitFederation_postRSKIP419_whenPendingFederationIsSet_shouldPerformCommitFederationActions() {
        // arrange
        Federation activeFederation = federationSupport.getActiveFederation();
        int numberOfUtxos = 10;
        List<UTXO> activeFederationUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(activeFederation.getP2SHScript())
            .buildMany(numberOfUtxos, i -> createHash(i + 1));

        bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), activeFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

        voteAndAssertCreateEmptyPendingFederation();
        voteAndAssertAddFederationMembersPublicKeysToPendingFederation(pendingFederationToBe.getMembers());

        // act
        voteAndAssertCommitPendingFederation();

        // assertions
        // assert proposed federation was set correctly
        Optional<Federation> proposedFederationOpt = storageProvider.getProposedFederation(federationMainnetConstants, activations);
        assertTrue(proposedFederationOpt.isPresent());
        Federation proposedFederation = proposedFederationOpt.get();
        long expectedProposedFederationCreationTimeValue = proposedFederation.getCreationTime().getEpochSecond();
        Instant expectedProposedFederationCreationTime = Instant.ofEpochSecond(RSK_EXECUTION_BLOCK_TIMESTAMP);
        assertIsTheExpectedFederation(proposedFederation, expectedProposedFederationCreationTimeValue, expectedProposedFederationCreationTime);

        assertPendingFederationVotingWasCleaned();

        assertLogCommitFederation(activeFederation, proposedFederation);

        assertNoHandoverToNewFederation();
    }


    @Test
    void commitProposedFederation_shouldPerformCommitProposedFederationActions() {
        // arrange
        storageProvider.setNewFederation(activeFederation);
        int numberOfUtxos = 10;
        List<UTXO> activeFederationUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(activeFederation.getP2SHScript())
            .buildMany(numberOfUtxos, i -> createHash(i + 1));
        bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), activeFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

        Federation proposedFederation = P2shP2wshErpFederationBuilder.builder().build();
        storageProvider.setProposedFederation(proposedFederation);

        // act
        federationSupport.commitProposedFederation();

        // assert
        assertFalse(federationSupport.getProposedFederation().isPresent());
        assertHandoverToNewFederation(activeFederationUTXOs, proposedFederation);
    }

    // vote rollback federation tests
    @Test
    void rollbackFederation_returnsSuccessfulResponseCodeAndRollsbackThePendingFederation() {
        // Arrange
        voteAndAssertCreateEmptyPendingFederation();

        // Voting to have at least one federation member before rollback
        voteAndAssertAddFederatorPublicKeysToPendingFederation(federatorBtcKey, federatorRskKey, federatorMstKey);
        int pendingFederationSizeBeforeRollback = federationSupport.getPendingFederationSize();
        assertEquals(1, pendingFederationSizeBeforeRollback);

        // Act
        int firstVoteRollbackResult = voteToRollbackPendingFederation(firstAuthorizedTx);
        int secondVoteRollbackResult = voteToRollbackPendingFederation(secondAuthorizedTx);

        // Assert
        int pendingFederationSizeAfterRollback = federationSupport.getPendingFederationSize();
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteRollbackResult);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteRollbackResult);
        assertEquals(-1, pendingFederationSizeAfterRollback);
        assertNull(federationSupport.getPendingFederationHash());
    }

    private void assertIsTheExpectedFederation(Federation federation, long expectedCreationTimeValue, Instant expectedCreationTime) {
        assertEquals(RSK_EXECUTION_BLOCK_TIMESTAMP, expectedCreationTimeValue);
        assertEquals(RSK_EXECUTION_BLOCK_NUMBER, federation.getCreationBlockNumber());

        Federation federationBuiltFromPendingFederation = pendingFederationToBe.buildFederation(
            expectedCreationTime,
            RSK_EXECUTION_BLOCK_NUMBER,
            federationMainnetConstants,
            activations
        );
        assertEquals(federationBuiltFromPendingFederation, federation);
    }

    private void voteAndAssertAddFederationMembersPublicKeysToPendingFederation(List<FederationMember> federationMembers) {
        for (FederationMember federationMember : federationMembers) {
            BtcECKey memberBtcKey = federationMember.getBtcPublicKey();
            ECKey memberRskKey = federationMember.getRskPublicKey();
            ECKey memberMstKey = federationMember.getMstPublicKey();

            voteAndAssertAddFederatorPublicKeysToPendingFederation(memberBtcKey, memberRskKey, memberMstKey);
        }
    }

    private void assertHandoverToNewFederation(List<UTXO> utxosToMove, Federation newFederation) {
        assertUTXOsWereMovedFromNewToOldFederation(utxosToMove);
        assertNewAndOldFederationsWereSet(newFederation);
        assertLastRetiredFederationScriptWasSet();
        assertNewActiveFederationCreationBlockHeightWasSet(newFederation.getCreationBlockNumber());
    }

    private void assertNoHandoverToNewFederation() {
        assertUTXOsWereNotMovedFromNewToOldFederation();
        assertNewAndOldFederationsWereNotSet();
        assertLastRetiredFederationScriptWasNotSet();
        assertNewActiveFederationCreationBlockHeightWasNotSet();
    }

    private void assertUTXOsWereMovedFromNewToOldFederation(List<UTXO> utxosToMove) {
        // assert utxos were moved from new federation to old federation
        List<UTXO> oldFederationUTXOs = storageProvider.getOldFederationBtcUTXOs();
        assertEquals(utxosToMove, oldFederationUTXOs);

        // assert new federation utxos were cleaned
        List<UTXO> newFederationUTXOs = storageProvider.getNewFederationBtcUTXOs(federationMainnetConstants.getBtcParams(), activations);
        assertTrue(newFederationUTXOs.isEmpty());
    }

    private void assertUTXOsWereNotMovedFromNewToOldFederation() {
        // assert old federation utxos are still empty
        List<UTXO> oldFederationUTXOs = storageProvider.getOldFederationBtcUTXOs();
        assertTrue(oldFederationUTXOs.isEmpty());

        // assert new federation utxos are not empty
        List<UTXO> newFederationUTXOs = storageProvider.getNewFederationBtcUTXOs(federationMainnetConstants.getBtcParams(), activations);
        assertFalse(newFederationUTXOs.isEmpty());
    }

    private void assertNewAndOldFederationsWereSet(Federation expectedNewFederation) {
        // assert the active federation was set as the old federation
        Federation oldFederation = storageProvider.getOldFederation(federationMainnetConstants, activations);
        assertEquals(federationSupport.getActiveFederation(), oldFederation);

        // assert the federation built from the pending one was set as the new federation
        Federation newFederation = storageProvider.getNewFederation(federationMainnetConstants, activations);
        assertEquals(expectedNewFederation, newFederation);
    }

    private void assertNewAndOldFederationsWereNotSet() {
        // assert old federation is still null
        Federation oldFederation = storageProvider.getOldFederation(federationMainnetConstants, activations);
        assertNull(oldFederation);

        // assert new federation is still the active federation
        Federation newFederation = storageProvider.getNewFederation(federationMainnetConstants, activations);
        assertEquals(activeFederation, newFederation);
    }

    private void assertPendingFederationVotingWasCleaned() {
        assertNull(storageProvider.getPendingFederation());

        Map<ABICallSpec, List<RskAddress>> federationElectionVotes = storageProvider.getFederationElection(federationMainnetConstants.getFederationChangeAuthorizer()).getVotes();
        assertTrue(federationElectionVotes.isEmpty());
    }

    private void assertNewActiveFederationCreationBlockHeightWasSet(long expectedNextFederationCreationBlockHeight) {
        Optional<Long> nextFederationCreationBlockHeight = storageProvider.getNextFederationCreationBlockHeight(activations);
        assertTrue(nextFederationCreationBlockHeight.isPresent());
        assertEquals(expectedNextFederationCreationBlockHeight, nextFederationCreationBlockHeight.get());
    }

    private void assertLastRetiredFederationScriptWasSet() {
        ErpFederation activeFederationCasted = (ErpFederation) federationSupport.getActiveFederation();
        Script activeFederationMembersP2SHScript = activeFederationCasted.getDefaultP2SHScript();
        Optional<Script> lastRetiredFederationP2SHScript = storageProvider.getLastRetiredFederationP2SHScript(activations);
        assertTrue(lastRetiredFederationP2SHScript.isPresent());
        assertEquals(activeFederationMembersP2SHScript, lastRetiredFederationP2SHScript.get());
    }

    private void assertNewActiveFederationCreationBlockHeightWasNotSet() {
        Optional<Long> nextFederationCreationBlockHeight = storageProvider.getNextFederationCreationBlockHeight(activations);
        assertFalse(nextFederationCreationBlockHeight.isPresent());
    }

    private void assertLastRetiredFederationScriptWasNotSet() {
        Optional<Script> lastRetiredFederationP2SHScript = storageProvider.getLastRetiredFederationP2SHScript(activations);
        assertFalse(lastRetiredFederationP2SHScript.isPresent());
    }

    private void assertLogCommitFederation(Federation federationToBeRetired, Federation votedFederation) {
        List<DataWord> encodedTopics = getEncodedTopics();
        byte[] encodedData = getEncodedData(federationToBeRetired, votedFederation);

        // assert the event was emitted just once with the expected topic and data
        assertEquals(1, logs.size());

        LogInfo log = logs.get(0);
        List<DataWord> topic = log.getTopics();
        assertEquals(encodedTopics.get(0), topic.get(0));
        assertArrayEquals(encodedData, log.getData());
    }

    private List<DataWord> getEncodedTopics() {
        byte[][] encodedTopicsInBytes = commitFederationEvent.encodeEventTopics();
        return LogInfo.byteArrayToList(encodedTopicsInBytes);
    }

    private byte[] getEncodedData(Federation federationToBeRetired, Federation votedFederation) {
        byte[] oldFederationFlatPubKeys = flatKeysAsByteArray(federationToBeRetired.getBtcPublicKeys());
        String oldFederationBtcAddress = federationToBeRetired.getAddress().toBase58();
        byte[] newFederationFlatPubKeys = flatKeysAsByteArray(votedFederation.getBtcPublicKeys());
        String newFederationBtcAddress = votedFederation.getAddress().toBase58();
        long newFedActivationBlockNumber = rskExecutionBlock.getNumber() + federationMainnetConstants.getFederationActivationAge(activations);

        return commitFederationEvent.encodeEventData(
            oldFederationFlatPubKeys,
            oldFederationBtcAddress,
            newFederationFlatPubKeys,
            newFederationBtcAddress,
            newFedActivationBlockNumber
        );
    }

    // utility methods
    private int voteToCreatePendingFederation(Transaction tx) {
        ABICallSpec createFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});
        return federationSupport.voteFederationChange(tx, createFederationAbiCallSpec, signatureCache, bridgeEventLogger);
    }

    private void voteAndAssertCreateEmptyPendingFederation() {
        // Voting with enough authorizers to create the pending federation
        int resultFromFirstAuthorizer = voteToCreatePendingFederation(firstAuthorizedTx);
        int resultFromSecondAuthorizer = voteToCreatePendingFederation(secondAuthorizedTx);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);

        assertEquals(0, federationSupport.getPendingFederationSize());
        assertNotNull(federationSupport.getPendingFederationHash());
    }

    private int voteToAddFederatorPublicKeysToPendingFederation(Transaction tx, BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        ABICallSpec addFederatorAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(),
            new byte[][]{ btcPublicKey.getPubKey(), rskPublicKey.getPubKey(), mstPublicKey.getPubKey() }
        );

        return federationSupport.voteFederationChange(tx, addFederatorAbiCallSpec, signatureCache, bridgeEventLogger);
    }

    private void voteAndAssertAddFederatorPublicKeysToPendingFederation(BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        int resultFromFirstAuthorizer = voteToAddFederatorPublicKeysToPendingFederation(firstAuthorizedTx, btcPublicKey, rskPublicKey, mstPublicKey);
        int resultFromSecondAuthorizer = voteToAddFederatorPublicKeysToPendingFederation(secondAuthorizedTx, btcPublicKey, rskPublicKey, mstPublicKey);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);
    }

    private void voteAndAssertAddFederatorPublicKeysToPendingFederation(int amountOfMembers) {
        for (int i = 0; i < amountOfMembers; i++) {
            BtcECKey memberBtcKey = BtcECKey.fromPrivate(BigInteger.valueOf(i + 100));
            ECKey memberRskKey = ECKey.fromPrivate(BigInteger.valueOf(i + 101));
            ECKey memberMstKey = ECKey.fromPrivate(BigInteger.valueOf(i + 102));

            voteAndAssertAddFederatorPublicKeysToPendingFederation(memberBtcKey, memberRskKey, memberMstKey);
        }
    }

    private int voteToCommitPendingFederation(Transaction tx) {
        Keccak256 pendingFederationHash = federationSupport.getPendingFederationHash();
        ABICallSpec commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{ pendingFederationHash.getBytes() });

        return federationSupport.voteFederationChange(tx, commitFederationAbiCallSpec, signatureCache, bridgeEventLogger);
    }

    private void voteAndAssertCommitPendingFederation() {
        int firstVoteResult = voteToCommitPendingFederation(firstAuthorizedTx);
        int secondVoteResult = voteToCommitPendingFederation(secondAuthorizedTx);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteResult);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteResult);
    }

    private int voteToRollbackPendingFederation(Transaction tx) {
        ABICallSpec rollbackAbiCallSpec = new ABICallSpec(FederationChangeFunction.ROLLBACK.getKey(), new byte[][]{});
        return federationSupport.voteFederationChange(tx, rollbackAbiCallSpec, signatureCache, bridgeEventLogger);
    }
}
