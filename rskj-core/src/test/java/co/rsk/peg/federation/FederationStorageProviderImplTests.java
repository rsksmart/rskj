package co.rsk.peg.federation;

import static co.rsk.peg.federation.FederationFormatVersion.*;
import static co.rsk.peg.storage.FederationStorageIndexKey.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import static co.rsk.bitcoinj.core.NetworkParameters.ID_TESTNET;
import static co.rsk.bitcoinj.core.NetworkParameters.ID_MAINNET;

import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.storage.FederationStorageIndexKey;
import co.rsk.peg.vote.ABICallElection;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Repository;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.verification.VerificationMode;
import co.rsk.bitcoinj.core.UTXO;

class FederationStorageProviderImplTests {

    private static final int STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION = STANDARD_MULTISIG_FEDERATION.getFormatVersion();
    private static final int NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION = NON_STANDARD_ERP_FEDERATION.getFormatVersion();
    private static final int P2SH_ERP_FEDERATION_FORMAT_VERSION = P2SH_ERP_FEDERATION.getFormatVersion();
    private static final RskAddress bridgeAddress = PrecompiledContracts.BRIDGE_ADDR;

    private final BridgeConstants bridgeConstantsRegtest = new BridgeRegTestConstants();
    private final FederationConstants federationConstantsRegtest = bridgeConstantsRegtest.getFederationConstants();
    private final NetworkParameters regtestBtcParams = bridgeConstantsRegtest.getBtcParams();
    private ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
    private final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private final FederationConstants federationConstants = bridgeConstants.getFederationConstants();
    private final NetworkParameters networkParameters = federationConstants.getBtcParams();
    private final ActivationConfig.ForBlock activationsBeforeFork = ActivationConfigsForTest.genesis().forBlock(0L);
    private final ActivationConfig.ForBlock activationsAllForks = ActivationConfigsForTest.all().forBlock(0);

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

