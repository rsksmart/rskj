package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;

class BridgeStorageProviderFederationTests {

    private static final int FEDERATION_FORMAT_VERSION_MULTIKEY = 1000;
    private static final int ERP_FEDERATION_FORMAT_VERSION = 2000;
    private static final int P2SH_ERP_FEDERATION_FORMAT_VERSION = 3000;

    private final BridgeConstants bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();
    private final NetworkParameters btcRegTestParams = bridgeConstantsRegtest.getBtcParams();
    private ActivationConfig.ForBlock activations;

    @Test
    void getNewFederation_should_return_P2shErpFederation() {
        activations = ActivationConfigsForTest.only().forBlock(0);

        Federation federation = createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION);

        testGetNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false,
            createFederationFrom(P2SH_ERP_FEDERATION_FORMAT_VERSION, federation)
        );
    }

    @Test
    void getNewFederation_should_return_erp_federation() {
        activations = ActivationConfigsForTest.only().forBlock(0);

        Federation federation = createFederation(ERP_FEDERATION_FORMAT_VERSION);
        testGetNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false,
            createFederationFrom(ERP_FEDERATION_FORMAT_VERSION, federation)
        );
    }

    @Test
    void getNewFederation_should_return_legacy_federation() {
        activations = ActivationConfigsForTest.only().forBlock(0);
        Federation federation = createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY);
        testGetNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            federation,
            false,
            createFederationFrom(FEDERATION_FORMAT_VERSION_MULTIKEY, federation)
        );
    }

    @Test
    void getNewFederation_format_version_different_from_fed() {
        activations = ActivationConfigsForTest.only().forBlock(0);
        Federation federation = createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY);

        // assert that the federation type does not depend on the persisted federation itself but on the format version saved
        testGetNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false,
            createFederationFrom(ERP_FEDERATION_FORMAT_VERSION, federation)
        );

        testGetNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false,
            createFederationFrom(P2SH_ERP_FEDERATION_FORMAT_VERSION, federation)
        );

        federation = createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION);

        testGetNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false,
            createFederationFrom(ERP_FEDERATION_FORMAT_VERSION, federation)
        );

        testGetNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            federation,
            false,
            createFederationFrom(FEDERATION_FORMAT_VERSION_MULTIKEY, federation)
        );
    }

    @Test
    void getNewFederation_should_return_null() {
        activations = ActivationConfigsForTest.only().forBlock(0);
        testGetNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            null,
            true,
            null
        );

        testGetNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            null,
            true,
            null
        );

        testGetNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            null,
            true,
            null
        );
    }

    private <T extends Federation, K extends Federation> void testGetNewFederation(
        int format,
        T savedFederation,
        boolean shouldReturnNull,
        K expectedFederation
    ) {
        // Arrange
        Repository repository = spy(createRepository());
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeRegTestConstants.getInstance(),
            activations
        );

        // Mock format storage version
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, BridgeStorageIndexKey.NEW_FEDERATION_FORMAT_VERSION.getKey())).thenReturn(
            BridgeSerializationUtils.serializeInteger(format)
        );

        // Mock federation
        byte[] serializeFederation = shouldReturnNull?
                                         null:
                                         BridgeSerializationUtils.serializeFederation(savedFederation);
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, BridgeStorageIndexKey.NEW_FEDERATION_KEY.getKey())).thenReturn(
            serializeFederation
        );

        // Act
        Federation resultFederation = provider.getNewFederation();

        // Assert

        // assert that resultFederation is the same instance of the first time we called `getNewFederation` method
        Federation resultFederation2 = provider.getNewFederation();
        assertEquals(resultFederation, resultFederation2);

       /* verify that repository.getStorageBytes to get the NEW_FEDERATION_FORMAT_VERSION,
         is call one time with the given values pass through the method parameters */
        verify(repository, atLeastOnce()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.NEW_FEDERATION_FORMAT_VERSION.getKey()
        );

        verify(repository, times(shouldReturnNull? 2:1)).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.NEW_FEDERATION_KEY.getKey());

        if (!shouldReturnNull){
            assertEquals(resultFederation, expectedFederation);
        } else {
            assertNull(resultFederation);
        }
    }

    @Test
    void getOldFederation_should_return_P2shErpFederation() {
        activations = ActivationConfigsForTest.only().forBlock(0);

        Federation federation = createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION);
        testGetOldFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false,
            createFederationFrom(P2SH_ERP_FEDERATION_FORMAT_VERSION, federation)
        );
    }

    @Test
    void getOldFederation_should_return_erp_federation() {
        activations = ActivationConfigsForTest.only().forBlock(0);

        Federation federation = createFederation(ERP_FEDERATION_FORMAT_VERSION);
        testGetOldFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false,
            federation
        );
    }

    @Test
    void getOldFederation_should_return_legacy_fed() {
        activations = ActivationConfigsForTest.only().forBlock(0);

        Federation federation = createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY);
        testGetOldFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            federation,
            false,
            createFederationFrom(FEDERATION_FORMAT_VERSION_MULTIKEY, federation)
        );
    }

    @Test
    void getOldFederation_format_version_different_from_fed() {
        activations = ActivationConfigsForTest.only().forBlock(0);
        Federation federation = createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY);

        // assert that the federation type does not depend on the persisted federation itself but on the format version saved
        testGetOldFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false,
            createFederationFrom(ERP_FEDERATION_FORMAT_VERSION, federation)
        );

        testGetOldFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false,
            createFederationFrom(P2SH_ERP_FEDERATION_FORMAT_VERSION, federation)
        );

        federation = createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION);

        testGetOldFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false,
            createFederationFrom(ERP_FEDERATION_FORMAT_VERSION, federation)
        );

        testGetOldFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            federation,
            false,
            createFederationFrom(FEDERATION_FORMAT_VERSION_MULTIKEY, federation)
        );
    }

    @Test
    void getOldFederation_should_return_null() {
        activations = ActivationConfigsForTest.only().forBlock(0);

        testGetOldFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            null,
            true,
            null
        );

        testGetOldFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            null,
            true,
            null
        );

        testGetOldFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            null,
            true,
            null
        );
    }

    private <T extends Federation, K extends Federation> void testGetOldFederation(
        int format,
        T savedFederation,
        boolean shouldReturnNull,
        K expectedFederation
    ) {
        // Arrange
        Repository repository = spy(createRepository());
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeRegTestConstants.getInstance(),
            activations
        );
        // Mock format storage version
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, BridgeStorageIndexKey.OLD_FEDERATION_FORMAT_VERSION.getKey())).thenReturn(
            BridgeSerializationUtils.serializeInteger(format)
        );

        // Mock federation
        byte[] serializeFederation = shouldReturnNull?
                                         null:
                                         BridgeSerializationUtils.serializeFederation(savedFederation);
        when(repository.getStorageBytes(PrecompiledContracts.BRIDGE_ADDR, BridgeStorageIndexKey.OLD_FEDERATION_KEY.getKey())).thenReturn(
            serializeFederation
        );

        // Act
        Federation resultFederation = provider.getOldFederation();

        // Assert

        // assert that resultFederation is the same instance of the first time we called `getOldFederation` method
        Federation resultFederation2 = provider.getOldFederation();
        assertEquals(resultFederation, resultFederation2);

        /* verify that repository.getStorageBytes to get the OLD_FEDERATION_FORMAT_VERSION,
         is call one time with the given values pass through the method parameters */
        verify(repository, atLeastOnce()).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.OLD_FEDERATION_FORMAT_VERSION.getKey());

        verify(repository, times(shouldReturnNull? 2:1)).getStorageBytes(PrecompiledContracts.BRIDGE_ADDR,
            BridgeStorageIndexKey.OLD_FEDERATION_KEY.getKey());

        if (!shouldReturnNull){
            assertEquals(resultFederation, expectedFederation);
        } else {
            assertNull(resultFederation);
        }
    }

    @Test
    void saveNewFederation_before_RSKIP123_should_allow_to_save_any_fed_type() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);
        testSaveNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY),
            false
        );

        testSaveNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION),
            false
        );

        testSaveNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveNewFederation_after_RSKIP123_should_save_legacy_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY),
            false
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
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY),
            false
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
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY),
            false
        );
    }

    @Test
    void saveNewFederation_before_RSKIP123_should_save_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);
        testSaveNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveNewFederation_after_RSKIP123_should_not_save_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(ERP_FEDERATION_FORMAT_VERSION),
            false
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
            createFederation(ERP_FEDERATION_FORMAT_VERSION),
            false
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
            createFederation(ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveNewFederation_before_RSKIP123_should_save_p2sh_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);
        Federation federation = createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION);
        testSaveNewFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false
        );
    }

    @Test
    void saveNewFederation_after_RSKIP123_should_not_save_p2sh_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveNewFederation_after_RSKIP201_should_not_save_p2sh_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);

        Federation federation = createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION);

        testSaveNewFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            federation,
            false
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
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveNewFederationFederation_before_RSKIP123_should_not_save_null() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);
        testSaveNewFederation(
            null,
            null,
            true
        );
    }

    @Test
    void saveNewFederationFederation_RSKIP123_should_not_save_null() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);
        testSaveNewFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            null,
            true
        );
    }

    private void testSaveNewFederation(
        Integer expectedFormatToSave,
        Federation federationToSave,
        boolean isSavingNull

    ) throws IOException {
        // Arrange
        Repository repository = spy(createRepository());
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeRegTestConstants.getInstance(),
            activations
        );

        // Act
        storageProvider.setNewFederation(federationToSave);
        storageProvider.save();

        // Assert
        if (!isSavingNull){
            if (activations.isActive(ConsensusRule.RSKIP123)){
                verify(repository, times(1)).addStorageBytes(
                    PrecompiledContracts.BRIDGE_ADDR,
                    BridgeStorageIndexKey.NEW_FEDERATION_FORMAT_VERSION.getKey(),
                    BridgeSerializationUtils.serializeInteger(expectedFormatToSave)
                );
                verify(repository, times(1)).addStorageBytes(
                    PrecompiledContracts.BRIDGE_ADDR,
                    BridgeStorageIndexKey.NEW_FEDERATION_KEY.getKey(),
                    BridgeSerializationUtils.serializeFederation(federationToSave)
                );
            } else {
                verify(repository, never()).addStorageBytes(
                    PrecompiledContracts.BRIDGE_ADDR,
                    BridgeStorageIndexKey.NEW_FEDERATION_FORMAT_VERSION.getKey(),
                    BridgeSerializationUtils.serializeInteger(expectedFormatToSave)
                );
                verify(repository, times(1)).addStorageBytes(
                    PrecompiledContracts.BRIDGE_ADDR,
                    BridgeStorageIndexKey.NEW_FEDERATION_KEY.getKey(),
                    BridgeSerializationUtils.serializeFederationOnlyBtcKeys(federationToSave)
                );
            }
            // assert that addStorageBytes it is only called the right number of time to store above values
            verify(repository, times(activations.isActive(ConsensusRule.RSKIP123)? 2:1)).addStorageBytes(
                any(),
                any(),
                any()
            );
        } else {
            verify(repository, never()).addStorageBytes(
                any(),
                any(),
                any()
            );
        }
    }

    @Test
    void saveOldFederation_before_RSKIP123_should_allow_to_save_any_fed_type() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);
        testSaveOldFederationFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY),
            false
        );

        testSaveOldFederationFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION),
            false
        );

        testSaveOldFederationFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveOldFederation_after_RSKIP123_should_save_legacy_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveOldFederationFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY),
            false
        );
    }

    @Test
    void saveOldFederation_after_RSKIP201_should_save_legacy_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveOldFederationFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY),
            false
        );
    }

    @Test
    void saveOldFederation_after_RSKIP353_should_save_legacy_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveOldFederationFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(FEDERATION_FORMAT_VERSION_MULTIKEY),
            false
        );
    }

    @Test
    void saveOldFederation_before_RSKIP123_should_save_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);

        testSaveOldFederationFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveOldFederation_after_RSKIP123_should_not_save_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveOldFederationFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveOldFederation_after_RSKIP201_should_save_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveOldFederationFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveOldFederation_after_RSKIP353_should_save_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveOldFederationFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveOldFederation_before_RSKIP123_should_save_p2sh_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);
        testSaveOldFederationFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveOldFederation_after_RSKIP123_should_not_save_p2sh_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveOldFederationFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveOldFederation_after_RSKIP201_should_not_save_p2sh_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201
        ).forBlock(0);
        testSaveOldFederationFederation(
            ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveOldFederation_after_RSKIP353_should_save_p2sh_erp_fed_format() throws IOException {
        activations = ActivationConfigsForTest.only(
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP353
        ).forBlock(0);
        testSaveOldFederationFederation(
            P2SH_ERP_FEDERATION_FORMAT_VERSION,
            createFederation(P2SH_ERP_FEDERATION_FORMAT_VERSION),
            false
        );
    }

    @Test
    void saveOldFederation_before_RSKIP123_should_save_null() throws IOException {
        activations = ActivationConfigsForTest.only().forBlock(0);
        testSaveOldFederationFederation(
            null,
            null,
            true
        );
    }

    @Test
    void saveOldFederation_RSKIP123_should_save_null() throws IOException {
        activations = ActivationConfigsForTest.only(ConsensusRule.RSKIP123).forBlock(0);
        testSaveOldFederationFederation(
            FEDERATION_FORMAT_VERSION_MULTIKEY,
            null,
            true
        );
    }

    private void testSaveOldFederationFederation(
        Integer expectedFormat,
        Federation federationToSave,
        boolean isSavingNull
    ) throws IOException {
        // Arrange
        Repository repository = spy(createRepository());
        BridgeStorageProvider storageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            BridgeRegTestConstants.getInstance(),
            activations
        );

        // Act
        storageProvider.setOldFederation(federationToSave);
        storageProvider.save();

        // Assert
        if (activations.isActive(ConsensusRule.RSKIP123)){
            verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                BridgeStorageIndexKey.OLD_FEDERATION_FORMAT_VERSION.getKey(),
                BridgeSerializationUtils.serializeInteger(expectedFormat)
            );
            verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                BridgeStorageIndexKey.OLD_FEDERATION_KEY.getKey(),
                isSavingNull? null:BridgeSerializationUtils.serializeFederation(federationToSave)
            );
        } else {
            verify(repository, never()).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                BridgeStorageIndexKey.OLD_FEDERATION_FORMAT_VERSION.getKey(),
                isSavingNull? null: BridgeSerializationUtils.serializeInteger(expectedFormat)
            );
            verify(repository, times(1)).addStorageBytes(
                PrecompiledContracts.BRIDGE_ADDR,
                BridgeStorageIndexKey.OLD_FEDERATION_KEY.getKey(),
                isSavingNull? null:BridgeSerializationUtils.serializeFederationOnlyBtcKeys(federationToSave)
            );
        }
        // assert that addStorageBytes it is only called the right number of time to store above values
        verify(repository, times(activations.isActive(ConsensusRule.RSKIP123)? 2:1)).addStorageBytes(
            any(),
            any(),
            any()
        );
    }

    private Federation createFederationFrom(int version, Federation federation){
        switch (version) {
            case P2SH_ERP_FEDERATION_FORMAT_VERSION:
                return new P2shErpFederation(
                    federation.getMembers(),
                    federation.getCreationTime(),
                    federation.getCreationBlockNumber(),
                    federation.getBtcParams(),
                    bridgeConstantsRegtest.getErpFedPubKeysList(),
                    bridgeConstantsRegtest.getErpFedActivationDelay(),
                    activations
                );
            case ERP_FEDERATION_FORMAT_VERSION:
                return new ErpFederation(
                    federation.getMembers(),
                    federation.getCreationTime(),
                    federation.getCreationBlockNumber(),
                    federation.getBtcParams(),
                    bridgeConstantsRegtest.getErpFedPubKeysList(),
                    bridgeConstantsRegtest.getErpFedActivationDelay(),
                    activations
                );
            default:
                return new Federation(
                    federation.getMembers(),
                    federation.getCreationTime(),
                    federation.getCreationBlockNumber(),
                    federation.getBtcParams()
                );
        }
    }

    private Federation createFederation(int version) {
        List<FederationMember> members = IntStream.
            range(0, 7).
            mapToObj(j -> new FederationMember(new BtcECKey(), new ECKey(), new ECKey()))
            .collect(Collectors.toList());

        switch (version) {
            case P2SH_ERP_FEDERATION_FORMAT_VERSION:
                return new P2shErpFederation(
                    members,
                    Instant.now(),
                    123,
                    btcRegTestParams,
                    bridgeConstantsRegtest.getErpFedPubKeysList(),
                    bridgeConstantsRegtest.getErpFedActivationDelay(),
                    activations
                );
            case ERP_FEDERATION_FORMAT_VERSION:
                return new ErpFederation(
                    members,
                    Instant.now(),
                    123,
                    btcRegTestParams,
                    bridgeConstantsRegtest.getErpFedPubKeysList(),
                    bridgeConstantsRegtest.getErpFedActivationDelay(),
                    activations
                );
            default:
                return new Federation(
                    members,
                    Instant.now(),
                    123,
                    btcRegTestParams
                );
        }
    }

    private static Repository createRepository() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(trieStore, new Trie(trieStore))));
    }
}
