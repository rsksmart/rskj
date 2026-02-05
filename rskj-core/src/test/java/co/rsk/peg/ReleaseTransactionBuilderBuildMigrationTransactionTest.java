package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
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

class ReleaseTransactionBuilderBuildMigrationTransactionTest {

    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all()
        .forBlock(0);

    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();
    private static final Context BTC_CONTEXT = new Context(BTC_MAINNET_PARAMS);

    private static final Coin MINIMUM_PEGIN_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(
        ALL_ACTIVATIONS);

    private static final Coin HIGH_FEE_PER_KB = Coin.valueOf(1_000_000);

    protected Federation federation;
    protected int federationFormatVersion;
    protected Address federationAddress;
    protected List<UTXO> federationUTXOs;
    protected Wallet wallet;

    private ActivationConfig.ForBlock activations;
    private Coin feePerKb;
    private Address destinationAddress;

    @BeforeEach
    void setUp() {
        setUpActivations(ALL_ACTIVATIONS);
        setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
        destinationAddress = new BtcECKey().toAddress(BTC_MAINNET_PARAMS);
    }

    @Nested
    class StandardMultiSigFederationTests {

        @BeforeEach
        void setup() {
            federation = StandardMultiSigFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();
        }

        @Test
        void buildMigrationTransaction_whenNoUTXOsToMigrate_shouldReturnDustySendRequested() {
            // Arrange
            federationUTXOs = Collections.emptyList();
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, result);
            assertNull(result.btcTx());
        }

