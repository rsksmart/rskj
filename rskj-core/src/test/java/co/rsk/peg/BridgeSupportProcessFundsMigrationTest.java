package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.BridgeSupportTestUtil.assertLogReleaseRequested;
import static co.rsk.peg.BridgeSupportTestUtil.assertPegoutWasAddedToPegoutsWaitingForConfirmations;
import static co.rsk.peg.BridgeSupportTestUtil.assertReleaseWasSettled;
import static co.rsk.peg.BridgeSupportTestUtil.buildUpdateCollectionsTransaction;
import static co.rsk.peg.BridgeSupportTestUtil.setUpFlyoverUtxoInStorage;
import static co.rsk.peg.BridgeSupportTestUtil.setUpFlyoverUtxosInStorage;
import static co.rsk.peg.ReleaseTransactionAssertions.*;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.getBtcEcKeysFromSeeds;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static co.rsk.peg.federation.FederationStorageIndexKey.OLD_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.FlyoverRedeemScriptBuilderImpl;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbStorageIndexKey;
import co.rsk.peg.feeperkb.FeePerKbStorageProvider;
import co.rsk.peg.feeperkb.FeePerKbStorageProviderImpl;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.feeperkb.constants.FeePerKbConstants;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.test.builders.UTXOBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BridgeSupportProcessFundsMigrationTest {

    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final Coin MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE = BRIDGE_CONSTANTS.getMigrationValueForMultipleOutputs();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0L);
    private static final ActivationConfig.ForBlock VETIVER_ACTIVATIONS = ActivationConfigsForTest.vetiver900().forBlock(0L);
    private static final int MAX_INPUTS_PER_PEGOUT_TX = BRIDGE_CONSTANTS.getMaxInputsPerMigrationTransaction(ALL_ACTIVATIONS);
    private static final int ABOVE_MAX_INPUTS_PER_PEGOUT_TX = MAX_INPUTS_PER_PEGOUT_TX + 1;
    private static final int MAX_INPUTS_PER_PEGOUT_TX_LEGACY = BRIDGE_CONSTANTS.getMaxInputsPerMigrationTransaction(VETIVER_ACTIVATIONS);
    private static final int ABOVE_MAX_INPUTS_PER_PEGOUT_TX_LEGACY = MAX_INPUTS_PER_PEGOUT_TX_LEGACY + 1;
    private static final Coin MULTIPLE_OUTPUTS_THRESHOLD_BTC_VALUE = BridgeUtils.getMultipleOutputsThresholdBtcValue(BRIDGE_CONSTANTS);
    private static final Coin LARGE_MULTIPLE_OUTPUTS_THRESHOLD_BTC_VALUE = BridgeUtils.getLargeMultipleOutputsThresholdBtcValue(BRIDGE_CONSTANTS);
    private static final int ONE_MIGRATION_TX_COUNT = 1;
    private static final int TWO_MIGRATION_TXS_COUNT = 2;
    private static final Coin FEE_PER_KB = Coin.valueOf(8_000L);
    private static final Coin FUNDS_BELOW_MIGRATION_CREATION_THRESHOLD = FEE_PER_KB.divide(2);
    private static final long ACTIVE_FEDERATION_CREATION_BLOCK = 100L;
    private static final Sha256Hash BTC_TX_HASH_FLYOVER_UTXO = createHash(10_000);
    private static final Keccak256 FLYOVER_DERIVATION_HASH = RskTestUtils.createHash(100_000);
    private Transaction updateCollectionsTransaction = buildUpdateCollectionsTransaction();

    private StorageAccessor bridgeStorageAccessor;
    private BridgeStorageProvider bridgeStorageProvider;
    private FederationStorageProviderImpl federationStorageProvider;
    private FederationSupport federationSupport;
    private BridgeSupport bridgeSupport;
    private FeePerKbSupport feePerKbSupport;
    private Repository repository;
    private List<LogInfo> logs;

    @BeforeEach
    void setUp() {
        repository = createRepository();
        bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, ALL_ACTIVATIONS);
        bridgeStorageAccessor = new InMemoryStorage();
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        logs = new ArrayList<>();

        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(bridgeStorageAccessor);
        FeePerKbConstants feePerKbConstants = BRIDGE_CONSTANTS.getFeePerKbConstants();
        feePerKbSupport = new FeePerKbSupportImpl(feePerKbConstants, feePerKbStorageProvider);
        setUpFeePerKb(FEE_PER_KB);
    }

    @Nested
    class P2shP2wshErpFederationTest {

        private static final int THREE_MIGRATION_TXS_COUNT = 3;

        private final Federation retiringFederation = P2shP2wshErpFederationBuilder.builder().build();
        private final Federation activeFederation = P2shP2wshErpFederationBuilder.builder()
            .withCreationBlockNumber(ACTIVE_FEDERATION_CREATION_BLOCK)
            .withMembersBtcPublicKeys(getActiveMemberKeys(20))
            .build();
        private final Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
            FLYOVER_DERIVATION_HASH,
            retiringFederation.getRedeemScript()
        );
        private final Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, retiringFederation.getFormatVersion());
        private final UTXO flyoverUtxo = UTXOBuilder.builder()
            .withValue(Coin.COIN)
            .withScriptPubKey(flyoverOutputScript)
            .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
            .build();

        @Test
        void updateCollections_withNoRetiringFederation_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            setUpBridgeAndFederationSupportForExecutionBlock(ACTIVE_FEDERATION_CREATION_BLOCK + 1);
            setUpActiveFederation(activeFederation);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
        }

        @Test
        void updateCollections_withNewFederationAgeBeforeMigrationBegins_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = blockNumberBeforeMigrationBegins();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_withZeroBalance_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of();
            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_duringMigration_preRSKIP455_withManyMinNonDustRetiringUtxos_whenMigrationBuildFails_shouldThrowIllegalStateException() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                () -> bridgeSupport.updateCollections(updateCollectionsTransaction));
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_postRSKIP455_withManyMinNonDustRetiringUtxos_whenMigrationBuildFails_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_preRSKIP455_withBalanceBelowMigrationCreationThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                    .withValue(FUNDS_BELOW_MIGRATION_CREATION_THRESHOLD)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .build()
            );

            long executionBlockNumber = duringMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber, VETIVER_ACTIVATIONS);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_postRSKIP455_withBalanceBelowMigrationCreationThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                    .withValue(FUNDS_BELOW_MIGRATION_CREATION_THRESHOLD)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .build()
            );

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_preRSKIP455_withMoreUtxosThanMaxInputs_shouldCreateAMigrationTxEachTime() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(MAX_INPUTS_PER_PEGOUT_TX_LEGACY, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = duringMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(executionBlockNumber);
            setUpFlyoverUtxoInStorage(
                flyoverUtxo,
                flyoverOutputScript,
                retiringFederation,
                bridgeStorageProvider,
                FLYOVER_DERIVATION_HASH
            );
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX_LEGACY,
                VETIVER_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();

            int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX_LEGACY;
            assertRetiringUtxosCount(remainingUtxos);

            // Act
            long secondExecutionBlockNumber = executionBlockNumber + 1;
            setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber, VETIVER_ACTIVATIONS);
            updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                TWO_MIGRATION_TXS_COUNT,
                remainingUtxos,
                VETIVER_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_preRSKIP455_withMoreUtxosThanMaxInputs_shouldCreateLastMigrationTxWithMaxInputsAndClearRetiringFedEvenIfUtxosRemain() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(MAX_INPUTS_PER_PEGOUT_TX_LEGACY, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = pastMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(executionBlockNumber);
            setUpFlyoverUtxoInStorage(
                flyoverUtxo,
                flyoverOutputScript,
                retiringFederation,
                bridgeStorageProvider,
                FLYOVER_DERIVATION_HASH
            );
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX_LEGACY,
                VETIVER_ACTIVATIONS
            );
            assertRetiringFederationCleared();

            int expectedRemainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX_LEGACY;
            assertRetiringUtxosCount(expectedRemainingUtxos);
        }

        @Test
        void updateCollections_pastMigrationAge_withZeroBalance_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, List.of());

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_preRSKIP455_withManyMinNonDustRetiringUtxo_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_pastMigrationAge_postRSKIP455_withManyMinNonDustRetiringUtxo_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_pastMigrationAge_withHighFees_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            setUpHighFeePerKb();
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Nested
        class WithUtxosSumBelowMTMUThreshold {

            private static final int BELOW_MAX_INPUTS_PER_PEGOUT_TX = MAX_INPUTS_PER_PEGOUT_TX - 1;
            private static final Coin BELOW_MTMU_THRESHOLD_BTC_VALUE = MULTIPLE_OUTPUTS_THRESHOLD_BTC_VALUE.subtract(Coin.SATOSHI);
            private static final Coin BELOW_MTMU_UTXO_BTC_VALUE = MULTIPLE_OUTPUTS_THRESHOLD_BTC_VALUE.divide(MAX_INPUTS_PER_PEGOUT_TX);
            private final UTXO flyoverUtxo = UTXOBuilder.builder()
                .withValue(BELOW_MTMU_UTXO_BTC_VALUE)
                .withScriptPubKey(flyoverOutputScript)
                .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
                .build();

            @Test
            void updateCollections_duringMigration_withOneUtxoBelowMTMUThresholdValue_shouldCreateMigrationTx() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = List.of(
                    UTXOBuilder.builder()
                        .withValue(BELOW_MTMU_THRESHOLD_BTC_VALUE)
                        .withScriptPubKey(retiringFederation.getP2SHScript())
                        .build()
                );

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withLegacyMaxInputsPlusOneUtxos_shouldCreateMigrationTx() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(BELOW_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(ABOVE_MAX_INPUTS_PER_PEGOUT_TX_LEGACY, i -> createHash(i + 1));

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withMaxInputsPerPegoutTxUtxos_shouldCreateMigrationTx() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(BELOW_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_aboveMaxInputsPerPegoutTx_shouldCreateAMigrationTxEachTime() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(BELOW_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(ABOVE_MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call: MAX_INPUTS_PER_PEGOUT_TX inputs, 1 UTXO remaining
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();

                int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxos);

                // Act - second call: consumes the 1 remaining UTXO
                long secondExecutionBlockNumber = executionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - second call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    TWO_MIGRATION_TXS_COUNT,
                    remainingUtxos,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withTwoTimesMaxInputsPerPegoutTxUtxosPlusOneUtxos_whenEachUtxoSelectionSumIsBelowMTMUThreshold_shouldCreateAMigrationTxEachTime() throws IOException {
                // Arrange
                int numberOfUtxos = MAX_INPUTS_PER_PEGOUT_TX * 2 + 1;
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(BELOW_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: consumes MAX_INPUTS_PER_PEGOUT_TX UTXOs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                int numberOfRetiringUtxos = retiringUtxos.size();
                int remainingUtxosCount = numberOfRetiringUtxos - MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxosCount);

                // Act - second call: consumes MAX_INPUTS_PER_PEGOUT_TX more UTXOs
                long secondExecutionBlockNumber = executionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - second call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    TWO_MIGRATION_TXS_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                remainingUtxosCount -= MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxosCount);

                // Act - third call: consumes the final 1 UTXO
                long thirdExecutionBlockNumber = secondExecutionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(thirdExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(2);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - third call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    THREE_MIGRATION_TXS_COUNT,
                    remainingUtxosCount,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_pastMigrationAge_withMaxInputsPerPegoutTxUtxos_shouldCreateMigrationTxAndClearRetiringFedOnceAllSpendableFundsAreMigrated() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(BELOW_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(BELOW_MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));
                retiringUtxos.add(flyoverUtxo);

                long executionBlockNumber = pastMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpFlyoverUtxoInStorage(
                    flyoverUtxo,
                    flyoverOutputScript,
                    retiringFederation,
                    bridgeStorageProvider,
                    FLYOVER_DERIVATION_HASH
                );
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: migrates all UTXOs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();

                // Act - second call: no funds left to migrate
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber + 1);
                bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(1));

                // Assert - second call
                assertMigrationTxCount(ONE_MIGRATION_TX_COUNT, ALL_ACTIVATIONS);
                assertRetiringFederationCleared();
            }

            @Test
            void updateCollections_pastMigrationAge_aboveMaxInputsPerPegoutTx_whenFirstUtxosSelectionSumIsBelowMTMUThreshold_shouldCreateAMigrationTxEachTimeAndClearRetiringFedOnceAllSpendableFundsAreMigrated() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(BELOW_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(ABOVE_MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));

                long executionBlockNumber = pastMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: consumes MAX_INPUTS_PER_PEGOUT_TX UTXOs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();

                int remainingUtxosCount = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxosCount);

                // Act - second call: consumes the remaining UTXO
                long secondExecutionBlockNumber = executionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - second call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    TWO_MIGRATION_TXS_COUNT,
                    remainingUtxosCount,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();

                // Act - third call: no funds left to migrate
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber + 1);
                bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(2));

                // Assert - third call
                assertMigrationTxCount(TWO_MIGRATION_TXS_COUNT, ALL_ACTIVATIONS);
                assertRetiringFederationCleared();
            }
        }

        @Nested
        class WithUtxosSumBetweenMTMUAndLargeMTMUThreshold {
            private static final Coin UTXO_VALUE_BELOW_LARGE_MTMU_THRESHOLD = LARGE_MULTIPLE_OUTPUTS_THRESHOLD_BTC_VALUE.subtract(Coin.SATOSHI);
            private static final Coin ABOVE_MTMU_THRESHOLD_BTC_VALUE = MULTIPLE_OUTPUTS_THRESHOLD_BTC_VALUE.add(Coin.SATOSHI);
            private static final Coin ABOVE_MTMU_UTXO_BTC_VALUE = ABOVE_MTMU_THRESHOLD_BTC_VALUE.divide(MAX_INPUTS_PER_PEGOUT_TX).add(Coin.SATOSHI);
            private static final Coin ABOVE_MTMU_UTXO_BTC_VALUE_LEGACY = ABOVE_MTMU_THRESHOLD_BTC_VALUE.divide(MAX_INPUTS_PER_PEGOUT_TX_LEGACY);
            private static final Coin TOTAL_MTMU_MIGRATED_BTC_VALUE = ABOVE_MTMU_UTXO_BTC_VALUE.multiply(MAX_INPUTS_PER_PEGOUT_TX);
            private static final Coin LAST_MIGRATION_OUTPUT_BTC_VALUE = TOTAL_MTMU_MIGRATED_BTC_VALUE.subtract(MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE);
            private static final List<Coin> EXPECTED_OUTPUT_VALUES_FOR_MIGRATION = List.of(
                MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE,
                LAST_MIGRATION_OUTPUT_BTC_VALUE
            );
            // Maximum value for the last output in a fixed-value migration transaction.
            // If the remaining value exceeds this by even 1 satoshi, it will be split into 2 outputs.
            private static final Coin MAX_LAST_OUTPUT_VALUE_FOR_FIXED_VALUE_MIGRATION = MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE
                .multiply(2)
                .subtract(Coin.SATOSHI);

            private final UTXO flyoverUtxo = UTXOBuilder.builder()
                .withValue(ABOVE_MTMU_UTXO_BTC_VALUE)
                .withScriptPubKey(flyoverOutputScript)
                .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
                .build();

            @Test
            void updateCollections_duringMigration_withOneUtxoExactlyMTMUThresholdValue_shouldCreateMigrationTxWithMultipleOutputs() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = List.of(
                    UTXOBuilder.builder()
                        .withValue(MULTIPLE_OUTPUTS_THRESHOLD_BTC_VALUE)
                        .withScriptPubKey(retiringFederation.getP2SHScript())
                        .build()
                );

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                List<Coin> expectedOutputValues = List.of(
                    MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE,
                    MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE
                );
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }
            @Test
            void updateCollections_duringMigration_withOneUtxoAboveMTMUThresholdValue_shouldCreateMigrationTxWithMultipleOutputs() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = List.of(
                    UTXOBuilder.builder()
                        .withValue(ABOVE_MTMU_THRESHOLD_BTC_VALUE)
                        .withScriptPubKey(retiringFederation.getP2SHScript())
                        .build()
                );

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                Coin lastMigrationOutputValue = MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE.add(Coin.SATOSHI);
                List<Coin> expectedOutputValues = List.of(
                    MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE,
                    lastMigrationOutputValue
                );
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withLegacyMaxInputsPlusOneUtxos_shouldCreateMigrationTxWithMultipleOutputs() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_MTMU_UTXO_BTC_VALUE_LEGACY)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(MAX_INPUTS_PER_PEGOUT_TX_LEGACY, i -> createHash(i + 1));
                retiringUtxos.add(flyoverUtxo);

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpFlyoverUtxoInStorage(
                    flyoverUtxo,
                    flyoverOutputScript,
                    retiringFederation,
                    bridgeStorageProvider,
                    FLYOVER_DERIVATION_HASH
                );
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                Coin migratedAmount = getTotalValue(retiringUtxos);
                Coin lastMigrationOutputValue = migratedAmount.subtract(MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE);
                List<Coin> expectedOutputValues = List.of(
                    MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE,
                    lastMigrationOutputValue
                );
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withMaxInputsPerPegoutTxUtxos_shouldCreateMigrationTxWithMultipleOutputs() throws IOException {
                // Arrange
                int numberOfUtxos = MAX_INPUTS_PER_PEGOUT_TX - 1;
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                retiringUtxos.add(flyoverUtxo);

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpFlyoverUtxoInStorage(
                    flyoverUtxo,
                    flyoverOutputScript,
                    retiringFederation,
                    bridgeStorageProvider,
                    FLYOVER_DERIVATION_HASH
                );
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    EXPECTED_OUTPUT_VALUES_FOR_MIGRATION
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_aboveMaxInputsPerPegoutTx_whenFirstBatchSumIsAboveMTMUThreshold_shouldCreateAMigrationTxEachTime() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));
                retiringUtxos.add(flyoverUtxo);

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpFlyoverUtxoInStorage(
                    flyoverUtxo,
                    flyoverOutputScript,
                    retiringFederation,
                    bridgeStorageProvider,
                    FLYOVER_DERIVATION_HASH
                );
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act — first call: picks up MAX_INPUTS_PER_PEGOUT_TX UTXOs whose sum is above MTMU threshold
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert — first migration tx has 2 outputs
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    EXPECTED_OUTPUT_VALUES_FOR_MIGRATION
                );
                assertRetiringFederationStillPresent();

                int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxos);

                // Act — second call: picks up the remaining 1 UTXO whose sum is below MTMU threshold
                long secondExecutionBlockNumber = executionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert — second migration tx has 1 output
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    TWO_MIGRATION_TXS_COUNT,
                    remainingUtxos,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withTwoTimesMaxInputsPerPegoutTxUtxosPlusOneUtxos_whenFirstTwoBatchesSumIsAboveThreshold_shouldCreateAMigrationTxEachTime() throws IOException {
                // Arrange
                // 301 UTXOs: each batch of MAX_INPUTS_PER_PEGOUT_TX UTXOs sums > 40 BTC (2 outputs);
                // the single leftover (~0.267 BTC) is below the threshold (1 output)
                int numberOfUtxos = MAX_INPUTS_PER_PEGOUT_TX * 2;
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                retiringUtxos.add(flyoverUtxo);

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpFlyoverUtxoInStorage(
                    flyoverUtxo,
                    flyoverOutputScript,
                    retiringFederation,
                    bridgeStorageProvider,
                    FLYOVER_DERIVATION_HASH
                );
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: MAX_INPUTS_PER_PEGOUT_TX UTXOs above MTMU threshold, 2 outputs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    EXPECTED_OUTPUT_VALUES_FOR_MIGRATION
                );
                assertRetiringFederationStillPresent();

                int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxos);

                // Act - second call: next MAX_INPUTS_PER_PEGOUT_TX UTXOs above MTMU threshold, 2 outputs
                long secondExecutionBlockNumber = executionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - second call
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    TWO_MIGRATION_TXS_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    EXPECTED_OUTPUT_VALUES_FOR_MIGRATION
                );
                assertRetiringFederationStillPresent();
                remainingUtxos -= MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxos);

                // Act - third call: 1 remaining UTXO, 1 output
                long thirdExecutionBlockNumber = secondExecutionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(thirdExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(2);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - third call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    THREE_MIGRATION_TXS_COUNT,
                    remainingUtxos,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withOneUtxoJustBelowLargeMTMUThreshold_shouldCreateMigrationTxWithMultipleOutputs() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = List.of(
                    UTXOBuilder.builder()
                        .withValue(UTXO_VALUE_BELOW_LARGE_MTMU_THRESHOLD)
                        .withScriptPubKey(retiringFederation.getP2SHScript())
                        .build()
                );

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                List<Coin> expectedOutputValues = getExpectedOutputValuesForOneUtxoJustBelowLargeMTMUThreshold();

                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_pastMigrationAge_withMaxInputsPerPegoutTxUtxos_shouldCreateMigrationTxWithMultipleOutputsAndClearRetiringFedOnceAllSpendableFundsAreMigrated() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(MAX_INPUTS_PER_PEGOUT_TX - 1, i -> createHash(i + 1));
                retiringUtxos.add(flyoverUtxo);

                long executionBlockNumber = pastMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpFlyoverUtxoInStorage(
                    flyoverUtxo,
                    flyoverOutputScript,
                    retiringFederation,
                    bridgeStorageProvider,
                    FLYOVER_DERIVATION_HASH
                );
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: migrates all UTXOs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    EXPECTED_OUTPUT_VALUES_FOR_MIGRATION
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();

                // Act - second call: no funds left to migrate
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber + 1);
                bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(1));

                // Assert - second call
                assertMigrationTxCount(ONE_MIGRATION_TX_COUNT, ALL_ACTIVATIONS);
                assertRetiringFederationCleared();
            }

            @Test
            void updateCollections_pastMigrationAge_aboveMaxInputsPerPegoutTx_whenFirstBatchSumIsAboveMTMUThreshold_shouldCreateMigrationTxWithMultipleOutputsAndClearRetiringFedOnceAllSpendableFundsAreMigrated() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));
                retiringUtxos.add(flyoverUtxo);

                long executionBlockNumber = pastMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpFlyoverUtxoInStorage(
                    flyoverUtxo,
                    flyoverOutputScript,
                    retiringFederation,
                    bridgeStorageProvider,
                    FLYOVER_DERIVATION_HASH
                );
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: consumes MAX_INPUTS_PER_PEGOUT_TX UTXOs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    EXPECTED_OUTPUT_VALUES_FOR_MIGRATION
                );
                assertRetiringFederationStillPresent();

                int remainingUtxosCount = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxosCount);

                // Act - second call: consumes the remaining UTXO, whose value is below the MTMU threshold
                long secondExecutionBlockNumber = executionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - second call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    TWO_MIGRATION_TXS_COUNT,
                    remainingUtxosCount,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();

                // Act - third call: no funds left to migrate
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber + 1);
                bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(2));

                // Assert - third call
                assertMigrationTxCount(TWO_MIGRATION_TXS_COUNT, ALL_ACTIVATIONS);
                assertRetiringFederationCleared();
            }

            @Test
            void updateCollections_pastMigration_withOneUtxoJustBelowLargeMTMUThreshold_shouldCreateMigrationTxWithMultipleOutputsAndClearRetiringFedOnceAllFundsAreMigrated() throws IOException {
                // Arrange
                Coin utxoValueBelowLargeMTMUThreshold = LARGE_MULTIPLE_OUTPUTS_THRESHOLD_BTC_VALUE.subtract(Coin.SATOSHI);
                List<UTXO> retiringUtxos = List.of(
                    UTXOBuilder.builder()
                        .withValue(utxoValueBelowLargeMTMUThreshold)
                        .withScriptPubKey(retiringFederation.getP2SHScript())
                        .build()
                );

                long executionBlockNumber = pastMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: migrates all UTXOs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                List<Coin> expectedOutputValues = getExpectedOutputValuesForOneUtxoJustBelowLargeMTMUThreshold();
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();

                // Act - second call: no funds left to migrate
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber + 1);
                bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(1));

                // Assert - second call
                assertMigrationTxCount(ONE_MIGRATION_TX_COUNT, ALL_ACTIVATIONS);
                assertRetiringFederationCleared();
            }

            private List<Coin> getExpectedOutputValuesForOneUtxoJustBelowLargeMTMUThreshold() {
                int expectedOutputCount = BRIDGE_CONSTANTS.getMaxOutputsPerMigrationTransaction() - 1;
                List<Coin> expectedOutputValues = new ArrayList<>();
                for (int i = 0; i < expectedOutputCount - 1; i++) {
                    expectedOutputValues.add(MIGRATION_VALUE_PER_OUTPUT_BTC_VALUE);
                }

                expectedOutputValues.add(MAX_LAST_OUTPUT_VALUE_FOR_FIXED_VALUE_MIGRATION);
                return expectedOutputValues;
            }
        }

        @Nested
        class WithUtxosSumAboveLargeMTMUThreshold {

            private static final Coin ABOVE_LARGE_MTMU_THRESHOLD_BTC_VALUE = LARGE_MULTIPLE_OUTPUTS_THRESHOLD_BTC_VALUE.add(Coin.SATOSHI);
            private static final Coin ABOVE_LARGE_MTMU_UTXO_BTC_VALUE = ABOVE_LARGE_MTMU_THRESHOLD_BTC_VALUE.divide(MAX_INPUTS_PER_PEGOUT_TX).add(Coin.SATOSHI);
            private static final Coin MIGRATED_VALUE_FOR_MAX_INPUTS_IN_BTC = ABOVE_LARGE_MTMU_UTXO_BTC_VALUE.multiply(MAX_INPUTS_PER_PEGOUT_TX);

            @Test
            void updateCollections_duringMigration_withOneUtxoExactlyAtLargeMTMUThreshold_shouldCreateMigrationTxWithMaxOutputs() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = List.of(
                    UTXOBuilder.builder()
                        .withValue(LARGE_MULTIPLE_OUTPUTS_THRESHOLD_BTC_VALUE)
                        .withScriptPubKey(retiringFederation.getP2SHScript())
                        .build()
                );

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                Coin migratedAmount = getTotalValue(retiringUtxos);
                List<Coin> expectedOutputValues = buildEvenlyDistributedExpectedOutputValues(migratedAmount);
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withOneUtxoAboveLargeMTMUThresholdValue_shouldCreateMigrationTxWithMaxOutputs() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = List.of(
                    UTXOBuilder.builder()
                        .withValue(ABOVE_LARGE_MTMU_THRESHOLD_BTC_VALUE)
                        .withScriptPubKey(retiringFederation.getP2SHScript())
                        .build()
                );

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                Coin migratedAmount = getTotalValue(retiringUtxos);
                List<Coin> expectedOutputValues = buildEvenlyDistributedExpectedOutputValues(migratedAmount);
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withMaxInputsPerPegoutTxUtxos_shouldCreateMigrationTxWithMaxOutputs() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_LARGE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                Coin migratedAmount = getTotalValue(retiringUtxos);
                List<Coin> expectedOutputValues = buildEvenlyDistributedExpectedOutputValues(migratedAmount);
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withMaxInputsPerPegoutTxUtxos_allFlyover_shouldCreateMigrationTxWithMaxOutputs() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_LARGE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(flyoverOutputScript)
                    .buildMany(MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpFlyoverUtxosInStorage(
                    retiringUtxos,
                    flyoverOutputScript,
                    retiringFederation,
                    bridgeStorageProvider,
                    FLYOVER_DERIVATION_HASH
                );
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert
                Coin migratedAmount = getTotalValue(retiringUtxos);
                List<Coin> expectedOutputValues = buildEvenlyDistributedExpectedOutputValues(migratedAmount);
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_aboveMaxInputsPerPegoutTx_whenFirstBatchSumIsAboveLargeMTMUThreshold_shouldCreateAMigrationTxEachTime() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_LARGE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(ABOVE_MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: MAX_INPUTS_PER_PEGOUT_TX UTXOs above large MTMU threshold, 50 outputs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                List<Coin> expectedOutputValues = buildEvenlyDistributedExpectedOutputValues(MIGRATED_VALUE_FOR_MAX_INPUTS_IN_BTC);
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();

                int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxos);

                // Act - second call: 1 remaining UTXO below MTMU threshold, 1 output
                long secondExecutionBlockNumber = executionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - second call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    TWO_MIGRATION_TXS_COUNT,
                    remainingUtxos,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_duringMigration_withTwoTimesMaxInputsPerPegoutTxUtxosPlusOneUtxos_whenFirstTwoBatchesSumIsAboveLargeMTMUThreshold_shouldCreateAMigrationTxEachTime() throws IOException {
                // Arrange
                int numberOfUtxos = MAX_INPUTS_PER_PEGOUT_TX * 2 + 1;
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_LARGE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));

                long executionBlockNumber = duringMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: MAX_INPUTS_PER_PEGOUT_TX UTXOs above large MTMU threshold, 50 outputs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                List<Coin> expectedOutputValues = buildEvenlyDistributedExpectedOutputValues(MIGRATED_VALUE_FOR_MAX_INPUTS_IN_BTC);
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();

                int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxos);

                // Act - second call: next MAX_INPUTS_PER_PEGOUT_TX UTXOs above large MTMU threshold, 50 outputs
                long secondExecutionBlockNumber = executionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - second call
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    TWO_MIGRATION_TXS_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                remainingUtxos -= MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxos);

                // Act - third call: 1 remaining UTXO below MTMU threshold, 1 output
                long thirdExecutionBlockNumber = secondExecutionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(thirdExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(2);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - third call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    THREE_MIGRATION_TXS_COUNT,
                    remainingUtxos,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();
            }

            @Test
            void updateCollections_pastMigrationAge_withMaxInputsPerPegoutTxUtxos_shouldCreateMigrationTxWithMaxOutputsAndClearRetiringFedOnceAllSpendableFundsAreMigrated() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_LARGE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));

                long executionBlockNumber = pastMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: migrates all UTXOs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                Coin migratedAmount = getTotalValue(retiringUtxos);
                List<Coin> expectedOutputValues = buildEvenlyDistributedExpectedOutputValues(migratedAmount);
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    retiringUtxos.size(),
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();

                // Act - second call: no funds left to migrate
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber + 1);
                bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(1));

                // Assert - second call
                assertMigrationTxCount(ONE_MIGRATION_TX_COUNT, ALL_ACTIVATIONS);
                assertRetiringFederationCleared();
            }

            @Test
            void updateCollections_pastMigrationAge_aboveMaxInputsPerPegoutTx_whenFirstBatchSumIsAboveLargeMTMUThreshold_shouldCreateMigrationTxWithMaxOutputsAndClearRetiringFedOnceAllSpendableFundsAreMigrated() throws IOException {
                // Arrange
                List<UTXO> retiringUtxos = UTXOBuilder.builder()
                    .withValue(ABOVE_LARGE_MTMU_UTXO_BTC_VALUE)
                    .withScriptPubKey(retiringFederation.getP2SHScript())
                    .buildMany(ABOVE_MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));

                long executionBlockNumber = pastMigrationBlockNumber();
                setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
                setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

                // Act - first call: consumes MAX_INPUTS_PER_PEGOUT_TX UTXOs
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - first call
                List<Coin> expectedOutputValues = buildEvenlyDistributedExpectedOutputValues(MIGRATED_VALUE_FOR_MAX_INPUTS_IN_BTC);
                assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    ONE_MIGRATION_TX_COUNT,
                    MAX_INPUTS_PER_PEGOUT_TX,
                    expectedOutputValues
                );
                assertRetiringFederationStillPresent();

                int remainingUtxosCount = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
                assertRetiringUtxosCount(remainingUtxosCount);

                // Act - second call: consumes the remaining UTXO, whose value is below the MTMU threshold
                long secondExecutionBlockNumber = executionBlockNumber + 1;
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
                updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
                bridgeSupport.updateCollections(updateCollectionsTransaction);
                bridgeStorageProvider.save();

                // Assert - second call
                assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
                    retiringFederation,
                    retiringUtxos,
                    TWO_MIGRATION_TXS_COUNT,
                    remainingUtxosCount,
                    ALL_ACTIVATIONS
                );
                assertRetiringFederationStillPresent();
                assertNoRemainingRetiringUtxos();

                // Act - third call: no funds left to migrate
                setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber + 1);
                bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(2));

                // Assert - third call
                assertMigrationTxCount(TWO_MIGRATION_TXS_COUNT, ALL_ACTIVATIONS);
                assertRetiringFederationCleared();
            }
        }

        private void assertLastMigrationTxAddedWithOneOutputWasBuiltAsExpected(
            Federation retiringFederation,
            List<UTXO> retiringFederationUtxos,
            int expectedMigrationTxCount,
            int expectedInputCount,
            ActivationConfig.ForBlock activations
        ) throws IOException {
            assertMigrationTxCount(expectedMigrationTxCount, activations);

            PegoutsWaitingForConfirmations.Entry migrationEntry = getLastMigrationEntryAdded(activations);
            BtcTransaction migrationTransaction = migrationEntry.getBtcTransaction();
            List<Coin> outpointValues = extractOutpointValues(migrationTransaction);
            List<UTXO> selectedUtxos = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);
            Coin totalAmountMigrated = getTotalValue(selectedUtxos);

            assertReleaseWasSettled(
                repository,
                bridgeStorageProvider,
                logs,
                migrationEntry.getPegoutCreationRskBlockNumber(),
                migrationEntry.getPegoutCreationRskTxHash(),
                migrationTransaction,
                outpointValues,
                totalAmountMigrated,
                activations
            );

            assertBtcTxVersionIs2(migrationTransaction);
            assertReleaseTxInputsP2shP2wshErp(
                migrationTransaction,
                retiringFederation.getRedeemScript(),
                retiringFederationUtxos,
                selectedUtxos,
                expectedInputCount
            );
            assertMigrationTxWithOneOutput(migrationTransaction, selectedUtxos);
            assertMigrationReleaseTxSizeIsBelowStandardSizeAllowed(retiringFederation, migrationTransaction, activations);
        }

        private void assertLastMigrationTxAddedWithMultipleOutputsWasBuiltAsExpected(
            Federation retiringFederation,
            List<UTXO> retiringFederationUtxos,
            int expectedMigrationTxCount,
            int expectedInputCount,
            List<Coin> expectedOutputValues
        ) throws IOException {
            assertMigrationTxCount(expectedMigrationTxCount, ALL_ACTIVATIONS);

            PegoutsWaitingForConfirmations.Entry migrationEntry = getLastMigrationEntryAdded(ALL_ACTIVATIONS);
            BtcTransaction migrationTransaction = migrationEntry.getBtcTransaction();
            List<Coin> outpointValues = extractOutpointValues(migrationTransaction);
            List<UTXO> selectedUtxos = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);
            Coin totalAmountMigrated = getTotalValue(selectedUtxos);

            assertReleaseWasSettled(
                repository,
                bridgeStorageProvider,
                logs,
                migrationEntry.getPegoutCreationRskBlockNumber(),
                migrationEntry.getPegoutCreationRskTxHash(),
                migrationTransaction,
                outpointValues,
                totalAmountMigrated,
                ALL_ACTIVATIONS
            );

            assertBtcTxVersionIs2(migrationTransaction);
            assertReleaseTxInputsP2shP2wshErp(
                migrationTransaction,
                retiringFederation.getRedeemScript(),
                retiringFederationUtxos,
                selectedUtxos,
                expectedInputCount
            );
            assertMigrationTxWithMultipleOutputs(
                migrationTransaction,
                expectedOutputValues,
                federationSupport.getActiveFederationAddress(),
                NETWORK_PARAMETERS
            );
            assertMigrationReleaseTxSizeIsBelowStandardSizeAllowed(retiringFederation, migrationTransaction, ALL_ACTIVATIONS);
        }

        private List<Coin> buildEvenlyDistributedExpectedOutputValues(Coin migratedAmount) {
            int maxOutputsPerMigrationTransaction = BRIDGE_CONSTANTS.getMaxOutputsPerMigrationTransaction();
            Coin[] valueDistribution = migratedAmount.divideAndRemainder(maxOutputsPerMigrationTransaction);
            Coin valuePerOutput = valueDistribution[0];
            Coin valueRemainder = valueDistribution[1];

            List<Coin> expectedOutputValues = new ArrayList<>();
            for (int i = 0; i < maxOutputsPerMigrationTransaction - 1; i++) {
                expectedOutputValues.add(valuePerOutput);
            }
            expectedOutputValues.add(valuePerOutput.add(valueRemainder));
            return expectedOutputValues;
        }
    }

    @Nested
    class P2shErpFederationTest {

        private final Federation retiringFederation = P2shErpFederationBuilder.builder().build();
        private final Federation activeFederation = P2shErpFederationBuilder.builder()
            .withCreationBlockNumber(ACTIVE_FEDERATION_CREATION_BLOCK)
            .withMembersBtcPublicKeys(getActiveMemberKeys(9))
            .build();
        private final Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
            FLYOVER_DERIVATION_HASH,
            retiringFederation.getRedeemScript()
        );
        private final Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, retiringFederation.getFormatVersion());
        private final UTXO flyoverUtxo = UTXOBuilder.builder()
            .withValue(Coin.COIN)
            .withScriptPubKey(flyoverOutputScript)
            .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
            .build();

        @Test
        void updateCollections_withNewFederationAgeBeforeMigrationBegins_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = blockNumberBeforeMigrationBegins();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_withOneSpendableRetiringUtxo_shouldCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size(),
                ALL_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_duringMigration_withMultipleSpendableRetiringUtxos_shouldCreateMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size(),
                ALL_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_duringMigration_preRSKIP455_withManyMinNonDustRetiringUtxos_whenMigrationBuildFails_shouldThrowIllegalStateException() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                () -> bridgeSupport.updateCollections(updateCollectionsTransaction));
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_postRSKIP455_withManyMinNonDustRetiringUtxos_whenMigrationBuildFails_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_withBalanceBelowThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(FUNDS_BELOW_MIGRATION_CREATION_THRESHOLD)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_preRSKIP455_withMoreUtxosThanMaxInputs_shouldCreateAMigrationTxEachTime() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(MAX_INPUTS_PER_PEGOUT_TX_LEGACY, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = duringMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX_LEGACY,
                VETIVER_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();

            int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX_LEGACY;
            assertRetiringUtxosCount(remainingUtxos);

            // Act
            long secondExecutionBlockNumber = executionBlockNumber + 1;
            setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber, VETIVER_ACTIVATIONS);
            updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                TWO_MIGRATION_TXS_COUNT,
                retiringUtxos.size(),
                VETIVER_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withOneSpendableRetiringUtxo_shouldCreateMigrationTxAndClearRetiringFedOnceAllFundsAreMigrated() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act - first call: migrates all UTXOs
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert - first call
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size(),
                ALL_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();

            // Act - second call: no funds left to migrate
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber + 1);
            bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(1));

            // Assert - second call
            assertMigrationTxCount(ONE_MIGRATION_TX_COUNT, ALL_ACTIVATIONS);
            assertRetiringFederationCleared();
        }

        @Test
        void updateCollections_pastMigrationAge_withManySpendableRetiringUtxos_shouldCreateMigrationTxAndClearRetiringFedOnceAllFundsAreMigrated() throws IOException {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act - first call: migrates all UTXOs
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert - first call
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size(),
                ALL_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();

            // Act - second call: no funds left to migrate
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber + 1);
            bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(1));

            // Assert - second call
            assertMigrationTxCount(ONE_MIGRATION_TX_COUNT, ALL_ACTIVATIONS);
            assertRetiringFederationCleared();
        }

        @Test
        void updateCollections_pastMigrationAge_preRSKIP455_withMoreUtxosThanMaxInputs_shouldCreateLastMigrationTxWithMaxInputsAndClearRetiringFedEvenIfUtxosRemain() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(MAX_INPUTS_PER_PEGOUT_TX_LEGACY, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = pastMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX_LEGACY,
                VETIVER_ACTIVATIONS
            );
            assertRetiringFederationCleared();

            int expectedRemainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX_LEGACY;
            assertRetiringUtxosCount(expectedRemainingUtxos);
        }

        @Test
        void updateCollections_pastMigrationAge_withZeroBalance_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, List.of());

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withMinNonDustRetiringUtxo_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_pastMigrationAge_withManyMinNonDustRetiringUtxo_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_pastMigrationAge_withHighFees_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            setUpHighFeePerKb();
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );
            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        private void assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
            Federation retiringFederation,
            List<UTXO> retiringFederationUtxos,
            int expectedMigrationTxCount,
            int expectedTotalInputCount,
            ActivationConfig.ForBlock activations
        ) throws IOException {
            assertMigrationTxCount(expectedMigrationTxCount, activations);

            List<PegoutsWaitingForConfirmations.Entry> migrationEntries = getMigrationEntriesSortedByCreationAndInputsCount(activations);
            List<UTXO> migratedUtxos = new ArrayList<>();
            int remainingExpectedInputs = expectedTotalInputCount;
            for (PegoutsWaitingForConfirmations.Entry migrationEntry : migrationEntries) {
                BtcTransaction migrationTransaction = migrationEntry.getBtcTransaction();
                assertBtcTxVersionIs2(migrationTransaction);

                int expectedInputCountInTx = getExpectedInputCountInTx(remainingExpectedInputs, activations);
                List<UTXO> selectedUtxosInTx = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);
                assertReleaseTxInputsP2shErp(
                    migrationTransaction,
                    expectedInputCountInTx,
                    retiringFederation.getRedeemScript(),
                    retiringFederationUtxos,
                    selectedUtxosInTx
                );

                List<Coin> outpointValues = extractOutpointValues(migrationTransaction);
                assertReleaseWasSettled(
                    repository,
                    bridgeStorageProvider,
                    logs,
                    migrationEntry.getPegoutCreationRskBlockNumber(),
                    migrationEntry.getPegoutCreationRskTxHash(),
                    migrationTransaction,
                    outpointValues,
                    getTotalValue(selectedUtxosInTx),
                    activations
                );

                migratedUtxos.addAll(selectedUtxosInTx);
                assertMigrationTxWithOneOutput(migrationTransaction, selectedUtxosInTx);
                remainingExpectedInputs -= expectedInputCountInTx;
            }
            assertAllExpectedInputsWereIncluded(remainingExpectedInputs);
            assertExpectedUtxosWereMigrated(migratedUtxos, retiringFederationUtxos, expectedTotalInputCount);
        }
    }

    @Nested
    class StandardMultisigFederationTest {

        private static final ActivationConfig.ForBlock IRIS_ACTIVATIONS = ActivationConfigsForTest.iris300().forBlock(0L);
        private final Federation retiringFederation = StandardMultiSigFederationBuilder.builder().build();
        private final Federation activeFederation = StandardMultiSigFederationBuilder.builder()
            .withCreationBlockNumber(ACTIVE_FEDERATION_CREATION_BLOCK)
            .withMembersBtcPublicKeys(getActiveMemberKeys(9))
            .build();
        private final Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
            FLYOVER_DERIVATION_HASH,
            retiringFederation.getRedeemScript()
        );
        private final Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, retiringFederation.getFormatVersion());
        private final UTXO flyoverUtxo = UTXOBuilder.builder()
            .withValue(Coin.COIN)
            .withScriptPubKey(flyoverOutputScript)
            .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
            .build();

        @Test
        void updateCollections_withNewFederationAgeBeforeMigrationBegins_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = blockNumberBeforeMigrationBegins();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_withOneSpendableRetiringUtxo_shouldCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size(),
                ALL_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_duringMigration_withMultipleSpendableRetiringUtxos_shouldCreateMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size(),
                ALL_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_duringMigration_preRSKIP455_withManyMinNonDustRetiringUtxos_whenMigrationBuildFails_shouldThrowIllegalStateException() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                () -> bridgeSupport.updateCollections(updateCollectionsTransaction));
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_postRSKIP455_withManyMinNonDustRetiringUtxos_whenMigrationBuildFails_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_withBalanceBelowThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(FUNDS_BELOW_MIGRATION_CREATION_THRESHOLD)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_preRSKIP294_withMoreUtxosThanMaxInputs_shouldUseAllRetiringUtxos() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(ABOVE_MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber(IRIS_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForIRIS(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionBetweenStandardMultisigFedsWasBuiltAsExpectedForIRIS(
                retiringFederation,
                retiringUtxos,
                retiringUtxos.size()
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_duringMigration_preRSKIP455_withMoreUtxosThanMaxInputs_shouldCreateAMigrationTxEachTime() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(MAX_INPUTS_PER_PEGOUT_TX_LEGACY, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = duringMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX_LEGACY,
                VETIVER_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();

            int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX_LEGACY;
            assertRetiringUtxosCount(remainingUtxos);

            // Act
            long secondExecutionBlockNumber = executionBlockNumber + 1;
            setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber, VETIVER_ACTIVATIONS);
            updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                TWO_MIGRATION_TXS_COUNT,
                retiringUtxos.size(),
                VETIVER_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withOneSpendableRetiringUtxo_shouldCreateMigrationTxAndClearRetiringFedOnceAllFundsAreMigrated() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act - first call: migrates all UTXOs
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert - first call
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size(),
                ALL_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();

            // Act - second call: no funds left to migrate
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber + 1);
            bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(1));

            // Assert - second call
            assertMigrationTxCount(ONE_MIGRATION_TX_COUNT, ALL_ACTIVATIONS);
            assertRetiringFederationCleared();
        }

        @Test
        void updateCollections_pastMigrationAge_withManySpendableRetiringUtxos_shouldCreateMigrationTxAndClearRetiringFedOnceAllFundsAreMigrated() throws IOException {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act - first call: migrates all UTXOs
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert - first call
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size(),
                ALL_ACTIVATIONS
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();

            // Act - second call: no funds left to migrate
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber + 1);
            bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(1));

            // Assert - second call
            assertMigrationTxCount(ONE_MIGRATION_TX_COUNT, ALL_ACTIVATIONS);
            assertRetiringFederationCleared();
        }

        @Test
        void updateCollections_pastMigrationAge_preRSKIP294_withMoreUtxosThanMaxInputs_shouldUseAllRetiringUtxos() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(ABOVE_MAX_INPUTS_PER_PEGOUT_TX, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber(IRIS_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForIRIS(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionBetweenStandardMultisigFedsWasBuiltAsExpectedForIRIS(
                retiringFederation,
                retiringUtxos,
                ABOVE_MAX_INPUTS_PER_PEGOUT_TX
            );
            assertRetiringFederationCleared();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_preRSKIP455_withMoreUtxosThanMaxInputs_shouldCreateLastMigrationTxWithMaxInputsAndClearRetiringFedEvenIfUtxosRemain() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(MAX_INPUTS_PER_PEGOUT_TX_LEGACY, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = pastMigrationBlockNumber(VETIVER_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeStorageProvider.save();

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX_LEGACY,
                VETIVER_ACTIVATIONS
            );
            assertRetiringFederationCleared();

            int expectedRemainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX_LEGACY;
            assertRetiringUtxosCount(expectedRemainingUtxos);
        }

        @Test
        void updateCollections_pastMigrationAge_withZeroBalance_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, List.of());

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withMinNonDustRetiringUtxo_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_pastMigrationAge_withManyMinNonDustRetiringUtxo_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_pastMigrationAge_withHighFees_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            setUpHighFeePerKb();
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        private void setUpBridgeAndFederationSupportForExecutionBlockForIRIS(long executionBlockNumber) {
            bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, IRIS_ACTIVATIONS);
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber, IRIS_ACTIVATIONS);
        }

        private void assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
            Federation retiringFederation,
            List<UTXO> retiringFederationUtxos,
            int expectedMigrationTxCount,
            int expectedTotalInputCount,
            ActivationConfig.ForBlock activations
        ) throws IOException {
            assertMigrationTxCount(expectedMigrationTxCount, activations);

            List<PegoutsWaitingForConfirmations.Entry> migrationEntries = getMigrationEntriesSortedByCreationAndInputsCount(activations);
            List<UTXO> migratedUtxos = new ArrayList<>();
            int remainingExpectedInputs = expectedTotalInputCount;
            for (PegoutsWaitingForConfirmations.Entry migrationEntry : migrationEntries) {
                BtcTransaction migrationTransaction = migrationEntry.getBtcTransaction();
                assertBtcTxVersionIs2(migrationTransaction);

                int expectedInputCountInTx = getExpectedInputCountInTx(remainingExpectedInputs, activations);
                List<UTXO> selectedUtxosInTx = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);
                assertReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    expectedInputCountInTx,
                    retiringFederation.getRedeemScript(),
                    retiringFederationUtxos,
                    selectedUtxosInTx
                );

                List<Coin> outpointValues = extractOutpointValues(migrationTransaction);
                assertReleaseWasSettled(
                    repository,
                    bridgeStorageProvider,
                    logs,
                    migrationEntry.getPegoutCreationRskBlockNumber(),
                    migrationEntry.getPegoutCreationRskTxHash(),
                    migrationTransaction,
                    outpointValues,
                    getTotalValue(selectedUtxosInTx),
                    activations
                );

                migratedUtxos.addAll(selectedUtxosInTx);
                assertMigrationTxWithOneOutput(migrationTransaction, selectedUtxosInTx);
                remainingExpectedInputs -= expectedInputCountInTx;
            }
            assertAllExpectedInputsWereIncluded(remainingExpectedInputs);
            assertExpectedUtxosWereMigrated(migratedUtxos, retiringFederationUtxos, expectedTotalInputCount);
        }

        private void assertMigrationTransactionBetweenStandardMultisigFedsWasBuiltAsExpectedForIRIS(
            Federation retiringFederation,
            List<UTXO> retiringFederationUtxos,
            int expectedInputCount
        ) throws IOException {
            assertOneMigrationTxCountForIRIS();

            PegoutsWaitingForConfirmations.Entry migrationEntry = getMigrationEntryForIRIS();
            BtcTransaction migrationTransaction = migrationEntry.getBtcTransaction();
            assertBtcTxVersionIs1(migrationTransaction);
            List<UTXO> selectedUtxos = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);
            assertReleaseTxInputsStandardMultisig(
                migrationTransaction,
                expectedInputCount,
                retiringFederation.getRedeemScript(),
                retiringFederationUtxos,
                selectedUtxos
            );

            assertPegoutWasAddedToPegoutsWaitingForConfirmations(
                bridgeStorageProvider.getPegoutsWaitingForConfirmations(),
                migrationTransaction.getHash(),
                migrationEntry.getPegoutCreationRskTxHash(),
                migrationEntry.getPegoutCreationRskBlockNumber(),
                IRIS_ACTIVATIONS
            );
            assertLogReleaseRequested(
                logs,
                migrationEntry.getPegoutCreationRskTxHash(),
                migrationTransaction.getHash(),
                getTotalValue(selectedUtxos)
            );

            assertMigrationTxWithOneOutput(migrationTransaction, selectedUtxos);
            assertExpectedUtxosWereMigrated(selectedUtxos, retiringFederationUtxos, expectedInputCount);
        }

        private void assertOneMigrationTxCountForIRIS() throws IOException {
            assertMigrationTxCount(ONE_MIGRATION_TX_COUNT, IRIS_ACTIVATIONS);
        }

        private PegoutsWaitingForConfirmations.Entry getMigrationEntryForIRIS() throws IOException {
            return bridgeStorageProvider.getPegoutsWaitingForConfirmations()
                .getEntries(IRIS_ACTIVATIONS)
                .iterator()
                .next();
        }
    }

    private static void assertAllExpectedInputsWereIncluded(int remainingExpectedInputs) {
        assertEquals(0, remainingExpectedInputs);
    }

    private static int getExpectedInputCountInTx(int remainingExpectedInputs, ActivationConfig.ForBlock activations) {
        int maxInputsPerPegoutTx = BRIDGE_CONSTANTS.getMaxInputsPerMigrationTransaction(activations);
        return Math.min(maxInputsPerPegoutTx, remainingExpectedInputs);
    }

    private List<PegoutsWaitingForConfirmations.Entry> getMigrationEntriesSortedByCreationAndInputsCount(ActivationConfig.ForBlock activations) throws IOException {
        return bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries(activations)
            .stream()
            .sorted(Comparator
                .comparing(PegoutsWaitingForConfirmations.Entry::getPegoutCreationRskBlockNumber)
                .thenComparing(
                    entry -> entry.getBtcTransaction().getInputs().size(),
                    Comparator.reverseOrder()
                )
            )
            .toList();
    }

    private PegoutsWaitingForConfirmations.Entry getLastMigrationEntryAdded(ActivationConfig.ForBlock activations) throws IOException {
        List<PegoutsWaitingForConfirmations.Entry> migrationEntries = getMigrationEntriesSortedByCreationAndInputsCount(activations);
        return migrationEntries.get(migrationEntries.size() - 1);
    }

    private void assertExpectedUtxosWereMigrated(
        List<UTXO> migratedUtxos,
        List<UTXO> retiringFederationUtxos,
        int expectedTotalInputCount
    ) {
        assertEquals(expectedTotalInputCount, migratedUtxos.size());
        assertTrue(retiringFederationUtxos.containsAll(migratedUtxos));
        assertEquals(migratedUtxos.size(), migratedUtxos.stream().distinct().count());
    }

    private void assertMigrationTxWithOneOutput(BtcTransaction migrationTransaction, List<UTXO> migratedUtxos) {
        Coin migratedAmount = getTotalValue(migratedUtxos);
        ReleaseTransactionAssertions.assertOneMigrationTxOutput(
            migrationTransaction,
            migratedAmount,
            federationSupport.getActiveFederationAddress(),
            NETWORK_PARAMETERS
        );
    }

    private static Coin getTotalValue(List<UTXO> selectedUtxos) {
        return selectedUtxos.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
    }

    private void setUpHighFeePerKb() {
        Coin highFeePerKb = Coin.COIN.multiply(2);
        setUpFeePerKb(highFeePerKb);
    }

    private void setUpBridgeAndFederationSupportForExecutionBlock(long executionBlockNumber) {
        setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber, ALL_ACTIVATIONS);
    }

    private void setUpBridgeAndFederationSupportForExecutionBlockForVETIVER(long executionBlockNumber) {
        bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, VETIVER_ACTIVATIONS);
        setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber, VETIVER_ACTIVATIONS);
    }

    private void setUpBridgeAndFederationSupportForExecutionBlock(long executionBlockNumber, ActivationConfig.ForBlock activations) {
        org.ethereum.core.Block executionBlock = new BlockGenerator().createBlock(executionBlockNumber, 1);

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(BRIDGE_CONSTANTS, activations, logs);

        bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(bridgeStorageProvider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withEventLogger(bridgeEventLogger)
            .build();
    }

    private void setUpFeePerKb(Coin feePerKb) {
        bridgeStorageAccessor.saveToRepository(FeePerKbStorageIndexKey.FEE_PER_KB.getKey(), feePerKb, BridgeSerializationUtils::serializeCoin);
    }

    private void setUpActiveFederation(Federation activeFederation) {
        federationStorageProvider.setNewFederation(activeFederation);
    }

    private void setUpActiveAndRetiringFederations(
        Federation activeFederation,
        Federation retiringFederation,
        List<UTXO> retiringUtxos
    ) {
        federationStorageProvider.setNewFederation(activeFederation);
        federationStorageProvider.setOldFederation(retiringFederation);
        bridgeStorageAccessor.saveToRepository(
            OLD_FEDERATION_BTC_UTXOS_KEY.getKey(),
            retiringUtxos,
            BridgeSerializationUtils::serializeUTXOList
        );
    }

    private long blockNumberBeforeMigrationBegins() {
        return blockNumberBeforeMigrationBegins(ALL_ACTIVATIONS);
    }

    private long blockNumberBeforeMigrationBegins(ActivationConfig.ForBlock activations) {
        long activationAge = FEDERATION_CONSTANTS.getFederationActivationAge(activations);
        return ACTIVE_FEDERATION_CREATION_BLOCK +
            activationAge +
            FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationBegin();
    }

    private long duringMigrationBlockNumber() {
        return duringMigrationBlockNumber(ALL_ACTIVATIONS);
    }

    private long duringMigrationBlockNumber(ActivationConfig.ForBlock activations) {
        return blockNumberBeforeMigrationBegins(activations) + 1;
    }

    private long pastMigrationBlockNumber() {
        return pastMigrationBlockNumber(ALL_ACTIVATIONS);
    }

    private long pastMigrationBlockNumber(ActivationConfig.ForBlock activations) {
        long migrationPeriodDuration = FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationEnd(activations) -
            FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationBegin();
        return blockNumberBeforeMigrationBegins(activations) + migrationPeriodDuration;
    }

    private List<UTXO> getSelectedUtxos(BtcTransaction migrationTransaction, List<UTXO> federationUtxos) {
        return migrationTransaction.getInputs()
            .stream()
            .map(input -> federationUtxos.stream()
                .filter(utxo -> utxo.getHash().equals(input.getOutpoint().getHash()) &&
                    utxo.getIndex() == input.getOutpoint().getIndex())
                .findFirst()
                .orElseThrow())
            .toList();
    }

    private void assertMigrationTxCount(int expectedCount, ActivationConfig.ForBlock activations) throws IOException {
        assertEquals(expectedCount, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(activations).size());
    }

    private void assertNoMigrationTxCreated() throws IOException {
        assertMigrationTxCount(0, ALL_ACTIVATIONS);
    }

    private void assertRetiringFederationStillPresent() {
        assertTrue(federationSupport.getRetiringFederation().isPresent());
    }

    private void assertRetiringFederationCleared() {
        assertTrue(federationSupport.getRetiringFederation().isEmpty());
    }

    private void assertRetiringUtxosCount(int expectedCount) {
        assertEquals(expectedCount, federationStorageProvider.getOldFederationBtcUTXOs().size());
    }

    private void assertNoRemainingRetiringUtxos() {
        assertEquals(0, federationStorageProvider.getOldFederationBtcUTXOs().size());
    }

    private List<BtcECKey> getActiveMemberKeys(int numberOfMembers) {
        String[] memberSeeds = new String[numberOfMembers];
        for (int i = 0; i < numberOfMembers; i++) {
            memberSeeds[i] = String.format("newActiveMember-%s", i);
        }
        return getBtcEcKeysFromSeeds(memberSeeds, true);
    }
}
