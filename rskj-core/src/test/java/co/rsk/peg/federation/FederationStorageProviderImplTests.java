package co.rsk.peg.federation;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.verification.VerificationMode;

import static co.rsk.bitcoinj.core.NetworkParameters.ID_TESTNET;
import static co.rsk.bitcoinj.core.NetworkParameters.ID_MAINNET;
import static co.rsk.peg.federation.FederationFormatVersion.*;
import static co.rsk.peg.storage.FederationStorageIndexKey.*;
import static co.rsk.peg.BridgeSerializationUtils.serializeElection;
import co.rsk.bitcoinj.script.Script;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.storage.FederationStorageIndexKey;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import co.rsk.bitcoinj.core.UTXO;

class FederationStorageProviderImplTests {

    private static final int STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION = STANDARD_MULTISIG_FEDERATION.getFormatVersion();
    private static final int NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION = NON_STANDARD_ERP_FEDERATION.getFormatVersion();
    private static final int P2SH_ERP_FEDERATION_FORMAT_VERSION = P2SH_ERP_FEDERATION.getFormatVersion();
    private static final int INVALID_FEDERATION_FORMAT = -1;
    private static final int EMPTY_FEDERATION_FORMAT = 0;

    private static final BridgeConstants bridgeConstantsRegtest = new BridgeRegTestConstants();
    private static final NetworkParameters regtestBtcParams = bridgeConstantsRegtest.getBtcParams();
    private static ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
    private static final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private static final FederationConstants federationConstants = bridgeConstants.getFederationConstants();
    private static final NetworkParameters networkParameters = federationConstants.getBtcParams();