        @Test
        void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertEquals(1, tx.getInputs().size());
            assertEquals(1, tx.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_when50UTXOsToMigrate_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(50, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertEquals(50, tx.getInputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_when51UTXOsToMigrate_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(51, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertEquals(51, tx.getInputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_whenMigrationValueIsDifferentThanBalance_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(1, Coin.COIN, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = Coin.COIN.div(2);

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            federationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, result);
            assertNull(result.btcTx());
        }

        private void assertReleaseTxInputsHasProperFormatAndBelongsToFederation(BtcTransaction releaseTransaction) {
            for (TransactionInput releaseInput : releaseTransaction.getInputs()) {
                Script inputScriptSig = releaseInput.getScriptSig();
                assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat(inputScriptSig,
                    federation.getRedeemScript());

                assertReleaseInputIsFromFederationUTXOsWallet(releaseInput);
            }
        }

        private void assertReleaseInputIsFromFederationUTXOsWallet(TransactionInput releaseInput) {
            Predicate<UTXO> isUTXOAndReleaseInputFromTheSameOutpoint = utxo ->
                utxo.getHash().equals(releaseInput.getOutpoint().getHash())
                    && utxo.getIndex() == releaseInput.getOutpoint().getIndex();
            Optional<UTXO> foundUtxo = federationUTXOs.stream()
                .filter(isUTXOAndReleaseInputFromTheSameOutpoint).findFirst();
            assertTrue(foundUtxo.isPresent());
        }
    }

    @Nested
    class P2shErpFederationTests {

        @BeforeEach
        void setup() {
            federation = P2shErpFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();
        }

        @Test
        void buildMigrationTransaction_whenNoUTXOsToMigrate_shouldReturnDustySendRequested() {
            // Arrange
            federationUTXOs = Collections.emptyList();
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, result);
            assertNull(result.btcTx());
        }

        @Test
        void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertEquals(1, tx.getInputs().size());
            assertEquals(1, tx.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_when50UTXOsToMigrate_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(50, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertEquals(50, tx.getInputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_when51UTXOsToMigrate_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(51, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertEquals(51, tx.getInputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_whenMigrationValueIsDifferentThanBalance_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(1, Coin.COIN, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = Coin.COIN.div(2);

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            federationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, result);
            assertNull(result.btcTx());
        }

        private void assertReleaseTxInputsHasProperFormatAndBelongsToFederation(BtcTransaction releaseTransaction) {
            for (TransactionInput releaseInput : releaseTransaction.getInputs()) {
                Script inputScriptSig = releaseInput.getScriptSig();
                assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat(inputScriptSig,
                    federation.getRedeemScript());

                assertReleaseInputIsFromFederationUTXOsWallet(releaseInput);
            }
        }

        private void assertReleaseInputIsFromFederationUTXOsWallet(TransactionInput releaseInput) {
            Predicate<UTXO> isUTXOAndReleaseInputFromTheSameOutpoint = utxo ->
                utxo.getHash().equals(releaseInput.getOutpoint().getHash())
                    && utxo.getIndex() == releaseInput.getOutpoint().getIndex();
            Optional<UTXO> foundUtxo = federationUTXOs.stream()
                .filter(isUTXOAndReleaseInputFromTheSameOutpoint).findFirst();
            assertTrue(foundUtxo.isPresent());
        }
    }

    @Nested
    class P2wshErpFederationTests {

        @BeforeEach
        void setup() {
            federation = P2shP2wshErpFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();
        }

        @Test
        void buildMigrationTransaction_whenNoUTXOsToMigrate_shouldReturnDustySendRequested() {
            // Arrange
            federationUTXOs = Collections.emptyList();
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, result);
            assertNull(result.btcTx());
        }

        @Test
        void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertEquals(1, tx.getInputs().size());
            assertEquals(1, tx.getOutputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_when50UTXOsToMigrate_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(50, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertEquals(50, tx.getInputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_when51UTXOsToMigrate_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(51, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertEquals(51, tx.getInputs().size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_whenMigrationValueIsDifferentThanBalance_shouldCreateMigrationTx() {
            // Arrange
            federationUTXOs = createUTXOs(1, Coin.COIN, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = Coin.COIN.div(2);

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(SUCCESS, result);
            BtcTransaction tx = result.btcTx();
            assertEquals(BTC_TX_VERSION_2, tx.getVersion());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(tx);
        }

        @Test
        void buildMigrationTransaction_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            federationUTXOs = createUTXOs(1, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin migrationValue = wallet.getBalance();

            // Act
            BuildResult result = releaseTransactionBuilder.buildMigrationTransaction(migrationValue, destinationAddress);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, result);
            assertNull(result.btcTx());
        }

        private void assertReleaseTxInputsHasProperFormatAndBelongsToFederation(BtcTransaction releaseTransaction) {
            List<TransactionInput> releaseTransactionInputs = releaseTransaction.getInputs();
            for (TransactionInput releaseInput : releaseTransactionInputs) {
                int inputIndex = releaseTransactionInputs.indexOf(releaseInput);
                TransactionWitness witness = releaseTransaction.getWitness(inputIndex);
                assertP2shP2wshScriptWithoutSignaturesHasProperFormat(witness,
                    federation.getRedeemScript());
            }
        }

        private void assertReleaseInputIsFromFederationUTXOsWallet(TransactionInput releaseInput) {
            Predicate<UTXO> isUTXOAndReleaseInputFromTheSameOutpoint = utxo ->
                utxo.getHash().equals(releaseInput.getOutpoint().getHash())
                    && utxo.getIndex() == releaseInput.getOutpoint().getIndex();
            Optional<UTXO> foundUtxo = federationUTXOs.stream()
                .filter(isUTXOAndReleaseInputFromTheSameOutpoint).findFirst();
            assertTrue(foundUtxo.isPresent());
        }
    }

    private void setUpActivations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
    }

    private void setUpFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    private void setUpWallet(List<UTXO> utxos) {
        Repository repository = createRepository();
        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            BTC_MAINNET_PARAMS,
            activations
        );

        wallet = BridgeUtils.getFederationSpendWallet(
            BTC_CONTEXT,
            federation,
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
            federationFormatVersion,
            federationAddress,
            feePerKb,
            activations
        );
    }

    private void assertBuildResultResponseCode(ReleaseTransactionBuilder.Response expectedResponseCode, BuildResult batchedPegoutsResult) {
        Response actualResponseCode = batchedPegoutsResult.responseCode();
        assertEquals(expectedResponseCode, actualResponseCode);
    }
}
