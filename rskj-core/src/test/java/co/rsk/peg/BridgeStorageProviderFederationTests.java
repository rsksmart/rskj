package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;
import org.mockito.verification.VerificationMode;

class BridgeStorageProviderFederationTests {

    private static final int FEDERATION_FORMAT_VERSION_MULTIKEY = 1000;
    private static final int ERP_FEDERATION_FORMAT_VERSION = 2000;
    private static final int P2SH_ERP_FEDERATION_FORMAT_VERSION = 3000;

    private final BridgeConstants bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();
    private ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

    @Test
    void getNewFederation_should_return_P2shErpFederation() {
        Federation federation = createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION);

        testGetNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            federation
        );
    }

    @Test
    void getNewFederation_should_return_erp_federation() {
        Federation federation = createFederation(ERP_FEDERATION_FORMAT_VERSION);
        testGetNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            federation
        );
    }

    @Test
    void getNewFederation_should_return_legacy_federation() {
        Federation federation = createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY);
        testGetNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            federation
        );
    }

    @Test
    void getNewFederation_should_return_null() {
        testGetNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            null
        );

        testGetNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
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
            BridgeStorageIndexKey.NEW_FEDERATION_FORMAT_VERSION.getKey())
        ).thenReturn(BridgeSerializationUtils.serializeInteger(federationFormat));

        // Mock federation
        byte[] serializedFederation = null;
        if (storedFederation != null) {
            serializedFederation = BridgeSerializationUtils.serializeFederation(storedFederation);
        }

        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.NEW_FEDERATION_KEY.getKey())
        ).thenReturn(serializedFederation);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        Federation obtainedFederation = provider.getNewFederation();

        // Assert

        // Assert that the NEW_FEDERATION_FORMAT_VERSION key is read from the storage
        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.NEW_FEDERATION_FORMAT_VERSION.getKey()
        );

        // Call getNewFederation again and assert the same federation is returned
        assertEquals(obtainedFederation, provider.getNewFederation());

        // The second call to getNewFederation() should return the federation stored in memory
        int timesFederationIsReadFromRepository = 1;
        if (storedFederation == null) {
            // If there is no federation in storage it will try to get it every time getNewFederation() is called
            timesFederationIsReadFromRepository = 2;
        }
        verify(repository, times(timesFederationIsReadFromRepository)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.NEW_FEDERATION_KEY.getKey()
        );

        assertEquals(storedFederation, obtainedFederation);
    }

    @Test
    void getOldFederation_should_return_P2shErpFederation() {
        Federation federation = createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION);

        testGetOldFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            federation
        );
    }

    @Test
    void getOldFederation_should_return_erp_federation() {
        Federation federation = createFederation(ERP_FEDERATION_FORMAT_VERSION);
        testGetOldFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            federation
        );
    }

    @Test
    void getOldFederation_should_return_legacy_fed() {
        Federation federation = createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY);
        testGetOldFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            federation
        );
    }

    @Test
    void getOldFederation_should_return_null() {
        testGetOldFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            null
        );

        testGetOldFederation(
            ERP_FEDERATION_FORMAT_VERSION,
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
            BridgeStorageIndexKey.OLD_FEDERATION_FORMAT_VERSION.getKey())
        ).thenReturn(BridgeSerializationUtils.serializeInteger(federationFormat));

        // Mock federation
        byte[] serializedFederation = null;
        if (storedFederation != null) {
            serializedFederation = BridgeSerializationUtils.serializeFederation(storedFederation);
        }

        when(repository.getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.OLD_FEDERATION_KEY.getKey())
        ).thenReturn(serializedFederation);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        Federation obtainedFederation = provider.getOldFederation();

        // Assert

        // Assert that the OLD_FEDERATION_FORMAT_VERSION key is read from the storage
        verify(repository, times(1)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.OLD_FEDERATION_FORMAT_VERSION.getKey()
        );

        // Call getNewFederation again and assert the same federation is returned
        assertEquals(obtainedFederation, provider.getOldFederation());

        // The second call to getNewFederation() should return the federation stored in memory
        int timesFederationIsReadFromRepository = 1;
        if (storedFederation == null) {
            // If there is no federation in storage it will try to get it every time getNewFederation() is called
            timesFederationIsReadFromRepository = 2;
        }
        verify(repository, times(timesFederationIsReadFromRepository)).getStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.OLD_FEDERATION_KEY.getKey()
        );

        assertEquals(storedFederation, obtainedFederation);
    }

    @Test
    void saveNewFederation_before_RSKIP123_should_allow_to_save_any_fed_type() throws IOException {
        testSaveNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY)
        );

        testSaveNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION)
        );

        testSaveNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP123_should_save_legacy_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP201_should_save_legacy_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP353_should_save_legacy_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP201_should_save_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP353_should_save_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveNewFederation_after_RSKIP353_should_save_p2sh_erp_fed_format() throws IOException {
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
    void saveNewFederation_before_RSKIP123_should_not_save_null() throws IOException {
        Repository repository = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            mock(ActivationConfig.ForBlock.class)
        );

        // Act
        storageProvider.setNewFederation(null);
        storageProvider.save();

        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
    }

    @Test
    void saveNewFederation_after_RSKIP123_should_not_save_null() throws IOException {
        Repository repository = mock(Repository.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(true);

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        storageProvider.setNewFederation(null);
        storageProvider.save();

        verify(repository, never()).addStorageBytes(
            any(),
            any(),
            any()
        );
    }

    private void testSaveNewFederation(
        int expectedFormatToSave,
        Federation federationToSave
    ) throws IOException {
        // Arrange
        Repository repository = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        storageProvider.setNewFederation(federationToSave);
        storageProvider.save();

        // Assert
        byte[] serializedFederation = activations.isActive(ConsensusRule.RSKIP123) ?
            BridgeSerializationUtils.serializeFederation(federationToSave) :
            BridgeSerializationUtils.serializeFederationOnlyBtcKeys(federationToSave);
        VerificationMode shouldSaveNewFederationFormatVersion = activations.isActive(ConsensusRule.RSKIP123) ?
            times(1) :
            never();

        verify(repository, shouldSaveNewFederationFormatVersion).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.NEW_FEDERATION_FORMAT_VERSION.getKey(),
            BridgeSerializationUtils.serializeInteger(expectedFormatToSave)
        );
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.NEW_FEDERATION_KEY.getKey(),
            serializedFederation
        );
    }

    @Test
    void saveOldFederation_before_RSKIP123_should_allow_to_save_any_fed_type() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);
        testSaveOldFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY)
        );

        testSaveOldFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION)
        );

        testSaveOldFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP123_should_save_legacy_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveOldFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP201_should_save_legacy_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveOldFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP353_should_save_legacy_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveOldFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP201_should_save_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveOldFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP353_should_save_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveOldFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION)
        );
    }

    @Test
    void saveOldFederation_after_RSKIP353_should_save_p2sh_erp_fed_format() throws IOException {
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
    void saveOldFederation_before_RSKIP123_should_save_null() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);
        Repository repository = mock(Repository.class);

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        storageProvider.setOldFederation(null);
        storageProvider.save();

        verify(repository, never()).addStorageBytes(
            eq(PrecompiledContracts.BRIDGE_ADDR),
            eq(BridgeStorageIndexKey.OLD_FEDERATION_FORMAT_VERSION.getKey()),
            any()
        );
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.OLD_FEDERATION_KEY.getKey(),
            null
        );
    }

    @Test
    void saveOldFederation_after_RSKIP123_should_save_null() throws IOException {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        Repository repository = mock(Repository.class);

        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        storageProvider.setOldFederation(null);
        storageProvider.save();

        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.OLD_FEDERATION_FORMAT_VERSION.getKey(),
            BridgeSerializationUtils.serializeInteger(FEDERATION_FORMAT_VERSION_MULTIKEY)
        );
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.OLD_FEDERATION_KEY.getKey(),
            null
        );
    }

    private void testSaveOldFederation(
        int expectedFormat,
        Federation federationToSave
    ) throws IOException {
        // Arrange
        Repository repository = mock(Repository.class);
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        // Act
        storageProvider.setOldFederation(federationToSave);
        storageProvider.save();

        // Assert
        byte[] serializedFederation = activations.isActive(ConsensusRule.RSKIP123) ?
            BridgeSerializationUtils.serializeFederation(federationToSave) :
            BridgeSerializationUtils.serializeFederationOnlyBtcKeys(federationToSave);
        VerificationMode shouldSaveOldFederationFormatVersion = activations.isActive(ConsensusRule.RSKIP123) ?
            times(1) :
            never();

        verify(repository, shouldSaveOldFederationFormatVersion).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.OLD_FEDERATION_FORMAT_VERSION.getKey(),
            BridgeSerializationUtils.serializeInteger(expectedFormat)
        );
        verify(repository, times(1)).addStorageBytes(
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.OLD_FEDERATION_KEY.getKey(),
            serializedFederation
        );
    }

    private Federation createFederation(int version) {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        switch (version) {
            case P2SH_ERP_FEDERATION_FORMAT_VERSION:
                return new P2shErpFederation(
                    members,
                    Instant.now(),
                    1L,
                    bridgeConstantsRegtest.getBtcParams(),
                    bridgeConstantsRegtest.getErpFedPubKeysList(),
                    bridgeConstantsRegtest.getErpFedActivationDelay(),
                    activations
                );
            case ERP_FEDERATION_FORMAT_VERSION:
                return new LegacyErpFederation(
                    members,
                    Instant.now(),
                    1L,
                    bridgeConstantsRegtest.getBtcParams(),
                    bridgeConstantsRegtest.getErpFedPubKeysList(),
                    bridgeConstantsRegtest.getErpFedActivationDelay(),
                    activations
                );
            default:
                return new StandardMultisigFederation(
                    members,
                    Instant.now(),
                    1L,
                    bridgeConstantsRegtest.getBtcParams()
                );
        }
    }
}
