package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.BridgeSupportTestUtil.buildUpdateCollectionsTransaction;
import static co.rsk.peg.BridgeSupportTestUtil.setUpFlyoverUtxoInStorage;
import static co.rsk.peg.ReleaseTransactionAssertions.*;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.getBtcEcKeysFromSeeds;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BridgeSupportProcessFundsMigrationTest {

    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final int MAX_INPUTS_PER_PEGOUT_TX = BRIDGE_CONSTANTS.getMaxInputsPerPegoutTransaction();
    private static final int ABOVE_MAX_INPUTS_PER_PEGOUT_TX = MAX_INPUTS_PER_PEGOUT_TX + 1;
    private static final int ONE_MIGRATION_TX_COUNT = 1;
    private static final int TWO_MIGRATION_TXS_COUNT = 2;
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0L);
    private static final Coin FEE_PER_KB = Coin.valueOf(8_000L);
    private static final Coin FUNDS_BELOW_MIGRATION_THRESHOLD = FEE_PER_KB.divide(2);
    private static final long ACTIVE_FEDERATION_CREATION_BLOCK = 100L;
    private static final Sha256Hash BTC_TX_HASH_FLYOVER_UTXO = createHash(10_000);
    private static final Keccak256 FLYOVER_DERIVATION_HASH = RskTestUtils.createHash(100_000);
    private final Transaction updateCollectionsTransaction = buildUpdateCollectionsTransaction();

    private StorageAccessor bridgeStorageAccessor;
    private BridgeStorageProvider bridgeStorageProvider;
    private FederationStorageProviderImpl federationStorageProvider;
    private FederationSupport federationSupport;
    private BridgeSupport bridgeSupport;
    private FeePerKbSupport feePerKbSupport;
    private Repository repository;

    @BeforeEach
    void setUp() {
        repository = createRepository();
        bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, ALL_ACTIVATIONS);
        bridgeStorageAccessor = new InMemoryStorage();
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(bridgeStorageAccessor);
        FeePerKbConstants feePerKbConstants = BRIDGE_CONSTANTS.getFeePerKbConstants();
        feePerKbSupport = new FeePerKbSupportImpl(feePerKbConstants, feePerKbStorageProvider);
        setUpFeePerKb(FEE_PER_KB);
    }

    @Nested
    class P2shP2wshErpFederationTest {

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

            // Assert
            assertMigrationTransactionsBetweenP2shP2wshErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
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

            // Assert
            assertMigrationTransactionsBetweenP2shP2wshErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_duringMigration_withManyMinNonDustRetiringUtxos_whenMigrationBuildFails_shouldThrowIllegalStateException() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                () -> bridgeSupport.updateCollections(updateCollectionsTransaction));
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_withBalanceBelowThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(FUNDS_BELOW_MIGRATION_THRESHOLD)
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
        void updateCollections_duringMigration_withMoreUtxosThanMaxInputs_whenCalledRepeatedly_shouldCreateAMigrationTxEachTime() throws IOException {
            // Arrange
            int numberOfUtxos = ABOVE_MAX_INPUTS_PER_PEGOUT_TX - 1;
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

            // Assert
            assertMigrationTransactionsBetweenP2shP2wshErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX
            );
            assertRetiringFederationStillPresent();

            int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
            assertRetiringUtxosCount(remainingUtxos);

            // Act
            long secondExecutionBlockNumber = executionBlockNumber + 1;
            setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
            Transaction secondUpdateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
            bridgeSupport.updateCollections(secondUpdateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenP2shP2wshErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                TWO_MIGRATION_TXS_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withOneSpendableRetiringUtxo_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
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

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenP2shP2wshErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationCleared();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withManySpendableRetiringUtxos_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
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

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenP2shP2wshErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationCleared();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withMoreUtxosThanMaxInputs_shouldCreateLastMigrationTxWithMaxInputsAndClearRetiringFedEvenIfUtxosRemain() throws IOException {
            // Arrange
            int numberOfUtxos = ABOVE_MAX_INPUTS_PER_PEGOUT_TX - 1;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenP2shP2wshErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX
            );
            assertRetiringFederationCleared();

            int expectedRemainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
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

        private void assertMigrationTransactionsBetweenP2shP2wshErpFedsWereBuiltAsExpected(
            Federation retiringFederation,
            List<UTXO> retiringFederationUtxos,
            int expectedMigrationTxCount,
            int expectedTotalInputCount
        ) throws IOException {
            assertMigrationTxCount(expectedMigrationTxCount);

            List<BtcTransaction> migrationTransactions = getMigrationTransactionsSortedByCreationAndInputsCount();
            List<UTXO> migratedUtxos = new ArrayList<>();
            int remainingExpectedInputs = expectedTotalInputCount;
            for (BtcTransaction migrationTransaction : migrationTransactions) {
                assertBtcTxVersionIs2(migrationTransaction);

                int expectedInputCountInTx = getExpectedInputCountInTx(remainingExpectedInputs);
                List<UTXO> selectedUtxosInTx = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);
                assertReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederation.getRedeemScript(),
                    retiringFederationUtxos,
                    selectedUtxosInTx,
                    expectedInputCountInTx
                );
                migratedUtxos.addAll(selectedUtxosInTx);
                assertReleaseTxOutputs(migrationTransaction, selectedUtxosInTx);
                remainingExpectedInputs -= expectedInputCountInTx;
            }
            assertAllExpectedInputsWereIncluded(remainingExpectedInputs);
            assertExpectedUtxosWereMigrated(migratedUtxos, retiringFederationUtxos, expectedTotalInputCount);
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

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
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

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_duringMigration_withManyMinNonDustRetiringUtxos_whenMigrationBuildFails_shouldThrowIllegalStateException() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                () -> bridgeSupport.updateCollections(updateCollectionsTransaction));
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_withBalanceBelowThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(FUNDS_BELOW_MIGRATION_THRESHOLD)
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
        void updateCollections_duringMigration_withMoreUtxosThanMaxInputs_whenCalledRepeatedly_shouldCreateAMigrationTxEachTime() throws IOException {
            // Arrange
            int numberOfUtxos = ABOVE_MAX_INPUTS_PER_PEGOUT_TX - 1;
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

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX
            );
            assertRetiringFederationStillPresent();

            int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
            assertRetiringUtxosCount(remainingUtxos);

            // Act
            long secondExecutionBlockNumber = executionBlockNumber + 1;
            setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
            Transaction secondUpdateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
            bridgeSupport.updateCollections(secondUpdateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                TWO_MIGRATION_TXS_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withOneSpendableRetiringUtxo_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
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

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationCleared();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withManySpendableRetiringUtxos_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
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

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationCleared();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withMoreUtxosThanMaxInputs_shouldCreateLastMigrationTxWithMaxInputsAndClearRetiringFedEvenIfUtxosRemain() throws IOException {
            // Arrange
            int numberOfUtxos = ABOVE_MAX_INPUTS_PER_PEGOUT_TX - 1;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenP2shErpFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX
            );
            assertRetiringFederationCleared();

            int expectedRemainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
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
            int expectedTotalInputCount
        ) throws IOException {
            assertMigrationTxCount(expectedMigrationTxCount);

            List<BtcTransaction> migrationTransactions = getMigrationTransactionsSortedByCreationAndInputsCount();
            List<UTXO> migratedUtxos = new ArrayList<>();
            int remainingExpectedInputs = expectedTotalInputCount;
            for (BtcTransaction migrationTransaction : migrationTransactions) {
                assertBtcTxVersionIs2(migrationTransaction);

                int expectedInputCountInTx = getExpectedInputCountInTx(remainingExpectedInputs);
                List<UTXO> selectedUtxosInTx = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);
                assertReleaseTxInputsP2shErp(
                    migrationTransaction,
                    expectedInputCountInTx,
                    retiringFederation.getRedeemScript(),
                    retiringFederationUtxos,
                    selectedUtxosInTx
                );
                migratedUtxos.addAll(selectedUtxosInTx);
                assertReleaseTxOutputs(migrationTransaction, selectedUtxosInTx);
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

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
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

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_duringMigration_withManyMinNonDustRetiringUtxos_whenMigrationBuildFails_shouldThrowIllegalStateException() throws IOException {
            // Arrange
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                () -> bridgeSupport.updateCollections(updateCollectionsTransaction));
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(retiringUtxos.size());
        }

        @Test
        void updateCollections_duringMigration_withBalanceBelowThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(FUNDS_BELOW_MIGRATION_THRESHOLD)
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

            long executionBlockNumber = duringMigrationBlockNumberForIRIS();
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
        void updateCollections_duringMigration_withMoreUtxosThanMaxInputs_whenCalledRepeatedly_shouldCreateAMigrationTxEachTime() throws IOException {
            // Arrange
            int numberOfUtxos = ABOVE_MAX_INPUTS_PER_PEGOUT_TX - 1;
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

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX
            );
            assertRetiringFederationStillPresent();

            int remainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
            assertRetiringUtxosCount(remainingUtxos);

            // Act
            long secondExecutionBlockNumber = executionBlockNumber + 1;
            setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
            Transaction secondUpdateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
            bridgeSupport.updateCollections(secondUpdateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                TWO_MIGRATION_TXS_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationStillPresent();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withOneSpendableRetiringUtxo_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
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

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationCleared();
            assertNoRemainingRetiringUtxos();
        }

        @Test
        void updateCollections_pastMigrationAge_withManySpendableRetiringUtxos_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
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

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                retiringUtxos.size()
            );
            assertRetiringFederationCleared();
            assertNoRemainingRetiringUtxos();
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
        void updateCollections_pastMigrationAge_withMoreUtxosThanMaxInputs_shouldCreateLastMigrationTxWithMaxInputsAndClearRetiringFedEvenIfUtxosRemain() throws IOException {
            // Arrange
            int numberOfUtxos = ABOVE_MAX_INPUTS_PER_PEGOUT_TX - 1;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            retiringUtxos.add(flyoverUtxo);

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
                retiringFederation,
                retiringUtxos,
                ONE_MIGRATION_TX_COUNT,
                MAX_INPUTS_PER_PEGOUT_TX
            );
            assertRetiringFederationCleared();

            int expectedRemainingUtxos = retiringUtxos.size() - MAX_INPUTS_PER_PEGOUT_TX;
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

        private long duringMigrationBlockNumberForIRIS() {
            return blockNumberBeforeMigrationBegins(IRIS_ACTIVATIONS) + 1;
        }

        private void setUpBridgeAndFederationSupportForExecutionBlockForIRIS(long executionBlockNumber) {
            bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, IRIS_ACTIVATIONS);
            org.ethereum.core.Block executionBlock = new BlockGenerator().createBlock(executionBlockNumber, 1);

            federationSupport = FederationSupportBuilder.builder()
                .withFederationConstants(FEDERATION_CONSTANTS)
                .withFederationStorageProvider(federationStorageProvider)
                .withRskExecutionBlock(executionBlock)
                .withActivations(IRIS_ACTIVATIONS)
                .build();

            bridgeSupport = BridgeSupportBuilder.builder()
                .withBridgeConstants(BRIDGE_CONSTANTS)
                .withProvider(bridgeStorageProvider)
                .withExecutionBlock(executionBlock)
                .withActivations(IRIS_ACTIVATIONS)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .build();
        }

        private void assertMigrationTransactionsBetweenStandardMultisigFedsWereBuiltAsExpected(
            Federation retiringFederation,
            List<UTXO> retiringFederationUtxos,
            int expectedMigrationTxCount,
            int expectedTotalInputCount
        ) throws IOException {
            assertMigrationTxCount(expectedMigrationTxCount);

            List<BtcTransaction> migrationTransactions = getMigrationTransactionsSortedByCreationAndInputsCount();
            List<UTXO> migratedUtxos = new ArrayList<>();
            int remainingExpectedInputs = expectedTotalInputCount;
            for (BtcTransaction migrationTransaction : migrationTransactions) {
                assertBtcTxVersionIs2(migrationTransaction);

                int expectedInputCountInTx = getExpectedInputCountInTx(remainingExpectedInputs);
                List<UTXO> selectedUtxosInTx = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);
                assertReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    expectedInputCountInTx,
                    retiringFederation.getRedeemScript(),
                    retiringFederationUtxos,
                    selectedUtxosInTx
                );
                migratedUtxos.addAll(selectedUtxosInTx);
                assertReleaseTxOutputs(migrationTransaction, selectedUtxosInTx);
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

            BtcTransaction migrationTransaction = getMigrationTransactionForIRIS();
            assertBtcTxVersionIs1(migrationTransaction);
            List<UTXO> selectedUtxos = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);
            assertReleaseTxInputsStandardMultisig(
                migrationTransaction,
                expectedInputCount,
                retiringFederation.getRedeemScript(),
                retiringFederationUtxos,
                selectedUtxos
            );

            assertReleaseTxOutputs(migrationTransaction, selectedUtxos);
            assertExpectedUtxosWereMigrated(selectedUtxos, retiringFederationUtxos, expectedInputCount);
        }

        private void assertOneMigrationTxCountForIRIS() throws IOException {
            assertEquals(ONE_MIGRATION_TX_COUNT, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(IRIS_ACTIVATIONS).size());
        }

        private BtcTransaction getMigrationTransactionForIRIS() throws IOException {
            return bridgeStorageProvider.getPegoutsWaitingForConfirmations()
                .getEntries(IRIS_ACTIVATIONS)
                .iterator()
                .next()
                .getBtcTransaction();
        }
    }

    private static void assertAllExpectedInputsWereIncluded(int remainingExpectedInputs) {
        assertEquals(0, remainingExpectedInputs);
    }

    private static int getExpectedInputCountInTx(int remainingExpectedInputs) {
        return Math.min(MAX_INPUTS_PER_PEGOUT_TX, remainingExpectedInputs);
    }

    private List<BtcTransaction> getMigrationTransactionsSortedByCreationAndInputsCount() throws IOException {
        return bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries(ALL_ACTIVATIONS)
            .stream()
            .sorted(Comparator
                .comparing(PegoutsWaitingForConfirmations.Entry::getPegoutCreationRskBlockNumber)
                .thenComparing(
                    entry -> entry.getBtcTransaction().getInputs().size(),
                    Comparator.reverseOrder()
                )
            )
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .toList();
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

    private void assertReleaseTxOutputs(BtcTransaction migrationTransaction, List<UTXO> selectedUtxos) {
        Coin migrationValue = selectedUtxos.stream()
            .map(UTXO::getValue)
            .reduce(Coin.ZERO, Coin::add);
        assertMigrationTxWithOnlyMigrationOutputs(
            migrationTransaction,
            migrationValue,
            federationSupport.getActiveFederationAddress(),
            NETWORK_PARAMETERS
        );
    }

    private void setUpHighFeePerKb() {
        Coin highFeePerKb = Coin.COIN.multiply(2);
        setUpFeePerKb(highFeePerKb);
    }

    private void setUpBridgeAndFederationSupportForExecutionBlock(long executionBlockNumber) {
        org.ethereum.core.Block executionBlock = new BlockGenerator().createBlock(executionBlockNumber, 1);

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(BridgeSupportProcessFundsMigrationTest.ALL_ACTIVATIONS)
            .build();

        bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(bridgeStorageProvider)
            .withExecutionBlock(executionBlock)
            .withActivations(BridgeSupportProcessFundsMigrationTest.ALL_ACTIVATIONS)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
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
        return blockNumberBeforeMigrationBegins() + 1;
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

    private void assertMigrationTxCount(int expectedCount) throws IOException {
        assertEquals(expectedCount, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(ALL_ACTIVATIONS).size());
    }

    private void assertNoMigrationTxCreated() throws IOException {
        assertMigrationTxCount(0);
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
