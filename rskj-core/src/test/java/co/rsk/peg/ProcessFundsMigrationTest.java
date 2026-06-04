package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.BridgeSupportTestUtil.buildUpdateCollectionsTransaction;
import static co.rsk.peg.ReleaseTransactionAssertions.assertBtcTxVersionIs2;
import static co.rsk.peg.ReleaseTransactionAssertions.assertMigrationTxWithOnlyMigrationOutputs;
import static co.rsk.peg.ReleaseTransactionAssertions.assertReleaseTxInputsP2shErp;
import static co.rsk.peg.ReleaseTransactionAssertions.assertReleaseTxInputsP2shP2wshErp;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.getBtcEcKeysFromSeeds;
import static co.rsk.peg.federation.FederationStorageIndexKey.OLD_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
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
import java.util.Comparator;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProcessFundsMigrationTest {

    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0L);
    private static final Coin FEE_PER_KB = Coin.valueOf(8_000L);
    private static final long ACTIVE_FEDERATION_CREATION_BLOCK = 100L;
    private static final int EXPECTED_MULTIPLE_MIGRATION_TX_COUNT = 2;
    private final Transaction updateCollectionsTransaction = buildUpdateCollectionsTransaction();

    private StorageAccessor bridgeStorageAccessor;
    private BridgeStorageProvider bridgeStorageProvider;
    private FederationStorageProviderImpl federationStorageProvider;
    private FederationSupport federationSupport;
    private BridgeSupport bridgeSupport;
    private FeePerKbSupport feePerKbSupport;

    @FunctionalInterface
    private interface MigrationTxInputsAssertion {
        void assertInputs(
            BtcTransaction migrationTransaction,
            Federation retiringFederation,
            List<UTXO> retiringFederationUtxos,
            List<UTXO> selectedUtxos,
            int expectedInputCount
        );
    }

    @Nested
    class P2shP2wshErpFederationTest {

        private final Federation retiringFederation = P2shP2wshErpFederationBuilder.builder().build();
        private final Federation activeFederation = P2shP2wshErpFederationBuilder.builder()
            .withCreationBlockNumber(ACTIVE_FEDERATION_CREATION_BLOCK)
            .withMembersBtcPublicKeys(getActiveMemberKeys(20))
            .build();
        private final MigrationTxInputsAssertion migrationTxInputsAssertion = (migrationTransaction, retiringFederation, retiringFederationUtxos,
            selectedUtxos, expectedInputCount) -> assertReleaseTxInputsP2shP2wshErp(
                migrationTransaction,
                retiringFederation.getRedeemScript(),
                retiringFederationUtxos,
                selectedUtxos,
                expectedInputCount
            );

        @Test
        void updateCollections_withNoRetiringFederation_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            setUpBridgeAndFederationSupport(FEE_PER_KB, ACTIVE_FEDERATION_CREATION_BLOCK + 1);
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreatedAndRetiringFederationIsStillPresent(retiringUtxos);
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpectedAndRetiringFederationIsStillPresent(
                retiringFederation,
                retiringUtxos,
                migrationTxInputsAssertion
            );
        }

        @Test
        void updateCollections_duringMigration_withMultipleSpendableRetiringUtxos_shouldCreateMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 3;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpectedAndRetiringFederationIsStillPresent(
                retiringFederation,
                retiringUtxos,
                migrationTxInputsAssertion
            );
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                () -> bridgeSupport.updateCollections(updateCollectionsTransaction));
            assertNoMigrationTxCreatedAndRetiringFederationIsStillPresent(retiringUtxos);
        }

        @Test
        void updateCollections_duringMigration_withBalanceBelowThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(FEE_PER_KB.divide(2))
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreatedAndRetiringFederationIsStillPresent(retiringUtxos);
        }

        @Test
        void updateCollections_duringMigration_withMoreUtxosThanMaxInputs_whenCalledRepeatedly_shouldCreateAMigrationTxEachTime() throws IOException {
            // Arrange
            int maxInputs = BRIDGE_CONSTANTS.getMaxInputsPerPegoutTransaction();
            int numberOfUtxos = maxInputs + 1;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpected(retiringFederation, retiringUtxos, maxInputs, migrationTxInputsAssertion);
            assertRetiringFederationStillPresent();

            int remainingUtxos = retiringUtxos.size() - maxInputs;
            assertRetiringUtxosCount(remainingUtxos);

            // Act
            long secondExecutionBlockNumber = executionBlockNumber + 1;
            setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
            Transaction secondUpdateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
            bridgeSupport.updateCollections(secondUpdateCollectionsTransaction);

            // Assert
            assertMultipleMigrationTransactionsWereBuiltAsExpected(retiringFederation, retiringUtxos, migrationTxInputsAssertion);
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpectedAndRetiringFederationIsCleared(
                retiringFederation,
                retiringUtxos,
                migrationTxInputsAssertion
            );
        }

        @Test
        void updateCollections_pastMigrationAge_withManySpendableRetiringUtxos_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
            // Arrange
            int numberOfUtxos = 3;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpectedAndRetiringFederationIsCleared(
                retiringFederation,
                retiringUtxos,
                migrationTxInputsAssertion
            );
        }

        @Test
        void updateCollections_pastMigrationAge_withMoreUtxosThanMaxInputs_shouldCreateLastMigrationTxWithMaxInputsAndClearRetiringFedEvenIfUtxosRemain() throws IOException {
            // Arrange
            int maxInputs = BRIDGE_CONSTANTS.getMaxInputsPerPegoutTransaction();
            int numberOfUtxos = maxInputs + 1;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpected(retiringFederation, retiringUtxos, maxInputs, migrationTxInputsAssertion);
            assertRetiringFederationCleared();

            int expectedRemainingUtxos = retiringUtxos.size() - maxInputs;
            assertRetiringUtxosCount(expectedRemainingUtxos);
        }

        @Test
        void updateCollections_pastMigrationAge_withZeroBalance_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreatedAndRetiringFederationIsCleared(retiringUtxos);
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreatedAndRetiringFederationIsCleared(retiringUtxos);
        }

        @Test
        void updateCollections_pastMigrationAge_withHighFees_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            Coin highFeePerKb = Coin.COIN.multiply(2);
            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupport(highFeePerKb, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreatedAndRetiringFederationIsCleared(retiringUtxos);
        }
    }

    @Nested
    class P2shErpFederationTest {

        private final Federation retiringFederation = P2shErpFederationBuilder.builder().build();
        private final Federation activeFederation = P2shErpFederationBuilder.builder()
            .withCreationBlockNumber(ACTIVE_FEDERATION_CREATION_BLOCK)
            .withMembersBtcPublicKeys(getActiveMemberKeys(9))
            .build();
        private final MigrationTxInputsAssertion migrationTxInputsAssertion = (migrationTransaction, retiringFederation, retiringFederationUtxos,
            selectedUtxos, expectedInputCount) -> assertReleaseTxInputsP2shErp(
                migrationTransaction,
                expectedInputCount,
                retiringFederation.getRedeemScript(),
                retiringFederationUtxos,
                selectedUtxos
            );

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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreatedAndRetiringFederationIsStillPresent(retiringUtxos);
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpectedAndRetiringFederationIsStillPresent(
                retiringFederation,
                retiringUtxos,
                migrationTxInputsAssertion
            );
        }

        @Test
        void updateCollections_duringMigration_withMultipleSpendableRetiringUtxos_shouldCreateMigrationTx() throws IOException {
            // Arrange
            int numberOfUtxos = 3;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpectedAndRetiringFederationIsStillPresent(
                retiringFederation,
                retiringUtxos,
                migrationTxInputsAssertion
            );
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                () -> bridgeSupport.updateCollections(updateCollectionsTransaction));
            assertNoMigrationTxCreatedAndRetiringFederationIsStillPresent(retiringUtxos);
        }

        @Test
        void updateCollections_duringMigration_withBalanceBelowThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(FEE_PER_KB.divide(2))
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreatedAndRetiringFederationIsStillPresent(retiringUtxos);
        }

        @Test
        void updateCollections_duringMigration_withMoreUtxosThanMaxInputs_whenCalledRepeatedly_shouldCreateAMigrationTxEachTime() throws IOException {
            // Arrange
            int maxInputs = BRIDGE_CONSTANTS.getMaxInputsPerPegoutTransaction();
            int numberOfUtxos = maxInputs + 1;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpected(retiringFederation, retiringUtxos, maxInputs, migrationTxInputsAssertion);
            assertRetiringFederationStillPresent();

            int remainingUtxos = retiringUtxos.size() - maxInputs;
            assertRetiringUtxosCount(remainingUtxos);

            // Act
            long secondExecutionBlockNumber = executionBlockNumber + 1;
            setUpBridgeAndFederationSupportForExecutionBlock(secondExecutionBlockNumber);
            Transaction secondUpdateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
            bridgeSupport.updateCollections(secondUpdateCollectionsTransaction);

            // Assert
            assertMultipleMigrationTransactionsWereBuiltAsExpected(retiringFederation, retiringUtxos, migrationTxInputsAssertion);
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpectedAndRetiringFederationIsCleared(
                retiringFederation,
                retiringUtxos,
                migrationTxInputsAssertion
            );
        }

        @Test
        void updateCollections_pastMigrationAge_withManySpendableRetiringUtxos_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
            // Arrange
            int numberOfUtxos = 3;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpectedAndRetiringFederationIsCleared(
                retiringFederation,
                retiringUtxos,
                migrationTxInputsAssertion
            );
        }

        @Test
        void updateCollections_pastMigrationAge_withMoreUtxosThanMaxInputs_shouldCreateLastMigrationTxWithMaxInputsAndClearRetiringFedEvenIfUtxosRemain() throws IOException {
            // Arrange
            int maxInputs = BRIDGE_CONSTANTS.getMaxInputsPerPegoutTransaction();
            int numberOfUtxos = maxInputs + 1;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpected(retiringFederation, retiringUtxos, maxInputs, migrationTxInputsAssertion);
            assertRetiringFederationCleared();

            int expectedRemainingUtxos = retiringUtxos.size() - maxInputs;
            assertRetiringUtxosCount(expectedRemainingUtxos);
        }

        @Test
        void updateCollections_pastMigrationAge_withZeroBalance_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreatedAndRetiringFederationIsCleared(retiringUtxos);
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
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreatedAndRetiringFederationIsCleared(retiringUtxos);
        }

        @Test
        void updateCollections_pastMigrationAge_withHighFees_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            Coin highFeePerKb = Coin.COIN.multiply(2);
            long executionBlockNumber = pastMigrationBlockNumber();
            setUpBridgeAndFederationSupport(highFeePerKb, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreatedAndRetiringFederationIsCleared(retiringUtxos);
        }
    }

    private void setUpBridgeAndFederationSupport(
        Coin feePerKb,
        long executionBlockNumber
    ) {
        Repository repository = createRepository();
        bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, ALL_ACTIVATIONS);
        bridgeStorageAccessor = new InMemoryStorage();
        setUpFeePerKb(feePerKb);
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        setUpBridgeAndFederationSupportForExecutionBlock(executionBlockNumber);
    }

    private void setUpBridgeAndFederationSupportForExecutionBlock(long executionBlockNumber) {
        org.ethereum.core.Block executionBlock = new BlockGenerator().createBlock(executionBlockNumber, 1);

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(BRIDGE_CONSTANTS.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(ALL_ACTIVATIONS)
            .build();

        bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(bridgeStorageProvider)
            .withExecutionBlock(executionBlock)
            .withActivations(ALL_ACTIVATIONS)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();
    }

    private void setUpFeePerKb(Coin feePerKb) {
        bridgeStorageAccessor.saveToRepository(FeePerKbStorageIndexKey.FEE_PER_KB.getKey(), feePerKb, BridgeSerializationUtils::serializeCoin);
        FeePerKbConstants feePerKbConstants = BRIDGE_CONSTANTS.getFeePerKbConstants();
        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(bridgeStorageAccessor);
        feePerKbSupport = new FeePerKbSupportImpl(feePerKbConstants, feePerKbStorageProvider);
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
        long activationAge = BRIDGE_CONSTANTS.getFederationConstants().getFederationActivationAge(ALL_ACTIVATIONS);
        return ACTIVE_FEDERATION_CREATION_BLOCK +
            activationAge +
            BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationBegin();
    }

    private long duringMigrationBlockNumber() {
        return blockNumberBeforeMigrationBegins() + 1;
    }

    private long pastMigrationBlockNumber() {
        long migrationPeriodDuration = BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationEnd(ALL_ACTIVATIONS) -
            BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationBegin();
        return blockNumberBeforeMigrationBegins() + migrationPeriodDuration;
    }

    private void assertNoMigrationTxCreated() throws IOException {
        assertMigrationTxCount(0);
    }

    private void assertNoMigrationTxCreatedAndRetiringFederationIsStillPresent(List<UTXO> retiringUtxos) throws IOException {
        assertNoMigrationTxCreated();
        assertRetiringFederationStillPresent();
        assertRetiringUtxosCount(retiringUtxos.size());
    }

    private void assertNoMigrationTxCreatedAndRetiringFederationIsCleared(List<UTXO> retiringUtxos) throws IOException {
        assertNoMigrationTxCreated();
        assertRetiringFederationCleared();
        assertRetiringUtxosCount(retiringUtxos.size());
    }

    private void assertOneMigrationTransactionWasBuiltAsExpected(
        Federation retiringFederation,
        List<UTXO> retiringFederationUtxos,
        int expectedInputCount,
        MigrationTxInputsAssertion migrationTxInputsAssertion
    ) throws IOException {
        assertMigrationTxCount(1);
        BtcTransaction migrationTransaction = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries()
            .iterator()
            .next()
            .getBtcTransaction();

        assertMigrationTransactionWasBuiltAsExpected(
            migrationTransaction,
            retiringFederation,
            retiringFederationUtxos,
            expectedInputCount,
            migrationTxInputsAssertion
        );
    }

    private void assertOneMigrationTransactionWasBuiltAsExpectedAndRetiringFederationIsCleared(
        Federation retiringFederation,
        List<UTXO> retiringFederationUtxos,
        MigrationTxInputsAssertion migrationTxInputsAssertion
    ) throws IOException {
        assertOneMigrationTransactionWasBuiltAsExpected(
            retiringFederation,
            retiringFederationUtxos,
            retiringFederationUtxos.size(),
            migrationTxInputsAssertion
        );
        assertRetiringFederationCleared();
        assertNoRemainingRetiringUtxos();
    }

    private void assertOneMigrationTransactionWasBuiltAsExpectedAndRetiringFederationIsStillPresent(
        Federation retiringFederation,
        List<UTXO> retiringFederationUtxos,
        MigrationTxInputsAssertion migrationTxInputsAssertion
    ) throws IOException {
        assertOneMigrationTransactionWasBuiltAsExpected(
            retiringFederation,
            retiringFederationUtxos,
            retiringFederationUtxos.size(),
            migrationTxInputsAssertion
        );
        assertRetiringFederationStillPresent();
        assertNoRemainingRetiringUtxos();
    }

    private void assertMultipleMigrationTransactionsWereBuiltAsExpected(
        Federation retiringFederation,
        List<UTXO> retiringFederationUtxos,
        MigrationTxInputsAssertion migrationTxInputsAssertion
    ) throws IOException {
        List<PegoutsWaitingForConfirmations.Entry> migrationTransactionEntries = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries()
            .stream()
            .sorted(Comparator.comparing(PegoutsWaitingForConfirmations.Entry::getPegoutCreationRskBlockNumber))
            .toList();

        List<BtcTransaction> migrationTransactions = migrationTransactionEntries.stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .toList();
        assertEquals(EXPECTED_MULTIPLE_MIGRATION_TX_COUNT, migrationTransactions.size());

        List<UTXO> selectedUtxos = migrationTransactions.stream()
            .flatMap(tx -> getSelectedUtxos(tx, retiringFederationUtxos).stream())
            .toList();
        assertEquals(retiringFederationUtxos.size(), selectedUtxos.size());
        assertTrue(selectedUtxos.containsAll(retiringFederationUtxos));

        int maxInputCount = BRIDGE_CONSTANTS.getMaxInputsPerPegoutTransaction();
        int remainingRetiringFederationUtxos = retiringFederationUtxos.size();
        for (BtcTransaction migrationTransaction : migrationTransactions) {
            int expectedInputCount = Math.min(maxInputCount, remainingRetiringFederationUtxos);
            assertMigrationTransactionWasBuiltAsExpected(
                migrationTransaction,
                retiringFederation,
                retiringFederationUtxos,
                expectedInputCount,
                migrationTxInputsAssertion
            );
            remainingRetiringFederationUtxos -= expectedInputCount;
        }
    }

    private void assertMigrationTransactionWasBuiltAsExpected(
        BtcTransaction migrationTransaction,
        Federation retiringFederation,
        List<UTXO> retiringFederationUtxos,
        int expectedInputCount,
        MigrationTxInputsAssertion migrationTxInputsAssertion
    ) {
        List<UTXO> selectedUtxos = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);

        assertBtcTxVersionIs2(migrationTransaction);
        migrationTxInputsAssertion.assertInputs(
            migrationTransaction,
            retiringFederation,
            retiringFederationUtxos,
            selectedUtxos,
            expectedInputCount
        );

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
        assertEquals(expectedCount, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().size());
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
