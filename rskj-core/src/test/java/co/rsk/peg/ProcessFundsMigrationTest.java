package co.rsk.peg;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationSupport;
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
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertP2shP2wshWitnessWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.*;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static co.rsk.peg.bitcoin.BitcoinUtils.BTC_TX_VERSION_2;
import static co.rsk.peg.federation.FederationStorageIndexKey.OLD_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFundsMigrationTest {

    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0L);
    private static final Coin FEE_PER_KB = Coin.valueOf(8_000L);
    private static final long ACTIVE_FEDERATION_CREATION_BLOCK = 100L;

    private StorageAccessor bridgeStorageAccessor;
    private BridgeStorageProvider bridgeStorageProvider;
    private FederationStorageProviderImpl federationStorageProvider;
    private FederationSupport federationSupport;
    private BridgeSupport bridgeSupport;
    private FeePerKbSupport feePerKbSupport;

    @Nested
    class P2shP2wshErpFederationTest {

        private final Federation activeFederation = buildP2shP2wshFederation();
        private final Transaction updateCollectionsTransaction = buildUpdateCollectionsTransaction();

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
            Federation retiringFederation = buildP2shP2wshFederation();
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );
            long executionBlockNumber = blockNumberBeforeMigrationBegins(ALL_ACTIVATIONS);

            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(1);
        }

        @Test
        void updateCollections_duringMigration_withOneSpendableRetiringUtxo_shouldCreateMigrationTx() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = duringMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpected(retiringFederation, retiringUtxos, retiringUtxos.size());
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_duringMigration_withMultipleSpendableRetiringUtxos_shouldCreateMigrationTx() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            int numberOfUtxos = 3;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpected(retiringFederation, retiringUtxos, retiringUtxos.size());
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_duringMigration_withManyMinNonDustRetiringUtxos_shouldCreateMigrationTx() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            Coin lowSpendableValue = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.add(FEE_PER_KB);
            int numberOfUtxos = 5;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(lowSpendableValue)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = duringMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpected(retiringFederation, retiringUtxos, retiringUtxos.size());
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_duringMigration_withBalanceBelowThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(FEE_PER_KB.divide(2))
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = duringMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(1);
        }

        @Test
        void updateCollections_duringMigration_withMoreUtxosThanMaxInputs_whenCalledRepeatedly_shouldCreateAMigrationTxEachTime() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            int maxInputs = BRIDGE_CONSTANTS.getMaxInputsPerPegoutTransaction();
            int numberOfUtxos = maxInputs + 2;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            Transaction secondUpdateCollectionsTransaction = buildUpdateCollectionsTransaction(1);

            long executionBlockNumber = duringMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeSupport.updateCollections(secondUpdateCollectionsTransaction);

            // Assert
            assertMultipleMigrationTransactionsWereBuiltAsExpected(retiringFederation, retiringUtxos, 2);
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_pastMigrationAge_withOneSpendableRetiringUtxo_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = pastMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpected(retiringFederation, retiringUtxos, retiringUtxos.size());
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_pastMigrationAge_withManySpendableRetiringUtxos_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            int numberOfUtxos = 3;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpected(retiringFederation, retiringUtxos, retiringUtxos.size());
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_pastMigrationAge_withMoreUtxosThanMaxInputs_shouldClearRetiringFedEvenIfUtxosRemain() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            int maxInputs = BRIDGE_CONSTANTS.getMaxInputsPerPegoutTransaction();
            int numberOfUtxos = maxInputs + 2;
            List<UTXO> retiringUtxos = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            long executionBlockNumber = pastMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertOneMigrationTransactionWasBuiltAsExpected(retiringFederation, retiringUtxos, maxInputs);
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(2);
        }

        @Test
        void updateCollections_pastMigrationAge_withZeroBalance_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();

            long executionBlockNumber = pastMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, List.of());

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_pastMigrationAge_withMinNonDustRetiringUtxo_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = pastMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(1);
        }

        @Test
        void updateCollections_pastMigrationAge_withHighFees_shouldClearRetiringFedWithoutMigrationTx() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            Coin highFeePerKb = Coin.COIN.multiply(2).subtract(Coin.SATOSHI);
            List<UTXO> retiringUtxos = List.of(
                UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(retiringFederation.getP2SHScript())
                .build()
            );

            long executionBlockNumber = pastMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(highFeePerKb, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertNoMigrationTxCreated();
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(1);
        }
    }

    private void setUpBridgeAndFederationSupport(
        Coin feePerKb,
        long executionBlockNumber
    ) {
        Repository repository = createRepository();
        bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, ProcessFundsMigrationTest.ALL_ACTIVATIONS);
        bridgeStorageAccessor = new InMemoryStorage();
        setUpFeePerKb(feePerKb);
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        org.ethereum.core.Block executionBlock = new BlockGenerator().createBlock(executionBlockNumber, 1);

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(BRIDGE_CONSTANTS.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(ProcessFundsMigrationTest.ALL_ACTIVATIONS)
            .build();

        bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(bridgeStorageProvider)
            .withExecutionBlock(executionBlock)
            .withActivations(ProcessFundsMigrationTest.ALL_ACTIVATIONS)
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

    private Federation buildP2shP2wshFederation() {
        return P2shP2wshErpFederationBuilder.builder()
            .withNetworkParameters(NETWORK_PARAMETERS)
            .withCreationBlockNumber(ACTIVE_FEDERATION_CREATION_BLOCK)
            .build();
    }

    private long blockNumberBeforeMigrationBegins(ActivationConfig.ForBlock activations) {
        long activationAge = BRIDGE_CONSTANTS.getFederationConstants().getFederationActivationAge(activations);
        return ACTIVE_FEDERATION_CREATION_BLOCK +
            activationAge +
            BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationBegin();
    }

    private long duringMigrationBlockNumber(ActivationConfig.ForBlock activations) {
        return blockNumberBeforeMigrationBegins(activations) + 1;
    }

    private long pastMigrationBlockNumber(ActivationConfig.ForBlock activations) {
        long migrationPeriodDuration = BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationEnd(activations) -
            BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationBegin();
        return blockNumberBeforeMigrationBegins(activations) + migrationPeriodDuration;
    }

    private void assertNoMigrationTxCreated() throws IOException {
        assertMigrationTxCount(0);
    }

    private void assertOneMigrationTransactionWasBuiltAsExpected(
        Federation retiringFederation,
        List<UTXO> retiringFederationUtxos,
        int expectedInputCount
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
            expectedInputCount
        );
    }

    private void assertMultipleMigrationTransactionsWereBuiltAsExpected(
        Federation retiringFederation,
        List<UTXO> retiringFederationUtxos,
        int expectedTransactionCount
    ) throws IOException {
        List<BtcTransaction> migrationTransactions = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .sorted(Comparator.comparingInt(tx -> tx.getInputs().size()))
            .toList();
        assertEquals(expectedTransactionCount, migrationTransactions.size());

        List<UTXO> selectedUtxos = migrationTransactions.stream()
            .flatMap(tx -> getSelectedUtxos(tx, retiringFederationUtxos).stream())
            .toList();
        assertEquals(retiringFederationUtxos.size(), selectedUtxos.size());
        assertTrue(selectedUtxos.containsAll(retiringFederationUtxos));

        for (BtcTransaction migrationTransaction : migrationTransactions) {
            assertMigrationTransactionWasBuiltAsExpected(
                migrationTransaction,
                retiringFederation,
                retiringFederationUtxos,
                migrationTransaction.getInputs().size()
            );
        }
    }

    private void assertMigrationTransactionWasBuiltAsExpected(
        BtcTransaction migrationTransaction,
        Federation retiringFederation,
        List<UTXO> retiringFederationUtxos,
        int expectedInputCount
    ) {
        List<UTXO> selectedUtxos = getSelectedUtxos(migrationTransaction, retiringFederationUtxos);

        assertBtcTxVersionIs2(migrationTransaction);
        assertMigrationReleaseTxInputsP2shP2wshErp(
            migrationTransaction,
            retiringFederation.getRedeemScript(),
            retiringFederationUtxos,
            selectedUtxos,
            expectedInputCount
        );

        Coin migrationValue = selectedUtxos.stream()
            .map(UTXO::getValue)
            .reduce(Coin.ZERO, Coin::add);
        assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
    }

    private void assertBtcTxVersionIs2(BtcTransaction migrationTransaction) {
        assertEquals(BTC_TX_VERSION_2, migrationTransaction.getVersion());
    }

    private void assertMigrationReleaseTxInputsP2shP2wshErp(
        BtcTransaction migrationTransaction,
        Script retiringFederationRedeemScript,
        List<UTXO> retiringFederationUtxos,
        List<UTXO> selectedUtxos,
        int expectedInputCount
    ) {
        List<TransactionInput> inputs = migrationTransaction.getInputs();
        assertEquals(expectedInputCount, inputs.size());
        assertEquals(expectedInputCount, selectedUtxos.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
            migrationTransaction,
            retiringFederationRedeemScript,
            retiringFederationUtxos
        );
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    private void assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
        BtcTransaction migrationTransaction,
        Script retiringFederationRedeemScript,
        List<UTXO> retiringFederationUtxos
    ) {
        List<TransactionInput> inputs = migrationTransaction.getInputs();
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            TransactionWitness witness = migrationTransaction.getWitness(inputIndex);
            assertP2shP2wshWitnessWithoutSignaturesHasProperFormat(witness, retiringFederationRedeemScript);
            assertInputIsFromFederationUtxosWallet(inputs.get(inputIndex), retiringFederationUtxos);
        }
    }

    private void assertInputIsFromFederationUtxosWallet(TransactionInput input, List<UTXO> federationUtxos) {
        long matchingUtxos = federationUtxos.stream()
            .filter(utxo -> utxo.getHash().equals(input.getOutpoint().getHash()) &&
                utxo.getIndex() == input.getOutpoint().getIndex())
            .count();
        assertEquals(1, matchingUtxos);
    }

    private void assertSelectedUtxosBelongToTheInputs(List<UTXO> selectedUtxos, List<TransactionInput> inputs) {
        for (UTXO selectedUtxo : selectedUtxos) {
            long matchingInputs = inputs.stream()
                .filter(input -> input.getOutpoint().getHash().equals(selectedUtxo.getHash()) &&
                    input.getOutpoint().getIndex() == selectedUtxo.getIndex())
                .count();
            assertEquals(1, matchingInputs);
        }
    }

    private void assertMigrationTxWithOnlyMigrationOutputs(
        BtcTransaction migrationTransaction,
        Coin migrationValue
    ) {
        int expectedNumberOfOutputs = 1;
        List<TransactionOutput> migrationTransactionOutputs = migrationTransaction.getOutputs();
        assertEquals(expectedNumberOfOutputs, migrationTransactionOutputs.size());
        assertDestinationAddress(migrationTransactionOutputs, federationSupport.getActiveFederationAddress());
        assertOutputsWithNoChange(migrationTransaction, migrationValue);
    }

    private void assertDestinationAddress(List<TransactionOutput> outputs, Address expectedDestinationAddress) {
        for (TransactionOutput output : outputs) {
            Address destinationAddress = output.getScriptPubKey().getToAddress(NETWORK_PARAMETERS);
            assertEquals(expectedDestinationAddress, destinationAddress);
        }
    }

    private void assertOutputsWithNoChange(BtcTransaction migrationTransaction, Coin expectedSentAmount) {
        Coin outputsAmount = migrationTransaction.getOutputSum();
        Coin fees = migrationTransaction.getFee();
        Coin totalAmountSent = fees.add(outputsAmount);
        assertEquals(expectedSentAmount, totalAmountSent);
        assertEquals(migrationTransaction.getInputSum(), totalAmountSent);
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

    private Transaction buildUpdateCollectionsTransaction() {
        return buildUpdateCollectionsTransaction(0);
    }

    private Transaction buildUpdateCollectionsTransaction(long nonce) {
        Transaction tx = Transaction
            .builder()
            .nonce(BigInteger.valueOf(nonce))
            .destination(PrecompiledContracts.BRIDGE_ADDR)
            .data(Bridge.UPDATE_COLLECTIONS.encode())
            .chainId(Constants.MAINNET_CHAIN_ID)
            .build();

        tx.sign(RskTestUtils.getEcKeyFromSeed("sender").getPrivKeyBytes());
        return tx;
    }
}
