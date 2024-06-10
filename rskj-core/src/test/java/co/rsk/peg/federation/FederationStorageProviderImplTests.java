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
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.InMemoryStorage;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
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
import org.junit.jupiter.api.Assertions;

class FederationStorageProviderImplTests {

    private static final int STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION = STANDARD_MULTISIG_FEDERATION.getFormatVersion();
    private static final int NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION = NON_STANDARD_ERP_FEDERATION.getFormatVersion();
    private static final int P2SH_ERP_FEDERATION_FORMAT_VERSION = P2SH_ERP_FEDERATION.getFormatVersion();

    private final BridgeConstants bridgeConstantsRegtest = new BridgeRegTestConstants();
    private final FederationConstants federationConstantsRegtest = bridgeConstantsRegtest.getFederationConstants();
    private final NetworkParameters regtestBtcParams = bridgeConstantsRegtest.getBtcParams();
    private ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
    private final BridgeConstants bridgeConstantsTestnet = BridgeTestNetConstants.getInstance();
    private final FederationConstants federationConstantsTestnet = bridgeConstantsTestnet.getFederationConstants();
    private final NetworkParameters testnetBtcParams = federationConstantsTestnet.getBtcParams();
    private final RskAddress bridgeAddress = PrecompiledContracts.BRIDGE_ADDR;

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
        return Stream.of(
            Arguments.of(false, false, NetworkParameters.ID_TESTNET),
            Arguments.of(false, false, NetworkParameters.ID_MAINNET),
            Arguments.of(false, true, NetworkParameters.ID_TESTNET),
            Arguments.of(false, true, NetworkParameters.ID_MAINNET),
            Arguments.of(true, false, NetworkParameters.ID_TESTNET),
            Arguments.of(true, false, NetworkParameters.ID_MAINNET),
            Arguments.of(true, true, NetworkParameters.ID_TESTNET),
            Arguments.of(true, true, NetworkParameters.ID_MAINNET)
        );
    }

    @ParameterizedTest
    @MethodSource("provideFederationBtcUTXOsTestArguments")
    void testGetNewFederationBtcUTXOsWithCombinationsOfRSKIPsAndNetworks(boolean isRskip284Active, boolean isRskip293Active, String networkId) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        NetworkParameters networkParameters = NetworkParameters.fromID(networkId);

        Address btcAddress = new Address(testnetBtcParams, Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));

        Repository repository = mock(Repository.class);
        List<UTXO> federationUtxos = BitcoinTestUtils.createUTXOs(1, btcAddress);
        when(repository.getStorageBytes(
            bridgeAddress,
            NEW_FEDERATION_BTC_UTXOS_KEY.getKey()
        )).thenReturn(BridgeSerializationUtils.serializeUTXOList(federationUtxos));

        List<UTXO> federationUtxosAfterRskip284Activation = BitcoinTestUtils.createUTXOs(2, btcAddress);

        when(repository.getStorageBytes(
            bridgeAddress,
            NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP.getKey()
        )).thenReturn(BridgeSerializationUtils.serializeUTXOList(federationUtxosAfterRskip284Activation));

        List<UTXO> federationUtxosAfterRskip293Activation = BitcoinTestUtils.createUTXOs(2, btcAddress);
        when(repository.getStorageBytes(
            bridgeAddress,
            NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_POST_HOP.getKey()
        )).thenReturn(BridgeSerializationUtils.serializeUTXOList(federationUtxosAfterRskip293Activation));

        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);

        List<UTXO> obtainedUtxos = federationStorageProvider.getNewFederationBtcUTXOs(networkParameters, activations);

        if (!networkId.equals(NetworkParameters.ID_TESTNET)) {
            Assertions.assertEquals(federationUtxos, obtainedUtxos);
            return;
        }

        // testnet
        // rskip284 & rskip293 are not active
        if (!isRskip284Active) {
            Assertions.assertEquals(federationUtxos, obtainedUtxos);
            return;
        }

        // rskip284 is active
        if (!isRskip293Active) {
            Assertions.assertEquals(federationUtxosAfterRskip284Activation, obtainedUtxos);
            return;
        }

        // rskip293 is active
        Assertions.assertEquals(federationUtxosAfterRskip293Activation, obtainedUtxos);
    }

    @Test
    void getNewFederationBtcUTXOs_calledTwice_returnCachedUtxos() {

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        StorageAccessor storageAccessor = new InMemoryStorage();

        DataWord newFederationBtcUtxosKey = NEW_FEDERATION_BTC_UTXOS_KEY.getKey();

        Address btcAddress = new Address(testnetBtcParams, Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));
        List<UTXO> expectedUtxos = BitcoinTestUtils.createUTXOs(1, btcAddress);
        storageAccessor.saveToRepository(newFederationBtcUtxosKey, expectedUtxos, BridgeSerializationUtils::serializeUTXOList);

        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(storageAccessor);

        List<UTXO> actualUtxos = federationStorageProvider.getNewFederationBtcUTXOs(testnetBtcParams, activations);

        assertEquals(1, actualUtxos.size());
        assertEquals(expectedUtxos, actualUtxos);

        List<UTXO> extraUtxos = new ArrayList<>(expectedUtxos);
        extraUtxos.addAll(BitcoinTestUtils.createUTXOs(1, btcAddress));

        storageAccessor.saveToRepository(newFederationBtcUtxosKey, extraUtxos, BridgeSerializationUtils::serializeUTXOList);

        List<UTXO> actualUtxosAfterSecondGet = federationStorageProvider.getNewFederationBtcUTXOs(null, activations);

        assertEquals(1, actualUtxosAfterSecondGet.size());
        assertEquals(expectedUtxos, actualUtxosAfterSecondGet);

        List<UTXO> actualUtxosInStorage = storageAccessor.getFromRepository(newFederationBtcUtxosKey, BridgeSerializationUtils::deserializeUTXOList);

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