    private static Stream<Arguments> provideFederationAndFormatArguments() {
        return Stream.of(
            Arguments.of(P2SH_ERP_FEDERATION_FORMAT_VERSION, createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION)),
            Arguments.of(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, createFederation(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION)),
            Arguments.of(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)),
            Arguments.of(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, null),
            Arguments.of(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, null),
            Arguments.of(P2SH_ERP_FEDERATION_FORMAT_VERSION, null),
            Arguments.of(INVALID_FEDERATION_FORMAT, null),
            Arguments.of(EMPTY_FEDERATION_FORMAT, null),
            Arguments.of(INVALID_FEDERATION_FORMAT, createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)),
            Arguments.of(EMPTY_FEDERATION_FORMAT, createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION))
        );
    }

    @ParameterizedTest
    @MethodSource("provideFederationAndFormatArguments")
    void testGetNewFederation(
        int federationFormat,
        Federation expectedFederation
    ) {
        // Arrange

        StorageAccessor storageAccessor = new InMemoryStorage();
        byte[] federationFormatSerialized = getFederationFormatSerialized(federationFormat);
        storageAccessor.saveToRepository(NEW_FEDERATION_FORMAT_VERSION.getKey(), federationFormatSerialized);
        byte[] serializedFederation = getSerializedFederation(expectedFederation, federationFormat);
        storageAccessor.saveToRepository(NEW_FEDERATION_KEY.getKey(), serializedFederation);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act

        Federation obtainedFederation = federationStorageProvider.getNewFederation(federationConstants, activations);

        // Directly saving a null federation in storage to then assert that the method returns the cached federation
        if(nonNull(expectedFederation)) {
            storageAccessor.saveToRepository(NEW_FEDERATION_KEY.getKey(), null);
        }

        // Assert

        // Call the method again and assert the same cached federation is returned
        assertEquals(obtainedFederation, federationStorageProvider.getNewFederation(federationConstants, activations));

        assertEquals(expectedFederation, obtainedFederation);
    }

    @ParameterizedTest
    @MethodSource("provideFederationAndFormatArguments")
    void testGetOldFederation(
        int federationFormat,
        Federation expectedFederation
    ) {
        // Arrange

        StorageAccessor storageAccessor = new InMemoryStorage();
        byte[] federationFormatSerialized = getFederationFormatSerialized(federationFormat);
        storageAccessor.saveToRepository(OLD_FEDERATION_FORMAT_VERSION.getKey(), federationFormatSerialized);
        byte[] serializedFederation = getSerializedFederation(expectedFederation, federationFormat);
        storageAccessor.saveToRepository(OLD_FEDERATION_KEY.getKey(), serializedFederation);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act

        Federation obtainedFederation = federationStorageProvider.getOldFederation(federationConstants, activations);

        // Directly saving a null federation in storage to then assert that the method returns the cached federation
        if(nonNull(expectedFederation)) {
            storageAccessor.saveToRepository(OLD_FEDERATION_KEY.getKey(), null);
        }

        // Assert

        // Call the method again and assert the same cached federation is returned
        assertEquals(obtainedFederation, federationStorageProvider.getOldFederation(federationConstants, activations));

        assertEquals(expectedFederation, obtainedFederation);
    }

    @Test
    void getOldFederation_previouslySetToNull_returnsNull() {
        StorageAccessor storageAccessor = new InMemoryStorage();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);
        federationStorageProvider.setOldFederation(null);
        Federation oldFederation = federationStorageProvider.getOldFederation(federationConstants, activations);
        assertNull(oldFederation);
    }

    private static Stream<Arguments> providePendingFederationAndFormatArguments() {
        return Stream.of(
            Arguments.of(P2SH_ERP_FEDERATION_FORMAT_VERSION, new PendingFederationBuilder().build()),
            Arguments.of(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, new PendingFederationBuilder().build()),
            Arguments.of(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, new PendingFederationBuilder().build())
        );
    }

    @ParameterizedTest
    @MethodSource("providePendingFederationAndFormatArguments")
    void testGetPendingFederation(
        int federationFormat,
        PendingFederation expectedFederation
    ) {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(true);

        StorageAccessor storageAccessor = new InMemoryStorage();
        byte[] federationFormatSerialized = getFederationFormatSerialized(federationFormat);
        storageAccessor.saveToRepository(PENDING_FEDERATION_FORMAT_VERSION.getKey(), federationFormatSerialized);

        byte[] serializedFederation = expectedFederation.serialize(activations);
        storageAccessor.saveToRepository(PENDING_FEDERATION_KEY.getKey(), serializedFederation);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act

        PendingFederation obtainedFederation = federationStorageProvider.getPendingFederation();

        // Directly saving a null federation in storage to then assert that the method returns the cached federation
        storageAccessor.saveToRepository(PENDING_FEDERATION_KEY.getKey(), null);

        // Assert

        // Call the method again and assert the same cached federation is returned
        assertEquals(obtainedFederation, federationStorageProvider.getPendingFederation());

        assertEquals(expectedFederation, obtainedFederation);

    }

    @Test
    void getPendingFederation_whenStorageVersionIsNotAvailable_deserializeFromBtcKeysOnly(
    ) {

        PendingFederation expectedFederation = new PendingFederationBuilder().build();

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(false);

        StorageAccessor storageAccessor = new InMemoryStorage();
        byte[] federationFormatSerialized = getFederationFormatSerialized(INVALID_FEDERATION_FORMAT);
        storageAccessor.saveToRepository(PENDING_FEDERATION_FORMAT_VERSION.getKey(), federationFormatSerialized);

        byte[] serializedFederation = expectedFederation.serialize(activations);
        storageAccessor.saveToRepository(PENDING_FEDERATION_KEY.getKey(), serializedFederation);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act

        PendingFederation obtainedFederation = federationStorageProvider.getPendingFederation();

        // Directly saving a null federation in storage to then assert that the method returns the cached federation
        storageAccessor.saveToRepository(PENDING_FEDERATION_KEY.getKey(), null);

        // Assert

        // Call the method again and assert the same cached federation is returned
        assertEquals(obtainedFederation, federationStorageProvider.getPendingFederation());


        assertArrayEquals(expectedFederation.serialize(activations), obtainedFederation.serialize(activations));

    }

    @Test
    void getPendingFederation_previouslySet_returnsCachedPendingFederation() {

        // Arrange

        PendingFederation expectedPendingFederation = new PendingFederationBuilder().build();

        // Act

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(null);
        federationStorageProvider.setPendingFederation(expectedPendingFederation);

        // Assert

        assertEquals(expectedPendingFederation, federationStorageProvider.getPendingFederation());

    }

    @Test
    void getPendingFederation_previouslySetToNull_returnsNull() {
        // Arrange

        StorageAccessor storageAccessor = mock(StorageAccessor.class);

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act

        federationStorageProvider.setPendingFederation(null);
        PendingFederation pendingFederation = federationStorageProvider.getPendingFederation();

        // Assert

        assertNull(pendingFederation);
        verify(storageAccessor, never()).getFromRepository(any(), any());

    }

    @Test
    void getPendingFederation_nullPendingFederationInStorage_returnsNull() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(false);

        StorageAccessor storageAccessor = new InMemoryStorage();
        byte[] federationFormatSerialized = getFederationFormatSerialized(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION);
        storageAccessor.saveToRepository(PENDING_FEDERATION_FORMAT_VERSION.getKey(), federationFormatSerialized);

        storageAccessor.saveToRepository(PENDING_FEDERATION_KEY.getKey(), null);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act

        PendingFederation actualPendingFederation = federationStorageProvider.getPendingFederation();

        // Assert

        assertNull(actualPendingFederation);

    }

    @Test
    void saveNewFederation_before_RSKIP123_should_allow_to_save_any_fed_type() {
        testSaveNewFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)
        );

        testSaveNewFederation(
            NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION)
        );

        testSaveNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP123_should_save_standard_multisig_fed_format() {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveNewFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP201_should_save_standard_multisig_fed_format() {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveNewFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP353_should_save_standard_multisig_fed_format() {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveNewFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP201_should_save_non_standard_erp_fed_format() {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveNewFederation(
            NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP353_should_save_non_standard_erp_fed_format() {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveNewFederation(
            NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP353_should_save_p2sh_erp_fed_format() {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveNewFederation_before_RSKIP123_should_not_save_null() {
        Repository repository = mock(Repository.class);
        activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(false);

        // Act
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(null);
        federationStorageProvider.save(regtestBtcParams, activations);

        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
    }

    @Test
    void saveNewFederation_after_RSKIP123_should_not_save_null() {
        Repository repository = mock(Repository.class);
        activations = ActivationConfigsForTest.only().forBlock(0);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);

        // Act
        federationStorageProvider.setNewFederation(null);
        federationStorageProvider.save(regtestBtcParams, activations);

        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
    }

    private void testSaveNewFederation(
        int expectedFormatToSave,
        Federation federationToSave
    ) {
        // Arrange
        Repository repository = mock(Repository.class);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);

        // Act
        federationStorageProvider.setNewFederation(federationToSave);
        federationStorageProvider.save(regtestBtcParams, activations);

        // Assert
        byte[] serializedFederation = activations.isActive(ConsensusRule.RSKIP123) ?
            BridgeSerializationUtils.serializeFederation(federationToSave) :
            BridgeSerializationUtils.serializeFederationOnlyBtcKeys(federationToSave);
        VerificationMode shouldSaveNewFederationFormatVersion = activations.isActive(ConsensusRule.RSKIP123) ?
            times(1) :
            never();

        verify(repository, shouldSaveNewFederationFormatVersion).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_FORMAT_VERSION.getKey(),
            BridgeSerializationUtils.serializeInteger(expectedFormatToSave)
        );
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_KEY.getKey(),
            serializedFederation
        );
    }

    @Test
    void saveOldFederation_before_RSKIP123_should_allow_to_save_any_fed_type() {
        activations = ActivationConfigsForTest.only().forBlock(0);
        testSaveOldFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)
        );

        testSaveOldFederation(
            NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION)
        );

        testSaveOldFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP123_should_save_standard_multisig_fed_format() {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveOldFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP201_should_save_standard_multisig_fed_format() {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveOldFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP353_should_save_standard_multisig_fed_format() {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveOldFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP201_should_save_non_standard_erp_fed_format() {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveOldFederation(
            NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP353_should_save_non_standard_erp_fed_format() {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveOldFederation(
            NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP353_should_save_p2sh_erp_fed_format() {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveOldFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveOldFederation_before_RSKIP123_should_save_null() {
        activations = ActivationConfigsForTest.only().forBlock(0);
        Repository repository = mock(Repository.class);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);

        // Act
        federationStorageProvider.setOldFederation(null);
        federationStorageProvider.save(regtestBtcParams, activations);

        verify(repository, never()).addStorageBytes(
            eq(PrecompiledContracts.BRIDGE_ADDR),
            eq(OLD_FEDERATION_FORMAT_VERSION.getKey()),
            any()
        );
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            OLD_FEDERATION_KEY.getKey(),
            null
        );
    }

    @Test
    void saveOldFederation_after_RSKIP123_should_save_null() {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        Repository repository = mock(Repository.class);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);

        // Act
        federationStorageProvider.setOldFederation(null);
        federationStorageProvider.save(regtestBtcParams, activations);

        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            OLD_FEDERATION_FORMAT_VERSION.getKey(),
            BridgeSerializationUtils.serializeInteger(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION)
        );
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            OLD_FEDERATION_KEY.getKey(),
            null
        );
    }

    private static Stream<Arguments> provideFederationBtcUTXOsTestArguments() {

        ActivationConfig.ForBlock activationsWithRskip284Inactive = mock(ActivationConfig.ForBlock.class);
        when(activationsWithRskip284Inactive.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        ActivationConfig.ForBlock activationsWithRskip284ActiveAnd293Inactive = mock(ActivationConfig.ForBlock.class);
        when(activationsWithRskip284ActiveAnd293Inactive.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activationsWithRskip284ActiveAnd293Inactive.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

        ActivationConfig.ForBlock activationsWithRskip284And293Active = mock(ActivationConfig.ForBlock.class);
        when(activationsWithRskip284And293Active.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activationsWithRskip284And293Active.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        NetworkParameters mainnetNetworkParams = NetworkParameters.fromID(ID_MAINNET);
        NetworkParameters testnetNetworkParams = NetworkParameters.fromID(ID_TESTNET);

        return Stream.of(
            Arguments.of(NEW_FEDERATION_BTC_UTXOS_KEY, testnetNetworkParams, activationsWithRskip284Inactive),
            Arguments.of(NEW_FEDERATION_BTC_UTXOS_KEY, mainnetNetworkParams, null),
            Arguments.of(NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP, testnetNetworkParams, activationsWithRskip284ActiveAnd293Inactive),
            Arguments.of(NEW_FEDERATION_BTC_UTXOS_KEY, mainnetNetworkParams, null),
            Arguments.of(NEW_FEDERATION_BTC_UTXOS_KEY, mainnetNetworkParams, null),
            Arguments.of(NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_POST_HOP, testnetNetworkParams, activationsWithRskip284And293Active),
            Arguments.of(NEW_FEDERATION_BTC_UTXOS_KEY, mainnetNetworkParams, null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideFederationBtcUTXOsTestArguments")
    void getNewFederationBtcUTXOs(FederationStorageIndexKey federationStorageIndexKey, NetworkParameters networkParameters, ActivationConfig.ForBlock activations) {

        StorageAccessor storageAccessor = new InMemoryStorage();
        Address btcAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "test");

        // Save utxos directly in storage
        List<UTXO> expectedUtxos = BitcoinTestUtils.createUTXOs(2, btcAddress);
        storageAccessor.saveToRepository(federationStorageIndexKey.getKey(), BridgeSerializationUtils.serializeUTXOList(expectedUtxos));

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Getting utxos from method
        List<UTXO> actualUtxos = federationStorageProvider.getNewFederationBtcUTXOs(networkParameters, activations);

        // Should be as the expected utxos
        assertEquals(expectedUtxos, actualUtxos);

    }

    @Test
    void getNewFederationBtcUTXOs_calledTwice_returnCachedUtxos() {

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        StorageAccessor storageAccessor = new InMemoryStorage();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        Address btcAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "test");

        DataWord newFederationBtcUtxosKey = NEW_FEDERATION_BTC_UTXOS_KEY.getKey();

        // Save utxos directly in storage.
        List<UTXO> expectedUtxos = BitcoinTestUtils.createUTXOs(1, btcAddress);
        storageAccessor.saveToRepository(newFederationBtcUtxosKey, expectedUtxos, BridgeSerializationUtils::serializeUTXOList);

        // Get utxos from method and check they are as expected
        List<UTXO> actualUtxos = federationStorageProvider.getNewFederationBtcUTXOs(networkParameters, activations);
        assertEquals(1, actualUtxos.size());
        assertEquals(expectedUtxos, actualUtxos);

        // Save new utxos directly in storage
        List<UTXO> extraUtxos = new ArrayList<>(BitcoinTestUtils.createUTXOs(2, btcAddress));
        storageAccessor.saveToRepository(newFederationBtcUtxosKey, extraUtxos, BridgeSerializationUtils::serializeUTXOList);

        // Get utxos from method and check they are still the same as the original expected utxos since it is returning the cached utxos
        List<UTXO> actualUtxosAfterSecondGet = federationStorageProvider.getNewFederationBtcUTXOs(null, activations);
        assertEquals(1, actualUtxosAfterSecondGet.size());
        assertEquals(expectedUtxos, actualUtxosAfterSecondGet);

        // Get utxos directly from storage and confirm that the storage has the new utxos that the method didn't return because the method returned a cached list
        List<UTXO> actualUtxosInStorage = storageAccessor.getFromRepository(newFederationBtcUtxosKey, BridgeSerializationUtils::deserializeUTXOList);
        assertEquals(2, actualUtxosInStorage.size());
        assertEquals(extraUtxos, actualUtxosInStorage);

    }

    @Test
    void getOldFederationBtcUTXOs() {

        StorageAccessor storageAccessor = new InMemoryStorage();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        Address btcAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "test");

        DataWord oldFederationBtcUtxosKey = OLD_FEDERATION_BTC_UTXOS_KEY.getKey();

        // Save utxos directly in storage.
        List<UTXO> expectedUtxos = BitcoinTestUtils.createUTXOs(1, btcAddress);
        storageAccessor.saveToRepository(oldFederationBtcUtxosKey, expectedUtxos, BridgeSerializationUtils::serializeUTXOList);

        // Get utxos from method and check they are as expected
        List<UTXO> actualUtxos = federationStorageProvider.getOldFederationBtcUTXOs();
        assertEquals(1, actualUtxos.size());
        assertEquals(expectedUtxos, actualUtxos);

    }

    @Test
    void getOldFederationBtcUTXOs_calledTwice_returnCachedUtxos() {

        StorageAccessor storageAccessor = new InMemoryStorage();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        Address btcAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "test");

        DataWord oldFederationBtcUtxosKey = OLD_FEDERATION_BTC_UTXOS_KEY.getKey();

        // Save utxos directly in storage.
        List<UTXO> expectedUtxos = BitcoinTestUtils.createUTXOs(1, btcAddress);
        storageAccessor.saveToRepository(oldFederationBtcUtxosKey, expectedUtxos, BridgeSerializationUtils::serializeUTXOList);

        // Get utxos from method and check they are as expected
        List<UTXO> actualUtxos = federationStorageProvider.getOldFederationBtcUTXOs();
        assertEquals(1, actualUtxos.size());
        assertEquals(expectedUtxos, actualUtxos);

        // Save new utxos directly in storage
        List<UTXO> extraUtxos = new ArrayList<>(BitcoinTestUtils.createUTXOs(2, btcAddress));
        storageAccessor.saveToRepository(oldFederationBtcUtxosKey, extraUtxos, BridgeSerializationUtils::serializeUTXOList);

        // Get utxos from method and check they are still the same as the original expected utxos since it is returning the cached utxos
        List<UTXO> actualUtxosAfterSecondGet = federationStorageProvider.getOldFederationBtcUTXOs();
        assertEquals(1, actualUtxosAfterSecondGet.size());
        assertEquals(expectedUtxos, actualUtxosAfterSecondGet);

        // Get utxos directly from storage and confirm that the storage has the new utxos that the method didn't return because the method returned a cached list
        List<UTXO> actualUtxosInStorage = storageAccessor.getFromRepository(oldFederationBtcUtxosKey, BridgeSerializationUtils::deserializeUTXOList);
        assertEquals(2, actualUtxosInStorage.size());
        assertEquals(extraUtxos, actualUtxosInStorage);

    }

    @Test
    void getFederationElection_whenElectionIsInStorage_shouldReturnNewElection() {

        // Arrange

        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();

        ABICallElection expectedElection = getSampleElection("function1", authorizer);
        byte[] expectedElectionEncoded = BridgeSerializationUtils.serializeElection(expectedElection);

        StorageAccessor storageAccessor = new InMemoryStorage();
        storageAccessor.saveToRepository(FEDERATION_ELECTION_KEY.getKey(), expectedElectionEncoded);

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act

        ABICallElection actualElection = federationStorageProvider.getFederationElection(authorizer);

        // Assert

        assertArrayEquals(expectedElectionEncoded, serializeElection(actualElection));
        
    }

    @Test
    void getFederationElection_whenCalledTwice_shouldReturnCached() {

        // Arrange

        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();

        ABICallElection expectedElection = getSampleElection("function1", authorizer);
        byte[] expectedElectionEncoded = BridgeSerializationUtils.serializeElection(expectedElection);

        StorageAccessor storageAccessor = new InMemoryStorage();
        storageAccessor.saveToRepository(FEDERATION_ELECTION_KEY.getKey(), expectedElectionEncoded);

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act

        ABICallElection actualElection = federationStorageProvider.getFederationElection(authorizer);
        assertArrayEquals(expectedElectionEncoded, serializeElection(actualElection));
        ABICallElection secondElectionSample = getSampleElection("function2", authorizer);
        storageAccessor.saveToRepository(FEDERATION_ELECTION_KEY.getKey(), BridgeSerializationUtils.serializeElection(secondElectionSample));

        // Assert

        ABICallElection cachedElection = federationStorageProvider.getFederationElection(authorizer);

        assertArrayEquals(serializeElection(actualElection), serializeElection(cachedElection));
        assertFalse(Arrays.equals(expectedElectionEncoded, serializeElection(secondElectionSample)));

    }

    @Test
    void getFederationElection_whenElectionIsNotInStorage_shouldReturnDefault() {

        // Arrange

        AddressBasedAuthorizer authorizer = getTestingAddressBasedAuthorizer();

        ABICallElection expectedElection = new ABICallElection(authorizer);

        StorageAccessor storageAccessor = new InMemoryStorage();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act

        ABICallElection actualElection = federationStorageProvider.getFederationElection(authorizer);

        // Assert

        byte[] expectedElectionEncoded = BridgeSerializationUtils.serializeElection(expectedElection);

        assertArrayEquals(expectedElectionEncoded, serializeElection(actualElection));

    }

    @Test
    void getActiveFederationCreationBlockHeight_beforeRSKIP186_storageIsNotAccessedAndReturnsEmpty() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(false);

        StorageAccessor storageAccessor = new InMemoryStorage();
        // Putting some value in the storage just to then assert that before fork, the storage won't be accessed.
        storageAccessor.saveToRepository(ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), new byte[] { 1 });

        // Act

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Assert
        assertEquals(Optional.empty(), federationStorageProvider.getActiveFederationCreationBlockHeight(activations));

    }

    @Test
    void getActiveFederationCreationBlockHeight_afterRSKIP186_getsValueFromStorage() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        StorageAccessor storageAccessor = new InMemoryStorage();
        long expectedValue = 1;
        storageAccessor.saveToRepository(ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), new byte[] { (byte) expectedValue });

        // Act

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);
        Optional<Long> actualValue = federationStorageProvider.getActiveFederationCreationBlockHeight(activations);

        // Assert

        assertTrue(actualValue.isPresent());
        assertEquals(expectedValue, actualValue.get());

        // Setting in storage a different value to assert that calling the method again should return cached value

        storageAccessor.saveToRepository(ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), new byte[] { 2 });

        Optional<Long> actualCachedValue = federationStorageProvider.getActiveFederationCreationBlockHeight(activations);

        assertTrue(actualCachedValue.isPresent());
        assertEquals(expectedValue, actualCachedValue.get());

    }

    @Test
    void getActiveFederationCreationBlockHeight_afterRSKIP186AndNoValueInStorage_returnsEmpty() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        StorageAccessor storageAccessor = new InMemoryStorage();
        storageAccessor.saveToRepository(ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), null);

        // Act

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);
        Optional<Long> actualValue = federationStorageProvider.getActiveFederationCreationBlockHeight(activations);

        // Assert

        assertFalse(actualValue.isPresent());

    }

    @Test
    void setActiveFederationCreationBlockHeight() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        StorageAccessor storageAccessor = new InMemoryStorage();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act
        long expectedValue = 3;
        federationStorageProvider.setActiveFederationCreationBlockHeight(expectedValue);

        // Assert

        Optional<Long> actualValue = federationStorageProvider.getActiveFederationCreationBlockHeight(activations);

        assertTrue(actualValue.isPresent());
        assertEquals(expectedValue, actualValue.get());

    }

    @Test
    void getNextFederationCreationBlockHeight_beforeRSKIP186_storageIsNotAccessedAndReturnsEmpty() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(false);

        StorageAccessor storageAccessor = new InMemoryStorage();
        // Putting some value in the storage just to then assert that before fork, the storage won't be accessed.
        storageAccessor.saveToRepository(NEXT_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), new byte[] { 1 });

        // Act

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Assert
        assertEquals(Optional.empty(), federationStorageProvider.getNextFederationCreationBlockHeight(activations));

    }

    @Test
    void getNextFederationCreationBlockHeight_afterRSKIP186_getsValueFromStorage() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        StorageAccessor storageAccessor = new InMemoryStorage();
        long expectedValue = 1;
        storageAccessor.saveToRepository(NEXT_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), new byte[] { (byte) expectedValue });

        // Act

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);
        Optional<Long> actualValue = federationStorageProvider.getNextFederationCreationBlockHeight(activations);

        // Assert

        assertTrue(actualValue.isPresent());
        assertEquals(expectedValue, actualValue.get());

        // Setting in storage a different value to assert that calling the method again should return cached value

        storageAccessor.saveToRepository(NEXT_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), new byte[] { 2 });

        Optional<Long> actualCachedValue = federationStorageProvider.getNextFederationCreationBlockHeight(activations);

        assertTrue(actualCachedValue.isPresent());
        assertEquals(expectedValue, actualCachedValue.get());

    }

    @Test
    void getNextFederationCreationBlockHeight_afterRSKIP186AndNoValueInStorage_returnsEmpty() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        StorageAccessor storageAccessor = new InMemoryStorage();
        storageAccessor.saveToRepository(NEXT_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), null);

        // Act

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);
        Optional<Long> actualValue = federationStorageProvider.getNextFederationCreationBlockHeight(activations);

        // Assert

        assertFalse(actualValue.isPresent());

    }

    @Test
    void getLastRetiredFederationP2SHScript_beforeRSKIP186_storageIsNotAccessedAndReturnsEmpty() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(false);

        StorageAccessor storageAccessor = new InMemoryStorage();
        Script expectedScript = new P2shErpFederationBuilder().build().getP2SHScript();
        byte[] serializedScript = BridgeSerializationUtils.serializeScript(expectedScript);
        // Putting some value in the storage just to then assert that before fork, the storage won't be accessed.
        storageAccessor.saveToRepository(LAST_RETIRED_FEDERATION_P2SH_SCRIPT_KEY.getKey(), serializedScript);

        // Act

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Assert
        assertEquals(Optional.empty(), federationStorageProvider.getLastRetiredFederationP2SHScript(activations));

    }

    @Test
    void getLastRetiredFederationP2SHScript_afterRSKIP186_getsValueFromStorage() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        StorageAccessor storageAccessor = new InMemoryStorage();
        Script expectedScript = new P2shErpFederationBuilder().build().getP2SHScript();

        byte[] serializedScript = BridgeSerializationUtils.serializeScript(expectedScript);

        storageAccessor.saveToRepository(LAST_RETIRED_FEDERATION_P2SH_SCRIPT_KEY.getKey(), serializedScript);

        // Act

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);
        Optional<Script> actualValue = federationStorageProvider.getLastRetiredFederationP2SHScript(activations);

        // Assert

        assertTrue(actualValue.isPresent());
        assertEquals(expectedScript, actualValue.get());

        // Setting in storage a different value to assert that calling the method again should return cached value

        storageAccessor.saveToRepository(LAST_RETIRED_FEDERATION_P2SH_SCRIPT_KEY.getKey(), expectedScript.getProgram());

        Optional<Script> actualCachedValue = federationStorageProvider.getLastRetiredFederationP2SHScript(activations);

        assertTrue(actualCachedValue.isPresent());
        assertEquals(expectedScript, actualCachedValue.get());

    }

    @Test
    void getLastRetiredFederationP2SHScript_afterRSKIP186AndNoValueInStorage_returnsEmpty() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        StorageAccessor storageAccessor = new InMemoryStorage();
        storageAccessor.saveToRepository(LAST_RETIRED_FEDERATION_P2SH_SCRIPT_KEY.getKey(), null);

        // Act

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);
        Optional<Script> actualValue = federationStorageProvider.getLastRetiredFederationP2SHScript(activations);

        // Assert

        assertFalse(actualValue.isPresent());

    }

    @Test
    void setLastRetiredFederationP2SHScript() {

        // Arrange

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);
        StorageAccessor storageAccessor = new InMemoryStorage();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Act

        Script expectedScript = new P2shErpFederationBuilder().build().getP2SHScript();
        federationStorageProvider.setLastRetiredFederationP2SHScript(expectedScript);
        Optional<Script> actualScript = federationStorageProvider.getLastRetiredFederationP2SHScript(activations);
        // Assert

        assertTrue(actualScript.isPresent());
        assertEquals(expectedScript, actualScript.get());

    }

    @ParameterizedTest
    @MethodSource("provideFederationBtcUTXOsTestArguments")
    void save_saveNewFederationBtcUTXOs_utxosShouldBeSavedToStorage(FederationStorageIndexKey federationStorageIndexKey, NetworkParameters networkParameters, ActivationConfig.ForBlock activations) {

        StorageAccessor storageAccessor = new InMemoryStorage();
        Address btcAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "test");

        int originalAmountOfUtxos = 2;
        // Save utxos directly in storage
        List<UTXO> expectedUtxos = BitcoinTestUtils.createUTXOs(originalAmountOfUtxos, btcAddress);
        storageAccessor.saveToRepository(federationStorageIndexKey.getKey(), BridgeSerializationUtils.serializeUTXOList(expectedUtxos));

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        // Getting utxos from method
        List<UTXO> actualUtxos = federationStorageProvider.getNewFederationBtcUTXOs(networkParameters, activations);

        // Should be as the expected utxos
        assertEquals(actualUtxos.size(), originalAmountOfUtxos);
        assertEquals(expectedUtxos, actualUtxos);

        List<UTXO> extraUtxos = BitcoinTestUtils.createUTXOs(1, btcAddress);

        actualUtxos.addAll(extraUtxos);

        federationStorageProvider.save(networkParameters, activations);

        int finalExpectedAmountOfUtxos = originalAmountOfUtxos + 1;

        // Getting the utxos from storage to ensure they were stored in the storage.
        List<UTXO> finalListOfUtxos = storageAccessor.getFromRepository(federationStorageIndexKey.getKey(), BridgeSerializationUtils::deserializeUTXOList);

        assertEquals(actualUtxos.size(), finalExpectedAmountOfUtxos);
        assertEquals(actualUtxos, finalListOfUtxos);
        // Ensuring `getNewFederationBtcUTXOs` also returns the one saved in the storage.
        assertEquals(finalListOfUtxos, federationStorageProvider.getNewFederationBtcUTXOs(networkParameters, activations));

    }

    @Test
    void save_saveOldFederationBtcUTXOs_utxosShouldBeSavedToStorage() {

        StorageAccessor storageAccessor = new InMemoryStorage();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        Address btcAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "test");

        DataWord oldFederationBtcUtxosKey = OLD_FEDERATION_BTC_UTXOS_KEY.getKey();

        // Save utxos directly in storage.
        List<UTXO> expectedUtxos = BitcoinTestUtils.createUTXOs(1, btcAddress);
        storageAccessor.saveToRepository(oldFederationBtcUtxosKey, expectedUtxos, BridgeSerializationUtils::serializeUTXOList);

        // Get utxos from method and check they are as expected
        List<UTXO> actualUtxos = federationStorageProvider.getOldFederationBtcUTXOs();
        assertEquals(1, actualUtxos.size());
        assertEquals(expectedUtxos, actualUtxos);

        List<UTXO> extraUtxos = BitcoinTestUtils.createUTXOs(1, btcAddress);

        actualUtxos.addAll(extraUtxos);

        federationStorageProvider.save(networkParameters, activations);

        // Getting the utxos from storage to ensure they were stored in the storage.
        List<UTXO> finalListOfUtxos = storageAccessor.getFromRepository(oldFederationBtcUtxosKey, BridgeSerializationUtils::deserializeUTXOList);

        assertEquals(actualUtxos.size(), 2);
        assertEquals(actualUtxos, finalListOfUtxos);
        // Ensuring `getOldFederationBtcUTXOs` also returns the one saved in the storage.
        assertEquals(finalListOfUtxos, federationStorageProvider.getOldFederationBtcUTXOs());

    }

    private void testSaveOldFederation(
        int expectedFormat,
        Federation federationToSave
    ) {
        // Arrange
        Repository repository = mock(Repository.class);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);

        // Act
        federationStorageProvider.setOldFederation(federationToSave);
        federationStorageProvider.save(regtestBtcParams, activations);

        // Assert
        byte[] serializedFederation = activations.isActive(ConsensusRule.RSKIP123) ?
            BridgeSerializationUtils.serializeFederation(federationToSave) :
            BridgeSerializationUtils.serializeFederationOnlyBtcKeys(federationToSave);
        VerificationMode shouldSaveOldFederationFormatVersion = activations.isActive(ConsensusRule.RSKIP123) ?
            times(1) :
            never();

        verify(repository, shouldSaveOldFederationFormatVersion).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            OLD_FEDERATION_FORMAT_VERSION.getKey(),
            BridgeSerializationUtils.serializeInteger(expectedFormat)
        );
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            OLD_FEDERATION_KEY.getKey(),
            serializedFederation
        );
    }

    private static Federation createFederation(int version) {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        FederationArgs federationArgs = new FederationArgs(members, Instant.now(), 1L, networkParameters);
        if (version == STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION) {
            return FederationFactory.buildStandardMultiSigFederation(federationArgs);
        }

        // version should be erp
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();

        if (version == NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION) {
            return FederationFactory.buildNonStandardErpFederation(federationArgs, erpPubKeys, activationDelay, activations);
        }
        if (version == P2SH_ERP_FEDERATION_FORMAT_VERSION) {
            return FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);
        }
        // To keep backwards compatibility
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    private FederationStorageProvider createFederationStorageProvider(Repository repository) {
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
        return new FederationStorageProviderImpl(bridgeStorageAccessor);
    }

    private byte[] getFederationFormatSerialized(int federationFormat) {
        switch (federationFormat) {
            case INVALID_FEDERATION_FORMAT:
                return null;
            case EMPTY_FEDERATION_FORMAT:
                return new byte[]{};
            default:
                return BridgeSerializationUtils.serializeInteger(federationFormat);
        }
    }

    private byte[] getSerializedFederation(Federation federation, int federationFormat) {

        if(isNull(federation)) {
            return null;
        }

        if(federationFormat == INVALID_FEDERATION_FORMAT || federationFormat == EMPTY_FEDERATION_FORMAT) {
            return BridgeSerializationUtils.serializeFederationOnlyBtcKeys(federation);
        }

        return BridgeSerializationUtils.serializeFederation(federation);

    }

    private static AddressBasedAuthorizer getTestingAddressBasedAuthorizer() {
        return new AddressBasedAuthorizer(Collections.EMPTY_LIST, null) {
            public boolean isAuthorized(RskAddress addess) {
                return true;
            }
        };
    }

    private static ABICallElection getSampleElection(String functionName, AddressBasedAuthorizer authorizer) {
        Map<ABICallSpec, List<RskAddress>> sampleVotes = new HashMap<>();
        RskAddress address = new RskAddress("9be6f6735c4d59c10240d4987414fb686c6b7323");
        sampleVotes.put(
            new ABICallSpec(functionName, new byte[][]{}),
            Collections.singletonList(address)
        );
        return new ABICallElection(authorizer, sampleVotes);
    }

}
