package co.rsk.peg;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.UTXO;
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
import java.util.List;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
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
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, 1, Coin.COIN);
            long executionBlockNumber = migrationBeginBlockNumber(ALL_ACTIVATIONS);

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
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, 1, Coin.COIN);

            long executionBlockNumber = duringMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTxCount(1);
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_duringMigration_withMultipleSpendableRetiringUtxos_shouldCreateMigrationTx() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, 3, Coin.COIN);

            long executionBlockNumber = duringMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTxCount(1);
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_duringMigration_withManyMinNonDustRetiringUtxos_shouldCreateMigrationTx() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            Coin lowSpendableValue = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.add(FEE_PER_KB);
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, 5, lowSpendableValue);

            long executionBlockNumber = duringMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTxCount(1);
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_duringMigration_withBalanceBelowThreshold_shouldNotCreateMigrationTx() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, 1, FEE_PER_KB.divide(2));

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
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, maxInputs + 2, Coin.COIN);
            Transaction secondUpdateCollectionsTransaction = buildUpdateCollectionsTransaction(1);

            long executionBlockNumber = duringMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);
            bridgeSupport.updateCollections(secondUpdateCollectionsTransaction);

            // Assert
            assertMigrationTxCount(2);
            assertRetiringFederationStillPresent();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_pastMigrationAge_withOneSpendableRetiringUtxo_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, 1, Coin.COIN);

            long executionBlockNumber = pastMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTxCount(1);
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_pastMigrationAge_withManySpendableRetiringUtxos_shouldCreateMigrationTxAndClearRetiringFed() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, 3, Coin.COIN);

            long executionBlockNumber = pastMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTxCount(1);
            assertRetiringFederationCleared();
            assertRetiringUtxosCount(0);
        }

        @Test
        void updateCollections_pastMigrationAge_withMoreUtxosThanMaxInputs_shouldClearRetiringFedEvenIfUtxosRemain() throws IOException {
            // Arrange
            Federation retiringFederation = buildP2shP2wshFederation();
            int maxInputs = BRIDGE_CONSTANTS.getMaxInputsPerPegoutTransaction();
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, maxInputs + 2, Coin.COIN);

            long executionBlockNumber = pastMigrationBlockNumber(ALL_ACTIVATIONS);
            setUpBridgeAndFederationSupport(FEE_PER_KB, executionBlockNumber);
            setUpActiveAndRetiringFederations(activeFederation, retiringFederation, retiringUtxos);

            // Act
            bridgeSupport.updateCollections(updateCollectionsTransaction);

            // Assert
            assertMigrationTxCount(1);
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
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, 1, MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

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
            List<UTXO> retiringUtxos = buildUtxos(retiringFederation, 1, Coin.COIN);

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

    private List<UTXO> buildUtxos(Federation federation, int count, Coin value) {
        return UTXOBuilder.builder()
            .withValue(value)
            .withScriptPubKey(federation.getP2SHScript())
            .buildMany(count, i -> createHash(i + 1));
    }

    private long migrationBeginBlockNumber(ActivationConfig.ForBlock activations) {
        long activationAge = BRIDGE_CONSTANTS.getFederationConstants().getFederationActivationAge(activations);
        return ACTIVE_FEDERATION_CREATION_BLOCK +
            activationAge +
            BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationBegin();
    }

    private long duringMigrationBlockNumber(ActivationConfig.ForBlock activations) {
        return migrationBeginBlockNumber(activations) + 1;
    }

    private long pastMigrationBlockNumber(ActivationConfig.ForBlock activations) {
        long activationAge = BRIDGE_CONSTANTS.getFederationConstants().getFederationActivationAge(activations);
        return ACTIVE_FEDERATION_CREATION_BLOCK +
            activationAge +
            BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationEnd(activations);
    }

    private void assertNoMigrationTxCreated() throws IOException {
        assertMigrationTxCount(0);
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