    @Test
    void getNewFederation_initialVersion() {
        List<Integer> storageCalls = new ArrayList<>();
        List<Integer> deserializeCalls = new ArrayList<>();
        Federation newFederation = buildMockFederation(100, 200, 300);
        Repository repositoryMock = mock(Repository.class);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

        when(repositoryMock.getStorageBytes(any(
            RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            assertEquals(bridgeAddress, contractAddress);

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assertions.assertEquals(NEW_FEDERATION_FORMAT_VERSION.getKey(), address);
                return new byte[0];
            } else {
                // Second call is the actual storage getter
                Assertions.assertEquals(2, storageCalls.size());
                Assertions.assertEquals(NEW_FEDERATION_KEY.getKey(), address);
                return new byte[]{(byte) 0xaa};
            }
        });

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeStandardMultisigFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class))).then((InvocationOnMock invocation) -> {
                deserializeCalls.add(0);
                byte[] data = invocation.getArgument(0);
                NetworkParameters networkParametersReceived = invocation.getArgument(1);

                // Make sure we're deserializing what just came from the repo with the correct BTC context
                assertArrayEquals(new byte[]{(byte) 0xaa}, data);
                Assertions.assertEquals(networkParametersReceived, networkParameters);
                return newFederation;
            });

            Assertions.assertEquals(newFederation, federationStorageProvider.getNewFederation(federationConstants, activationsBeforeFork));
            Assertions.assertEquals(newFederation, federationStorageProvider.getNewFederation(federationConstants, activationsBeforeFork));
            Assertions.assertEquals(2, storageCalls.size());
            Assertions.assertEquals(1, deserializeCalls.size());
        }
    }

    @Test
    void getNewFederation_initialVersion_nullBytes() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class, CALLS_REAL_METHODS)) {
            List<Integer> storageCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

            when(repositoryMock.getStorageBytes(any(RskAddress.class), any(DataWord.class))).then((InvocationOnMock invocation) -> {
                storageCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);

                assertEquals(bridgeAddress, contractAddress);

                if (storageCalls.size() == 1) {
                    Assertions.assertEquals(NEW_FEDERATION_FORMAT_VERSION.getKey(), address);
                    // First call is storage version getter
                    return new byte[0];
                } else {
                    // Second and third calls are actual storage getters
                    Assertions.assertTrue(storageCalls.size() == 2 || storageCalls.size() == 3);
                    Assertions.assertEquals(NEW_FEDERATION_KEY.getKey(), address);
                    return null;
                }
            });

            assertNull(federationStorageProvider.getNewFederation(federationConstants, activationsBeforeFork));
            assertNull(federationStorageProvider.getNewFederation(federationConstants, activationsBeforeFork));
            Assertions.assertEquals(3, storageCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeStandardMultisigFederationOnlyBtcKeys(any(byte[].class), any(NetworkParameters.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeStandardMultisigFederation(any(byte[].class), any(NetworkParameters.class)), never());
        }
    }

    @Test
    void getNewFederation_multiKeyVersion() {
        Federation newFederation = buildMockFederation(100, 200, 300);
        testGetNewFederationPostMultiKey(newFederation);
    }

    @Test
    void getNewFederation_non_standard_erp_and_p2sh_erp_feds() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.iris300().forBlock(0);
        Federation newFederation = buildMockFederation(100, 200, 300);

        FederationArgs federationArgs = newFederation.getArgs();
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();

        ErpFederation nonStandardErpFederation = FederationFactory.buildNonStandardErpFederation(federationArgs, erpPubKeys, activationDelay, activations);
        ErpFederation p2shErpFederation = FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);

        testGetNewFederationPostMultiKey(nonStandardErpFederation);
        testGetNewFederationPostMultiKey(p2shErpFederation);
    }

    @Test
    void getNewFederation_multiKeyVersion_nullBytes() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class, CALLS_REAL_METHODS)) {
            List<Integer> storageCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);

            FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

            when(repositoryMock.getStorageBytes(
                any(RskAddress.class),
                any(DataWord.class))
            ).then((InvocationOnMock invocation) -> {
                storageCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);

                assertEquals(bridgeAddress, contractAddress);

                if (storageCalls.size() == 1) {
                    Assertions.assertEquals(NEW_FEDERATION_FORMAT_VERSION.getKey(), address);
                    // First call is storage version getter
                    return RLP.encodeBigInteger(BigInteger.valueOf(1234));
                } else {
                    // Second and third calls are the actual storage getters
                    Assertions.assertTrue(storageCalls.size() == 2 || storageCalls.size() == 3);
                    Assertions.assertEquals(NEW_FEDERATION_KEY.getKey(), address);
                    return null;
                }
            });

            assertNull(federationStorageProvider.getNewFederation(federationConstants, activationsBeforeFork));
            assertNull(federationStorageProvider.getNewFederation(federationConstants, activationsBeforeFork));
            assertEquals(3, storageCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeStandardMultisigFederationOnlyBtcKeys(
                    any(byte[].class),
                    any(NetworkParameters.class)),
                never()
            );
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.deserializeStandardMultisigFederation(
                    any(byte[].class),
                    any(NetworkParameters.class)),
                never()
            );
        }
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

    @Test
    void saveFederationElection() {
        ABICallElection electionMock = mock(ABICallElection.class);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeElection(any(ABICallElection.class))).then((InvocationOnMock invocation) -> {
                ABICallElection election = invocation.getArgument(0);
                Assertions.assertSame(electionMock, election);
                serializeCalls.add(0);
                return Hex.decode("aabb");
            });

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                assertEquals(bridgeAddress, contractAddress);
                Assertions.assertEquals(FEDERATION_ELECTION_KEY.getKey(), address);
                assertArrayEquals(Hex.decode("aabb"), data);
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            federationStorageProvider.save(networkParameters, activationsBeforeFork);
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            Assertions.assertEquals(0, serializeCalls.size());
            TestUtils.setInternalState(federationStorageProvider, "federationElection", electionMock);
            federationStorageProvider.save(networkParameters, activationsBeforeFork);
            Assertions.assertEquals(1, storageBytesCalls.size());
            Assertions.assertEquals(1, serializeCalls.size());
        }
    }

    @Test
    void savePendingFederation_preMultikey() {
        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(100, 200, 300));
        List<Integer> storageBytesCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

        doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            assertEquals(bridgeAddress, contractAddress);
            Assertions.assertEquals(PENDING_FEDERATION_KEY.getKey(), address);
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

        federationStorageProvider.save(networkParameters, activationsBeforeFork);
        // Shouldn't have tried to save anything since pending federation is not set
        assertEquals(0, storageBytesCalls.size());

        federationStorageProvider.setPendingFederation(pendingFederation);
        // Should save the pending federation because is now set
        federationStorageProvider.save(networkParameters, activationsBeforeFork);
        // Should have called storage one time
        assertEquals(1, storageBytesCalls.size());
    }

    @Test
    void savePendingFederation_preMultikey_setToNull() {
        List<Integer> storageBytesCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);
            // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
            assertEquals(bridgeAddress, contractAddress);
            Assertions.assertEquals(PENDING_FEDERATION_KEY.getKey(), address);
            assertNull(data);
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

        federationStorageProvider.save(networkParameters, activationsBeforeFork);
        // Shouldn't have tried to save nor serialize anything
        Assertions.assertEquals(0, storageBytesCalls.size());
        federationStorageProvider.setPendingFederation(null);
        federationStorageProvider.save(networkParameters, activationsBeforeFork);
        Assertions.assertEquals(1, storageBytesCalls.size());
    }

    @Test
    void savePendingFederation_postMultikey() {
        PendingFederation pendingFederation = new PendingFederation(FederationTestUtils.getFederationMembersFromPks(100, 200, 300));
        List<Integer> storageBytesCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);

            assertEquals(bridgeAddress, contractAddress);

            if (storageBytesCalls.size() == 1) {
                Assertions.assertEquals(PENDING_FEDERATION_FORMAT_VERSION.getKey(), address);
                Assertions.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
            } else {
                Assertions.assertEquals(2, storageBytesCalls.size());
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assertions.assertEquals(PENDING_FEDERATION_KEY.getKey(), address);
            }
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

        federationStorageProvider.save(networkParameters, activationsAllForks);
        // Shouldn't have tried to save anything since pending federation is not set
        Assertions.assertEquals(0, storageBytesCalls.size());

        federationStorageProvider.setPendingFederation(pendingFederation);
        // Should save the pending federation because is now set
        federationStorageProvider.save(networkParameters, activationsAllForks);
        // Should have called storage two times since RSKIP123 is activated
        Assertions.assertEquals(2, storageBytesCalls.size());
    }

    @Test
    void savePendingFederation_postMultikey_setToNull() {
        List<Integer> storageBytesCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

        Mockito.doAnswer((InvocationOnMock invocation) -> {
            storageBytesCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);
            byte[] data = invocation.getArgument(2);

            assertEquals(bridgeAddress, contractAddress);

            if (storageBytesCalls.size() == 1) {
                Assertions.assertEquals(PENDING_FEDERATION_FORMAT_VERSION.getKey(), address);
                Assertions.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
            } else {
                Assertions.assertEquals(2, storageBytesCalls.size());
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                Assertions.assertEquals(PENDING_FEDERATION_KEY.getKey(), address);
                assertNull(data);
            }
            return null;
        }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

        federationStorageProvider.save(networkParameters, activationsAllForks);
        // Shouldn't have tried to save nor serialize anything
        Assertions.assertEquals(0, storageBytesCalls.size());
        federationStorageProvider.setPendingFederation(null);
        federationStorageProvider.save(networkParameters, activationsAllForks);
        Assertions.assertEquals(2, storageBytesCalls.size());
    }

    @Test
    void saveOldFederation_preMultikey() {
        Federation oldFederation = buildMockFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeFederationOnlyBtcKeys(
                any(Federation.class)
            )).then((InvocationOnMock invocation) -> {
                Federation federation = invocation.getArgument(0);
                Assertions.assertEquals(oldFederation, federation);
                serializeCalls.add(0);
                return new byte[]{(byte) 0xbb};
            });
            doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                assertEquals(bridgeAddress, contractAddress);
                Assertions.assertEquals(OLD_FEDERATION_KEY.getKey(), address);
                assertArrayEquals(new byte[]{(byte) 0xbb}, data);
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            federationStorageProvider.save(networkParameters, activationsBeforeFork);
            // Shouldn't have tried to save nor serialize anything
            assertEquals(0, storageBytesCalls.size());
            assertEquals(0, serializeCalls.size());
            federationStorageProvider.setOldFederation(oldFederation);
            federationStorageProvider.save(networkParameters, activationsBeforeFork);
            assertEquals(1, storageBytesCalls.size());
            assertEquals(1, serializeCalls.size());
        }
    }

    @Test
    void saveOldFederation_postMultikey() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(true);

        Federation oldFederation = buildMockFederation(100, 200, 300);
        testSaveOldFederation(oldFederation, STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, activations);
    }

    @Test
    void saveOldFederation_postMultikey_RSKIP_201_active_non_standard_erp_fed() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        Federation oldFederation = buildMockFederation(100, 200, 300);

        FederationArgs federationArgs = oldFederation.getArgs();
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();

        ErpFederation nonStandardErpFederation = FederationFactory.buildNonStandardErpFederation(federationArgs, erpPubKeys, activationDelay, activations);
        testSaveOldFederation(nonStandardErpFederation, NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, activations);
    }

    @Test
    void saveOldFederation_postMultikey_RSKIP_353_active_p2sh_erp_fed() {
        Federation oldFederation = buildMockFederation(100, 200, 300);

        FederationArgs federationArgs = oldFederation.getArgs();
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();

        ErpFederation p2shErpFederation = FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);
        testSaveOldFederation(p2shErpFederation, P2SH_ERP_FEDERATION_FORMAT_VERSION, activationsAllForks);
    }

    @Test
    void saveOldFederation_preMultikey_setToNull() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            List<Integer> storageBytesCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);

                // Make sure the bytes are set to the correct address in the repo and that what's saved is null
                assertEquals(bridgeAddress, contractAddress);
                Assertions.assertEquals(OLD_FEDERATION_KEY.getKey(), address);
                assertNull(data);
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

            federationStorageProvider.save(networkParameters, activationsBeforeFork);
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            federationStorageProvider.setOldFederation(null);
            federationStorageProvider.save(networkParameters, activationsBeforeFork);
            Assertions.assertEquals(1, storageBytesCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializeFederation(any(Federation.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class)), never());
        }
    }

    @Test
    void saveOldFederation_postMultikey_setToNull() {
        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class, CALLS_REAL_METHODS)) {
            List<Integer> storageBytesCalls = new ArrayList<>();
            Repository repositoryMock = mock(Repository.class);
            FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);

                if (storageBytesCalls.size() == 1) {
                    // First call is the version setting
                    assertEquals(bridgeAddress, contractAddress);
                    Assertions.assertEquals(OLD_FEDERATION_FORMAT_VERSION.getKey(), address);
                    Assertions.assertEquals(BigInteger.valueOf(1000), RLP.decodeBigInteger(data, 0));
                } else {
                    Assertions.assertEquals(2, storageBytesCalls.size());
                    // Make sure the bytes are set to the correct address in the repo and that what's saved is null
                    assertEquals(bridgeAddress, contractAddress);
                    Assertions.assertEquals(OLD_FEDERATION_KEY.getKey(), address);
                    assertNull(data);
                }
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any());

            federationStorageProvider.save(networkParameters, activationsAllForks);
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            federationStorageProvider.setOldFederation(null);
            federationStorageProvider.save(networkParameters, activationsAllForks);
            Assertions.assertEquals(2, storageBytesCalls.size());

            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializeFederation(any(Federation.class)), never());
            bridgeSerializationUtilsMocked.verify(() -> BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class)), never());
        }
    }

    @Test
    void saveNewFederation_preMultikey() {
        Federation newFederation = buildMockFederation(100, 200, 300);
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeFederationOnlyBtcKeys(any(Federation.class)))
                .then((InvocationOnMock invocation) -> {
                    Federation federation = invocation.getArgument(0);
                    assertEquals(newFederation, federation);
                    serializeCalls.add(0);
                    return new byte[]{(byte) 0xbb};
                });

            doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);
                // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                assertEquals(bridgeAddress, contractAddress);
                Assertions.assertEquals(NEW_FEDERATION_KEY.getKey(), address);
                assertArrayEquals(new byte[]{(byte) 0xbb}, data);
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            federationStorageProvider.save(networkParameters, activationsBeforeFork);
            // Shouldn't have tried to save nor serialize anything
            assertEquals(0, storageBytesCalls.size());
            assertEquals(0, serializeCalls.size());
            federationStorageProvider.setNewFederation(newFederation);
            federationStorageProvider.save(networkParameters, activationsBeforeFork);
            assertEquals(1, storageBytesCalls.size());
            assertEquals(1, serializeCalls.size());
        }
    }

    @Test
    void saveNewFederation_postMultiKey() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.papyrus200().forBlock(0);

        Federation newFederation = buildMockFederation(100, 200, 300);
        testSaveNewFederationPostMultiKey(newFederation, STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, activations);
    }

    @Test
    void saveNewFederation_postMultiKey_RSKIP_201_active_non_standard_erp_fed() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.iris300().forBlock(0);
        Federation newFederation = buildMockFederation(100, 200, 300);

        FederationArgs federationArgs = newFederation.getArgs();
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();

        ErpFederation nonStandardErpFederation = FederationFactory.buildNonStandardErpFederation(federationArgs, erpPubKeys, activationDelay, activations);

        testSaveNewFederationPostMultiKey(nonStandardErpFederation, NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, activations);
    }

    @Test
    void saveNewFederation_postMultiKey_RSKIP_353_active_p2sh_erp_fed() {
        Federation newFederation = buildMockFederation(100, 200, 300);

        FederationArgs federationArgs = newFederation.getArgs();
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();

        ErpFederation p2shErpFederation = FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);

        testSaveNewFederationPostMultiKey(p2shErpFederation, P2SH_ERP_FEDERATION_FORMAT_VERSION, activationsAllForks);
    }

    private void testSaveNewFederationPostMultiKey(Federation newFederation, int version, ActivationConfig.ForBlock activations) {
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            useOriginalIntegerSerialization(bridgeSerializationUtilsMocked);

            bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeFederation(any(Federation.class))).then((InvocationOnMock invocation) -> {
                Federation federation = invocation.getArgument(0);
                Assertions.assertEquals(newFederation, federation);
                serializeCalls.add(0);
                return new byte[]{(byte) 0xbb};
            });

            Mockito.doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);

                if (storageBytesCalls.size() == 1) {
                    // First call is the version setting
                    assertEquals(bridgeAddress, contractAddress);
                    Assertions.assertEquals(NEW_FEDERATION_FORMAT_VERSION.getKey(), address);
                    Assertions.assertEquals(BigInteger.valueOf(version), RLP.decodeBigInteger(data, 0));
                } else {
                    Assertions.assertEquals(2, storageBytesCalls.size());
                    // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                    assertEquals(bridgeAddress, contractAddress);
                    Assertions.assertEquals(NEW_FEDERATION_KEY.getKey(), address);
                    assertArrayEquals(new byte[]{(byte) 0xbb}, data);
                }
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            federationStorageProvider.save(networkParameters, activations);
            // Shouldn't have tried to save nor serialize anything
            Assertions.assertEquals(0, storageBytesCalls.size());
            Assertions.assertEquals(0, serializeCalls.size());
            federationStorageProvider.setNewFederation(newFederation);
            federationStorageProvider.save(networkParameters, activations);
            Assertions.assertEquals(2, storageBytesCalls.size());
            Assertions.assertEquals(1, serializeCalls.size());
        }
    }

    @Test
    void saveNewFederationBtcUTXOs_no_data() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        Repository repository = mock(Repository.class);

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);

        federationStorageProvider.save(networkParameters, activations);

        verify(repository, times(0)).addStorageBytes(
            eq(bridgeAddress),
            eq(NEW_FEDERATION_BTC_UTXOS_KEY.getKey()),
            any()
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

    private Federation buildMockFederation(Integer... pks) {
        FederationArgs federationArgs = new FederationArgs(FederationTestUtils.getFederationMembersFromPks(pks),
            Instant.ofEpochMilli(1000),
            1, networkParameters);
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    private void testGetNewFederationPostMultiKey(Federation federation) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<Integer> storageCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);

        when(repositoryMock.getStorageBytes(
            any(RskAddress.class),
            any(DataWord.class)
        )).then((InvocationOnMock invocation) -> {
            storageCalls.add(0);
            RskAddress contractAddress = invocation.getArgument(0);
            DataWord address = invocation.getArgument(1);

            assertEquals(bridgeAddress, contractAddress);

            if (storageCalls.size() == 1) {
                // First call is storage version getter
                Assertions.assertEquals(NEW_FEDERATION_FORMAT_VERSION.getKey(), address);
                int federationVersion = federation.getFormatVersion();
                return RLP.encodeBigInteger(BigInteger.valueOf(federationVersion));
            } else {
                // Second call is the actual storage getter
                Assertions.assertEquals(2, storageCalls.size());
                Assertions.assertEquals(NEW_FEDERATION_KEY.getKey(), address);
                return BridgeSerializationUtils.serializeFederation(federation);
            }
        });

        assertEquals(federation, federationStorageProvider.getNewFederation(federationConstants, activations));
        assertEquals(2, storageCalls.size());
    }

    private void testSaveOldFederation(Federation oldFederation, int version, ActivationConfig.ForBlock activations) {
        List<Integer> storageBytesCalls = new ArrayList<>();
        List<Integer> serializeCalls = new ArrayList<>();
        Repository repositoryMock = mock(Repository.class);

        try (MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked = mockStatic(BridgeSerializationUtils.class)) {
            useOriginalIntegerSerialization(bridgeSerializationUtilsMocked);

            bridgeSerializationUtilsMocked.when(
                    () -> BridgeSerializationUtils.serializeFederation(any(Federation.class)))
                .then((InvocationOnMock invocation) -> {
                    Federation federation = invocation.getArgument(0);
                    Assertions.assertEquals(oldFederation, federation);
                    serializeCalls.add(0);
                    return new byte[]{(byte) 0xbb};
                });

            doAnswer((InvocationOnMock invocation) -> {
                storageBytesCalls.add(0);
                RskAddress contractAddress = invocation.getArgument(0);
                DataWord address = invocation.getArgument(1);
                byte[] data = invocation.getArgument(2);

                if (storageBytesCalls.size() == 1) {
                    // First call is the version setting
                    assertEquals(bridgeAddress, contractAddress);
                    Assertions.assertEquals(OLD_FEDERATION_FORMAT_VERSION.getKey(), address);
                    Assertions.assertEquals(BigInteger.valueOf(version), RLP.decodeBigInteger(data, 0));
                } else {
                    Assertions.assertEquals(2, storageBytesCalls.size());
                    // Make sure the bytes are set to the correct address in the repo and that what's saved is what was serialized
                    assertEquals(bridgeAddress, contractAddress);
                    Assertions.assertEquals(OLD_FEDERATION_KEY.getKey(), address);
                    assertArrayEquals(new byte[]{(byte) 0xbb}, data);
                }
                return null;
            }).when(repositoryMock).addStorageBytes(any(RskAddress.class), any(DataWord.class), any(byte[].class));

            FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repositoryMock);
            federationStorageProvider.save(networkParameters, activations);
            // Shouldn't have tried to save nor serialize anything
            assertEquals(0, storageBytesCalls.size());
            assertEquals(0, serializeCalls.size());
            federationStorageProvider.setOldFederation(oldFederation);
            federationStorageProvider.save(networkParameters, activations);
            assertEquals(2, storageBytesCalls.size());
            assertEquals(1, serializeCalls.size());
        }
    }

    private void useOriginalIntegerSerialization(MockedStatic<BridgeSerializationUtils> bridgeSerializationUtilsMocked) {
        bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.serializeInteger(any(Integer.class))).thenCallRealMethod();
        bridgeSerializationUtilsMocked.when(() -> BridgeSerializationUtils.deserializeInteger(any(byte[].class))).thenCallRealMethod();
    }

}
