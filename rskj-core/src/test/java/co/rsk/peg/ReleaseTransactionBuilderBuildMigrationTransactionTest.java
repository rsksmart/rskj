package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_1;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.SUCCESS;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.assertP2shP2wshScriptWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createUTXOs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.ReleaseTransactionBuilder.BuildResult;
import co.rsk.peg.ReleaseTransactionBuilder.Response;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
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
    private static final ActivationConfig.ForBlock PAPYRUS_ACTIVATIONS = ActivationConfigsForTest.papyrus200()
        .forBlock(0);

    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();
    private static final Context BTC_CONTEXT = new Context(BTC_MAINNET_PARAMS);

    private static final Coin MINIMUM_PEGIN_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(
        ALL_ACTIVATIONS);

    private static final Coin HIGH_FEE_PER_KB = Coin.valueOf(1_000_000);
    private static final int LARGE_NUMBER_OF_UTXOS = 100;

    private static final Coin DUSTY_AMOUNT_SEND_REQUESTED = BtcTransaction.MIN_NONDUST_OUTPUT.minus(
        Coin.SATOSHI);

    private static final int OUTPUTS_COUNT_WITHOUT_CHANGE = 1;
    private static final int OUTPUTS_COUNT_WITH_CHANGE = 2;
    public static final int EXPECTED_MIGRATION_OUTPUTS_COUNT = 1;

    protected Federation retiringFederation;
    protected int retiringFederationFormatVersion;
    protected Address retiringFederationAddress;
    protected List<UTXO> retiringFederationUTXOs;
    protected Wallet wallet;

    private ActivationConfig.ForBlock activationConfig;
    private Coin transactionFeePerKb;
    private Address destinationAddress;
    private Coin dustAmount;

    @BeforeEach
    void setUp() {
        setUpActivationConfig(ALL_ACTIVATIONS);
        setUpTransactionFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
        destinationAddress = new BtcECKey().toAddress(BTC_MAINNET_PARAMS);
        dustAmount = transactionFeePerKb.div(2);
    }

    @Nested
    class StandardMultiSigFederationTests {

        @BeforeEach
        void setUp() {
            retiringFederation = StandardMultiSigFederationBuilder.builder().build();
            retiringFederationFormatVersion = retiringFederation.getFormatVersion();
            retiringFederationAddress = retiringFederation.getAddress();
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
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenRSKIP201IsNotActive_shouldCreateReleaseTxWithBtcVersion1() {
            // Arrange
            setUpActivationConfig(PAPYRUS_ACTIVATIONS);
            int expectedNumberOfUTXOsToMigrate = 1;
            retiringFederationUTXOs = createUTXOs(expectedNumberOfUTXOsToMigrate, MINIMUM_PEGIN_TX_VALUE,
                retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_1, releaseTransaction.getVersion());
            assertEquals(expectedNumberOfUTXOsToMigrate, releaseTransaction.getInputs().size());
            assertEquals(OUTPUTS_COUNT_WITHOUT_CHANGE, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(1, releaseTransaction.getInputs().size());
            assertEquals(OUTPUTS_COUNT_WITHOUT_CHANGE, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @ParameterizedTest
        @ValueSource(ints = {50, 51})
        void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx(int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = createUTXOs(numberOfUtxos, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(numberOfUtxos, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenMigrationValueIsDifferentThanBalance_shouldCreateMigrationTx() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, Coin.COIN, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = Coin.COIN.div(2);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenInsufficientFunds_shouldReturnInsufficientMoney() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance().add(Coin.SATOSHI);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(INSUFFICIENT_MONEY, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenResultChangeOutputWillBeDust_shouldCreateTxWithDustChangeOutput() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGIN_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            retiringFederationUTXOs = createUTXOs(1, utxoAmount, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                MINIMUM_PEGIN_TX_VALUE, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(OUTPUTS_COUNT_WITH_CHANGE, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenMigrationValueIsDusty_shouldReturnDustySendRequested() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                DUSTY_AMOUNT_SEND_REQUESTED, destinationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpTransactionFeePerKb(HIGH_FEE_PER_KB);
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenUtxosAreDustButEnoughToPay_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, dustAmount, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @ParameterizedTest
        @ValueSource(ints = {277, 278})
        void buildMigrationTransaction_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = createUTXOs(numberOfUtxos, Coin.COIN, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @ParameterizedTest
        @ValueSource(ints = {275, 276})
        void buildMigrationTransaction_whenTxIsAlmostExceedingMaxTxSize_shouldCreateMigrationTx(int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = createUTXOs(numberOfUtxos, Coin.COIN, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(numberOfUtxos, releaseTransaction.getInputs().size());
            assertEquals(EXPECTED_MIGRATION_OUTPUTS_COUNT, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        private void assertReleaseTxInputsHasProperFormatAndBelongsToFederation(
            BtcTransaction releaseTransaction) {
            for (TransactionInput releaseInput : releaseTransaction.getInputs()) {
                Script inputScriptSig = releaseInput.getScriptSig();
                assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat(inputScriptSig,
                    retiringFederation.getRedeemScript());

                assertReleaseInputIsFromFederationUTXOsWallet(releaseInput);
            }
        }
    }

    @Nested
    class P2shErpFederationTests {

        @BeforeEach
        void setUp() {
            retiringFederation = P2shErpFederationBuilder.builder().build();
            retiringFederationFormatVersion = retiringFederation.getFormatVersion();
            retiringFederationAddress = retiringFederation.getAddress();
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
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenRSKIP201IsNotActive_shouldCreateReleaseTxWithBtcVersion1() {
            // Arrange
            setUpActivationConfig(PAPYRUS_ACTIVATIONS);
            int expectedNumberOfUTXOsToMigrate = 1;
            retiringFederationUTXOs = createUTXOs(expectedNumberOfUTXOsToMigrate, MINIMUM_PEGIN_TX_VALUE,
                retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_1, releaseTransaction.getVersion());
            assertEquals(expectedNumberOfUTXOsToMigrate, releaseTransaction.getInputs().size());
            assertEquals(OUTPUTS_COUNT_WITHOUT_CHANGE, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(1, releaseTransaction.getInputs().size());
            assertEquals(OUTPUTS_COUNT_WITHOUT_CHANGE, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @ParameterizedTest
        @ValueSource(ints = {50, 51})
        void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx(int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = createUTXOs(numberOfUtxos, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(numberOfUtxos, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenMigrationValueIsDifferentThanBalance_shouldCreateMigrationTx() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, Coin.COIN, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = Coin.COIN.div(2);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenInsufficientFunds_shouldReturnInsufficientMoney() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance().add(Coin.SATOSHI);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(INSUFFICIENT_MONEY, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenResultChangeOutputWillBeDust_shouldCreateTxWithDustChangeOutput() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGIN_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            retiringFederationUTXOs = createUTXOs(1, utxoAmount, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                MINIMUM_PEGIN_TX_VALUE, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(OUTPUTS_COUNT_WITH_CHANGE, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenMigrationValueIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                DUSTY_AMOUNT_SEND_REQUESTED, destinationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpTransactionFeePerKb(HIGH_FEE_PER_KB);
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenUtxosAreDustButEnoughToPay_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, dustAmount, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @ParameterizedTest
        @ValueSource(ints = {196, 197})
        void buildMigrationTransaction_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = createUTXOs(numberOfUtxos, Coin.COIN, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @ParameterizedTest
        @ValueSource(ints = {194, 195})
        void buildMigrationTransaction_whenTxIsAlmostExceedingMaxTxSize_shouldCreateMigrationTx(
            int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = createUTXOs(numberOfUtxos, Coin.COIN, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(numberOfUtxos, releaseTransaction.getInputs().size());
            assertEquals(EXPECTED_MIGRATION_OUTPUTS_COUNT, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        private void assertReleaseTxInputsHasProperFormatAndBelongsToFederation(
            BtcTransaction releaseTransaction) {
            for (TransactionInput releaseInput : releaseTransaction.getInputs()) {
                Script inputScriptSig = releaseInput.getScriptSig();
                assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat(inputScriptSig,
                    retiringFederation.getRedeemScript());

                assertReleaseInputIsFromFederationUTXOsWallet(releaseInput);
            }
        }
    }

    @Nested
    class P2wshErpFederationTests {

        @BeforeEach
        void setUp() {
            retiringFederation = P2shP2wshErpFederationBuilder.builder().build();
            retiringFederationFormatVersion = retiringFederation.getFormatVersion();
            retiringFederationAddress = retiringFederation.getAddress();
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
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenRSKIP201IsNotActive_shouldCreateReleaseTxWithBtcVersion1() {
            // Arrange
            setUpActivationConfig(PAPYRUS_ACTIVATIONS);
            int expectedNumberOfUTXOsToMigrate = 1;
            retiringFederationUTXOs = createUTXOs(expectedNumberOfUTXOsToMigrate, MINIMUM_PEGIN_TX_VALUE,
                retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_1, releaseTransaction.getVersion());
            assertEquals(expectedNumberOfUTXOsToMigrate, releaseTransaction.getInputs().size());
            assertEquals(OUTPUTS_COUNT_WITHOUT_CHANGE, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(1, releaseTransaction.getInputs().size());
            assertEquals(OUTPUTS_COUNT_WITHOUT_CHANGE, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @ParameterizedTest
        @ValueSource(ints = {50, 51})
        void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx(
            int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = createUTXOs(numberOfUtxos, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(numberOfUtxos, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenMigrationValueIsDifferentThanBalance_shouldCreateMigrationTx() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, Coin.COIN, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = Coin.COIN.div(2);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenInsufficientFunds_shouldReturnInsufficientMoney() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance().add(Coin.SATOSHI);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(INSUFFICIENT_MONEY, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenResultChangeOutputWillBeDust_shouldCreateTxWithDustChangeOutput() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGIN_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            retiringFederationUTXOs = createUTXOs(1, utxoAmount, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                MINIMUM_PEGIN_TX_VALUE, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(OUTPUTS_COUNT_WITH_CHANGE, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @Test
        void buildMigrationTransaction_whenMigrationValueIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                DUSTY_AMOUNT_SEND_REQUESTED, destinationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpTransactionFeePerKb(HIGH_FEE_PER_KB);
            retiringFederationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @Test
        void buildMigrationTransaction_whenUtxosAreDustButEnoughToPay_shouldCreateMigrationTx() {
            // Arrange
            retiringFederationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, dustAmount, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        @ParameterizedTest
        @ValueSource(ints = {2438, 2439})
        void buildMigrationTransaction_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = createUTXOs(numberOfUtxos, Coin.COIN, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, migrationTransactionResult);
            assertNull(migrationTransactionResult.btcTx());
            assertNull(migrationTransactionResult.selectedUTXOs());
        }

        @ParameterizedTest
        @ValueSource(ints = {2436, 2437})
        void buildMigrationTransaction_whenTxIsAlmostExceedingMaxTxSize_shouldCreateMigrationTx(int numberOfUtxos) {
            // Arrange
            retiringFederationUTXOs = createUTXOs(numberOfUtxos, Coin.COIN, retiringFederationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
            assertEquals(retiringFederationUTXOs, migrationTransactionResult.selectedUTXOs());
            BtcTransaction releaseTransaction = migrationTransactionResult.btcTx();
            assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
            assertEquals(numberOfUtxos, releaseTransaction.getInputs().size());
            assertEquals(EXPECTED_MIGRATION_OUTPUTS_COUNT, releaseTransaction.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
        }

        private void assertReleaseTxInputsHasProperFormatAndBelongsToFederation(
            BtcTransaction releaseTransaction) {
            List<TransactionInput> releaseTransactionInputs = releaseTransaction.getInputs();
            for (TransactionInput releaseInput : releaseTransactionInputs) {
                int inputIndex = releaseTransactionInputs.indexOf(releaseInput);
                TransactionWitness witness = releaseTransaction.getWitness(inputIndex);
                assertP2shP2wshScriptWithoutSignaturesHasProperFormat(witness,
                    retiringFederation.getRedeemScript());

                assertReleaseInputIsFromFederationUTXOsWallet(releaseInput);
            }
        }
    }

    private void setUpActivationConfig(ActivationConfig.ForBlock activationConfig) {
        this.activationConfig = activationConfig;
    }

    private void setUpTransactionFeePerKb(Coin transactionFeePerKb) {
        this.transactionFeePerKb = transactionFeePerKb;
    }

    private void setUpWallet(List<UTXO> utxos) {
        Repository repository = createRepository();
        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            BTC_MAINNET_PARAMS,
            activationConfig
        );

        wallet = BridgeUtils.getFederationSpendWallet(
            BTC_CONTEXT,
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

    private void assertBuildResultResponseCode(
        ReleaseTransactionBuilder.Response expectedResponseCode, BuildResult batchedPegoutsResult) {
        Response actualResponseCode = batchedPegoutsResult.responseCode();
        assertEquals(expectedResponseCode, actualResponseCode);
    }

    private void assertReleaseInputIsFromFederationUTXOsWallet(TransactionInput releaseInput) {
        Predicate<UTXO> isUTXOAndReleaseInputFromTheSameOutpoint = utxo ->
            utxo.getHash().equals(releaseInput.getOutpoint().getHash())
                && utxo.getIndex() == releaseInput.getOutpoint().getIndex();
        Optional<UTXO> foundUtxo = retiringFederationUTXOs.stream()
            .filter(isUTXOAndReleaseInputFromTheSameOutpoint).findFirst();
        assertTrue(foundUtxo.isPresent());
    }
}
