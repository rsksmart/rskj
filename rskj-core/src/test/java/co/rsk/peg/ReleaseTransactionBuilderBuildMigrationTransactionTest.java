package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.SUCCESS;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.*;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.ReleaseTransactionBuilder.BuildResult;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import java.util.Collections;
import java.util.List;

import co.rsk.test.builders.UTXOBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReleaseTransactionBuilderBuildMigrationTransactionTest {

    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all()
        .forBlock(0);
    private static final ActivationConfig.ForBlock FINGERROOT_ACTIVATIONS = ActivationConfigsForTest.fingerroot500()
        .forBlock(0);

    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();

    private static final Coin MINIMUM_PEGIN_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(
        ALL_ACTIVATIONS);

    private static final Coin HIGH_FEE_PER_KB = Coin.valueOf(1_000_000);
    private static final Coin DUSTY_AMOUNT_SEND_REQUESTED = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.minus(Coin.SATOSHI);

    protected Federation retiringFederation;
    protected int retiringFederationFormatVersion;
    protected Address retiringFederationAddress;
    protected List<UTXO> retiringFederationUTXOs;
    protected Script retiringFederationOutputScript;
    private Script retiringFederationRedeemScript;
    protected Wallet wallet;

    private ActivationConfig.ForBlock activationConfig;
    private Coin transactionFeePerKb;
    private Address newFederationAddress;

    @BeforeEach
    void setUp() {
        setUpActivationConfig(ALL_ACTIVATIONS);
        setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
    }

    @Nested
    class StandardMultiSigFederationTests {

        @BeforeEach
        void setUp() {
            retiringFederation = StandardMultiSigFederationBuilder.builder().build();
            retiringFederationFormatVersion = retiringFederation.getFormatVersion();
            retiringFederationAddress = retiringFederation.getAddress();
            retiringFederationOutputScript = retiringFederation.getP2SHScript();
            retiringFederationRedeemScript = retiringFederation.getRedeemScript();

            Federation newFederation = P2shErpFederationBuilder.builder().build();
            newFederationAddress = newFederation.getAddress();
        }

        @Test
        void buildMigrationTransaction_whenNoUTXOsToMigrate_shouldReturnDustySendRequested() {
            // Arrange
            retiringFederationUTXOs = Collections.emptyList();
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenRSKIP376IsNotActive_shouldCreateMigrationTxWithBtcVersion1() {
            // Arrange
            setUpActivationConfig(FINGERROOT_ACTIVATIONS);
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs1(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @Test
        void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
            // Arrange
            retiringFederationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @Test
        void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx() {
            // Arrange
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; the minimum UTXO the Federation
         * may hold is {@link co.rsk.peg.bitcoin.BitcoinTestUtils#MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT}
         * but we use it to exercise the DUSTY_SEND_REQUESTED path.
         */
        @Test
        void buildMigrationTransaction_whenMigrationValueIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            retiringFederationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(DUSTY_AMOUNT_SEND_REQUESTED)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreEnoughUtxosToPay_shouldCreateMigrationTx() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreNotEnoughUtxosToPay_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            retiringFederationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        /**
         * Tests an unrealistic scenario where the federation's balance differs from the value being migrated. Although
         * unreal, the method {@link ReleaseTransactionBuilder#buildMigrationTransaction(Coin, Address)} receives the
         * value to migrate as a parameter, and permits it to be less than the federation's balance. In reality, there's
         * no partial migration. Instead, all the UTXOs available for migration are migrated.
         */
        @Test
        void buildMigrationTransaction_whenFederationBalanceDiffersWithValueMigrated_shouldCreateMigrationTxWithTwoOutputs() {
            // Arrange
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = MINIMUM_PEGIN_TX_VALUE.subtract(Coin.valueOf(1000L)).multiply(numberOfUtxos);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithTwoMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 10})
        void buildMigrationTransaction_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards(int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize() {
            // Arrange
            int numberOfUtxos = 277;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenTxIsAlmostExceedingMaxTxSize_shouldCreateMigrationTx() {
            // Arrange
            int numberOfUtxos = 276;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }
    }

    @Nested
    class P2shErpFederationTests {

        @BeforeEach
        void setUp() {
            retiringFederation = P2shErpFederationBuilder.builder().build();
            retiringFederationFormatVersion = retiringFederation.getFormatVersion();
            retiringFederationAddress = retiringFederation.getAddress();
            retiringFederationOutputScript = retiringFederation.getP2SHScript();
            retiringFederationRedeemScript = retiringFederation.getRedeemScript();
            Federation newFederation = P2shP2wshErpFederationBuilder.builder().build();
            newFederationAddress = newFederation.getAddress();
        }

        @Test
        void buildMigrationTransaction_whenNoUTXOsToMigrate_shouldReturnDustySendRequested() {
            // Arrange
            retiringFederationUTXOs = Collections.emptyList();
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenRSKIP376IsNotActive_shouldCreateMigrationTxWithBtcVersion1() {
            // Arrange
            setUpActivationConfig(FINGERROOT_ACTIVATIONS);
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs1(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @Test
        void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
            // Arrange
            retiringFederationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @Test
        void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx() {
            // Arrange
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; the minimum UTXO the Federation
         * may hold is {@link co.rsk.peg.bitcoin.BitcoinTestUtils#MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT}
         * but we use it to exercise the DUSTY_SEND_REQUESTED path.
         */
        @Test
        void buildMigrationTransaction_whenMigrationValueIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            retiringFederationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(DUSTY_AMOUNT_SEND_REQUESTED)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreEnoughUtxosToPay_shouldCreateMigrationTx() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreNotEnoughUtxosToPay_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            retiringFederationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        /**
         * Tests an unrealistic scenario where the federation's balance differs from the value being migrated. Although
         * unreal, the method {@link ReleaseTransactionBuilder#buildMigrationTransaction(Coin, Address)} receives the
         * value to migrate as a parameter, and permits it to be less than the federation's balance. In reality, there's
         * no partial migration. Instead, all the UTXOs available for migration are migrated.
         */
        @Test
        void buildMigrationTransaction_whenFederationBalanceDiffersWithValueMigrated_shouldCreateMigrationTxWithTwoOutputs() {
            // Arrange
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = MINIMUM_PEGIN_TX_VALUE.subtract(Coin.valueOf(1000L)).multiply(numberOfUtxos);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithTwoMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 10})
        void buildMigrationTransaction_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards(int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize() {
            // Arrange
            int numberOfUtxos = 196;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenTxIsAlmostExceedingMaxTxSize_shouldCreateMigrationTx() {
            // Arrange
            int numberOfUtxos = 195;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }
    }

    @Nested
    class P2shP2wshErpFederationTests {

        @BeforeEach
        void setUp() {
            retiringFederation = P2shP2wshErpFederationBuilder.builder().build();
            retiringFederationFormatVersion = retiringFederation.getFormatVersion();
            retiringFederationAddress = retiringFederation.getAddress();
            retiringFederationOutputScript = retiringFederation.getP2SHScript();
            retiringFederationRedeemScript = retiringFederation.getRedeemScript();
            List<BtcECKey> newFederationMembersKeys = getBtcEcKeys(20);
            Federation newFederation = P2shP2wshErpFederationBuilder.builder().withMembersBtcPublicKeys(newFederationMembersKeys).build();
            newFederationAddress =  newFederation.getAddress();
        }

        @Test
        void buildMigrationTransaction_whenNoUTXOsToMigrate_shouldReturnDustySendRequested() {
            // Arrange
            retiringFederationUTXOs = Collections.emptyList();
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
            // Arrange
            retiringFederationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @Test
        void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx() {
            // Arrange
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; the minimum UTXO the Federation
         * may hold is {@link co.rsk.peg.bitcoin.BitcoinTestUtils#MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT}
         * but we use it to exercise the DUSTY_SEND_REQUESTED path.
         */
        @Test
        void buildMigrationTransaction_whenMigrationValueIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            retiringFederationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(DUSTY_AMOUNT_SEND_REQUESTED)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreEnoughUtxosToPay_shouldCreateMigrationTx() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreNotEnoughUtxosToPay_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            retiringFederationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        /**
         * Tests an unrealistic scenario where the federation's balance differs from the value being migrated. Although
         * unreal, the method {@link ReleaseTransactionBuilder#buildMigrationTransaction(Coin, Address)} receives the
         * value to migrate as a parameter, and permits it to be less than the federation's balance. In reality, there's
         * no partial migration. Instead, all the UTXOs available for migration are migrated.
         */
        @Test
        void buildMigrationTransaction_whenFederationBalanceDiffersWithValueMigrated_shouldCreateMigrationTxWithTwoOutputs() {
            // Arrange
            int numberOfUtxos = 10;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = MINIMUM_PEGIN_TX_VALUE.subtract(Coin.valueOf(1000L)).multiply(numberOfUtxos);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );
            assertMigrationTxWithTwoMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 10})
        void buildMigrationTransaction_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards(int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize() {
            // Arrange
            int numberOfUtxos = 2438;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenTxIsAlmostExceedingMaxTxSize_shouldCreateMigrationTx() {
            // Arrange
            int numberOfUtxos = 2437;
            retiringFederationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(retiringFederationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, newFederationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
            assertBtcTxVersionIs2(migrationTransaction);

            List<TransactionInput> migrationTransactionInputs = migrationTransaction.getInputs();
            assertEquals(retiringFederationUTXOs.size(), migrationTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                migrationTransaction,
                retiringFederationRedeemScript,
                retiringFederationUTXOs
            );

            assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(migrationTransactionResult.selectedUTXOs(), migrationTransactionInputs);
        }
    }

    private void assertSelectedUtxosAreFederationUtxosAndMigrationTxInputs(List<UTXO> selectedUTXOsForMigration,
                                                                           List<TransactionInput> migrationTransactionInputs) {
        assertEquals(retiringFederationUTXOs, selectedUTXOsForMigration);
        assertSelectedUtxosBelongToTheInputs(selectedUTXOsForMigration, migrationTransactionInputs);
    }

    /**
     * Used only in unrealistic scenarios where the requested migration value differs from the total value
     * available in the retiring federation UTXOs. In that case, the change is also sent to the
     * migration destination address implying that the total value sent is greater than the requested migration value.
     */
    private void assertMigrationTxWithTwoMigrationOutputs(BtcTransaction migrationTransaction,
                                                          Coin migratedValue) {
        int expectedNumberOfOutputs = 2;
        List<TransactionOutput> migrationTransactionOutputs = migrationTransaction.getOutputs();
        assertReleaseTxNumberOfOutputs(expectedNumberOfOutputs, migrationTransactionOutputs);
        assertDestinationAddress(migrationTransactionOutputs, newFederationAddress, BTC_MAINNET_PARAMS);
        assertMigrationTransactionIsMigratingMoreThanRequestedValue(migratedValue, migrationTransaction);
    }

    private void assertMigrationTxWithOnlyMigrationOutputs(BtcTransaction migrationTransaction,
                                                           Coin migratedAmount
    ) {
        int expectedNumberOfChangeOutputs = 0;
        int expectedNumberOfMigrationOutputs = 1;
        int expectedNumberOfOutputs = expectedNumberOfMigrationOutputs + expectedNumberOfChangeOutputs;
        List<TransactionOutput> migrationTransactionOutputs = migrationTransaction.getOutputs();
        assertReleaseTxNumberOfOutputs(expectedNumberOfOutputs, migrationTransactionOutputs);
        assertDestinationAddress(migrationTransactionOutputs, newFederationAddress, BTC_MAINNET_PARAMS);

        List<TransactionOutput> migrationTransactionChangeOutputs = getChangeOutputs(migrationTransaction);
        assertEquals(expectedNumberOfChangeOutputs, migrationTransactionChangeOutputs.size());
        assertReleaseTxWithNoChangeHasPegoutsAmountWithFeesProperly(migrationTransaction, migratedAmount);
    }

    private List<TransactionOutput> getChangeOutputs(BtcTransaction migrationTransaction) {
        return migrationTransaction.getOutputs().stream()
            .filter(this::isFederationOutput)
            .toList();
    }

    private boolean isFederationOutput(TransactionOutput output) {
        Address destination = output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
        return destination.equals(retiringFederationAddress);
    }

    private void setUpActivationConfig(ActivationConfig.ForBlock activationConfig) {
        this.activationConfig = activationConfig;
    }

    private void setUpFeePerKb(Coin transactionFeePerKb) {
        this.transactionFeePerKb = transactionFeePerKb;
    }

    private static void assertMigrationTransactionIsMigratingMoreThanRequestedValue(Coin migrationValueRequested, BtcTransaction migrationTransaction) {
        Coin migratedValue = getMigrationTransactionValueSent(migrationTransaction);
        Coin fee = migrationTransaction.getFee();
        Coin totalValueSent = migratedValue.add(fee);
        assertTrue(totalValueSent.isGreaterThan(migrationValueRequested));
    }

    private static Coin getMigrationTransactionValueSent(BtcTransaction migrationTransaction) {
        return migrationTransaction.getOutputs().stream().map(TransactionOutput::getValue).reduce(Coin.ZERO, Coin::add);
    }

    private void setUpWallet(List<UTXO> utxos) {
        Repository repository = createRepository();
        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            BTC_MAINNET_PARAMS,
            activationConfig
        );

        Context btcContext = new Context(BTC_MAINNET_PARAMS);
        wallet = BridgeUtils.getFederationSpendWallet(
            btcContext,
            retiringFederation,
            utxos,
            true,
            bridgeStorageProvider
        );
    }

    private ReleaseTransactionBuilder setupWalletAndCreateReleaseTransactionBuilder(
        List<UTXO> utxos) {
        setUpWallet(utxos);
        return createReleaseTransactionBuilder();
    }

    protected ReleaseTransactionBuilder createReleaseTransactionBuilder() {
        return new ReleaseTransactionBuilder(
            BTC_MAINNET_PARAMS,
            wallet,
            retiringFederationFormatVersion,
            retiringFederationAddress,
            transactionFeePerKb,
            activationConfig
        );
    }
}
