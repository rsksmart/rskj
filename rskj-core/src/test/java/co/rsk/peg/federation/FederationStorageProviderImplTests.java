package co.rsk.peg.federation;

import static co.rsk.peg.federation.FederationFormatVersion.*;
import static co.rsk.peg.storage.FederationStorageIndexKey.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import static co.rsk.bitcoinj.core.NetworkParameters.ID_TESTNET;
import static co.rsk.bitcoinj.core.NetworkParameters.ID_MAINNET;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.storage.FederationStorageIndexKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.verification.VerificationMode;
import co.rsk.bitcoinj.core.UTXO;

class FederationStorageProviderImplTests {

    private static final int STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION = STANDARD_MULTISIG_FEDERATION.getFormatVersion();
    private static final int NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION = NON_STANDARD_ERP_FEDERATION.getFormatVersion();
    private static final int P2SH_ERP_FEDERATION_FORMAT_VERSION = P2SH_ERP_FEDERATION.getFormatVersion();

    private final BridgeConstants bridgeConstantsRegtest = new BridgeRegTestConstants();
    private final FederationConstants federationConstantsRegtest = bridgeConstantsRegtest.getFederationConstants();
    private final NetworkParameters regtestBtcParams = bridgeConstantsRegtest.getBtcParams();
    private ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
    private final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private final FederationConstants federationConstants = bridgeConstants.getFederationConstants();
    private final NetworkParameters networkParameters = federationConstants.getBtcParams();

    @Test
    void getNewFederation_should_return_p2sh_erp_federation() {
        Federation federation = createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION);

        testGetNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            federation
        );
    }

    @Test
    void getNewFederation_should_return_non_standard_erp_federation() {
        Federation federation = createFederation(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION);
        testGetNewFederation(
            NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION,
            federation
        );
    }

    @Test
    void getNewFederation_should_return_standard_multisig_federation() {
        Federation federation = createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION);
        testGetNewFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            federation
        );
    }

    @Test
    void getNewFederation_should_return_null() {
        testGetNewFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            null
        );

        testGetNewFederation(
            NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION,
            null
        );

        testGetNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            null
        );
    }

    private void testGetNewFederation(
        int federationFormat,
        Federation storedFederation
    ) {
        // Arrange
        Repository repository = mock(Repository.class);

        // Mock federation format in storage
        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_FORMAT_VERSION.getKey())
        ).thenReturn(BridgeSerializationUtils.serializeInteger(federationFormat));

        // Mock federation
        byte[] serializedFederation = null;
        if (storedFederation != null) {
            serializedFederation = BridgeSerializationUtils.serializeFederation(storedFederation);
        }

        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_KEY.getKey())
        ).thenReturn(serializedFederation);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);

        // Act
        Federation obtainedFederation = federationStorageProvider.getNewFederation(federationConstantsRegtest, activations);

        // Assert

        // Assert that the NEW_FEDERATION_FORMAT_VERSION key is read from the storage
        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_FORMAT_VERSION.getKey()
        );

        // Call getNewFederation again and assert the same federation is returned
        assertEquals(obtainedFederation, federationStorageProvider.getNewFederation(federationConstantsRegtest, activations));

        // The second call to getNewFederation() should return the federation stored in memory
        int timesFederationIsReadFromRepository = 1;
        if (storedFederation == null) {
            // If there is no federation in storage it will try to get it every time getNewFederation() is called
            timesFederationIsReadFromRepository = 2;
        }
        verify(repository, times(timesFederationIsReadFromRepository)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            NEW_FEDERATION_KEY.getKey()
        );

        assertEquals(storedFederation, obtainedFederation);
    }

    @Test
    void getOldFederation_should_return_p2sh_erp_federation() {
        Federation federation = createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION);

        testGetOldFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            federation
        );
    }

    @Test
    void getOldFederation_should_return_non_standard_erp_federation() {
        Federation federation = createFederation(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION);
        testGetOldFederation(
            NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION,
            federation
        );
    }

    @Test
    void getOldFederation_should_return_standard_multisig_fed() {
        Federation federation = createFederation(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION);
        testGetOldFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            federation
        );
    }

    @Test
    void getOldFederation_should_return_null() {
        testGetOldFederation(
            STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION,
            null
        );

        testGetOldFederation(
            NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION,
            null
        );

        testGetOldFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            null
        );
    }

    private void testGetOldFederation(
        int federationFormat,
        Federation storedFederation
    ) {
        // Arrange
        Repository repository = mock(Repository.class);

        // Mock federation format in storage
        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            OLD_FEDERATION_FORMAT_VERSION.getKey())
        ).thenReturn(BridgeSerializationUtils.serializeInteger(federationFormat));

        // Mock federation
        byte[] serializedFederation = null;
        if (storedFederation != null) {
            serializedFederation = BridgeSerializationUtils.serializeFederation(storedFederation);
        }

        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            OLD_FEDERATION_KEY.getKey())
        ).thenReturn(serializedFederation);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);

        // Act
        Federation obtainedFederation = federationStorageProvider.getOldFederation(federationConstantsRegtest, activations);

        // Assert

        // Assert that the OLD_FEDERATION_FORMAT_VERSION key is read from the storage
        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            OLD_FEDERATION_FORMAT_VERSION.getKey()
        );

        // Call getNewFederation again and assert the same federation is returned
        assertEquals(obtainedFederation, federationStorageProvider.getOldFederation(federationConstantsRegtest, activations));

        // The second call to getNewFederation() should return the federation stored in memory
        int timesFederationIsReadFromRepository = 1;
        if (storedFederation == null) {
            // If there is no federation in storage it will try to get it every time getNewFederation() is called
            timesFederationIsReadFromRepository = 2;
        }
        verify(repository, times(timesFederationIsReadFromRepository)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            OLD_FEDERATION_KEY.getKey()
        );

        assertEquals(storedFederation, obtainedFederation);
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

    private Federation createFederation(int version) {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        FederationArgs federationArgs = new FederationArgs(members, Instant.now(), 1L, regtestBtcParams);
        if (version == STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION) {
            return FederationFactory.buildStandardMultiSigFederation(federationArgs);
        }

        // version should be erp
        List<BtcECKey> erpPubKeys = federationConstantsRegtest.getErpFedPubKeysList();
        long activationDelay = federationConstantsRegtest.getErpFedActivationDelay();

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

}
