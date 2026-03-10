package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.SUCCESS;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.assertBtcTxVersionIs1;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.assertBtcTxVersionIs2;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.assertBuildResultResponseCode;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.assertDestinationAddress;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.assertReleaseTxWithNoChangeHasPegoutsAmountWithFeesProperly;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.assertSelectedUtxosBelongToTheInputs;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
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
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.ReleaseTransactionBuilder.BuildResult;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import co.rsk.test.builders.UTXOBuilder;
import java.math.BigInteger;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReleaseTransactionBuilderBuildEmptyWalletToTest {

    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0);
    private static final ActivationConfig.ForBlock PAPYRUS_ACTIVATIONS = ActivationConfigsForTest.papyrus200().forBlock(0);

    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();
    private static final Context BTC_CONTEXT = new Context(BTC_MAINNET_PARAMS);

    private static final Coin MINIMUM_PEGIN_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(ALL_ACTIVATIONS);
    private static final Coin HIGH_FEE_PER_KB = Coin.valueOf(1_000_000);
    private static final int RECIPIENT_ADDRESS_KEY_OFFSET = 3100;
    private static final Address RECIPIENT_ADDRESS = createRecipientAddress();

    protected Federation federation;
    protected int federationFormatVersion;
    protected Address federationAddress;
    protected List<UTXO> federationUTXOs;
    protected Script federationOutputScript;
    protected Script federationRedeemScript;
    protected Wallet wallet;

    private ActivationConfig.ForBlock activations;
    private Coin feePerKb;

    @BeforeEach
    void setUp() {
        setUpActivations(ALL_ACTIVATIONS);
        setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
    }

    @Nested
    class StandardMultiSigFederationTests {

        @BeforeEach
        void setup() {
            federation = StandardMultiSigFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();
            federationOutputScript = federation.getP2SHScript();
            federationRedeemScript = federation.getRedeemScript();
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            setUpWallet(federationUTXOs);
        }

        /**
         * This is an unrealistic scenario. The federation wallet is built from the peg-in UTXOs, so it cannot
         * be empty. If that were the case, the peg-in transaction would fail at the validation point
         * in {@link BridgeSupport#registerBtcTransaction(org.ethereum.core.Transaction, byte[], int, byte[])}.
         */
        @Test
        void buildEmptyWalletTo_whenFedHasNoUTXOs_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            federationUTXOs = List.of();
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            assertNull(emptyWalletResult.btcTx());
            assertNull(emptyWalletResult.selectedUTXOs());
        }

        @Test
        void buildEmptyWalletTo_whenRSKIP201IsNotActive_shouldCreateRefundTxWithBtcVersion1() {
            // Arrange
            setUpActivations(PAPYRUS_ACTIVATIONS);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs1(emptyWalletTransaction);

            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            assertEquals(federationUTXOs.size(), emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenSingleUtxoInWallet_shouldCreateRefundTxSpendingSingleUtxo() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(emptyWalletTransaction);

            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            int expectedNumberOfUtxos = 1;
            assertEquals(expectedNumberOfUtxos, emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenMultipleUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(emptyWalletTransaction);

            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            assertEquals(federationUTXOs.size(), emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
            // Arrange
            int numberOfUtxos = 277;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, emptyWalletResult);
            assertNull(emptyWalletResult.btcTx());
            assertNull(emptyWalletResult.selectedUTXOs());
        }

        @Test
        void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreEnoughToPayForFees_shouldCreateRefundTx() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(emptyWalletTransaction);
            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            assertEquals(numberOfUtxos, emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 3;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            assertNull(emptyWalletResult.btcTx());
            assertNull(emptyWalletResult.selectedUTXOs());
        }
    }

    @Nested
    class P2shFederationTests {

        @BeforeEach
        void setup() {
            federation = P2shErpFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();
            federationOutputScript = federation.getP2SHScript();
            federationRedeemScript = federation.getRedeemScript();
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            setUpWallet(federationUTXOs);
        }

        /**
         * This is an unrealistic scenario. The federation wallet is built from the peg-in UTXOs, so it cannot
         * be empty. If that were the case, the peg-in transaction would fail at the validation point
         * in {@link BridgeSupport#registerBtcTransaction(org.ethereum.core.Transaction, byte[], int, byte[])}.
         */
        @Test
        void buildEmptyWalletTo_whenFedHasNoUTXOs_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            federationUTXOs = List.of();
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            assertNull(emptyWalletResult.btcTx());
            assertNull(emptyWalletResult.selectedUTXOs());
        }

        @Test
        void buildEmptyWalletTo_whenRSKIP201IsNotActive_shouldCreateRefundTxWithBtcVersion1() {
            // Arrange
            setUpActivations(PAPYRUS_ACTIVATIONS);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs1(emptyWalletTransaction);

            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            assertEquals(federationUTXOs.size(), emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenSingleUtxoInWallet_shouldCreateRefundTxSpendingSingleUtxo() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(emptyWalletTransaction);

            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            int expectedNumberOfUtxos = 1;
            assertEquals(expectedNumberOfUtxos, emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenMultipleUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(emptyWalletTransaction);

            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            assertEquals(federationUTXOs.size(), emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
            // Arrange
            int numberOfUtxos = 196;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, emptyWalletResult);
            assertNull(emptyWalletResult.btcTx());
            assertNull(emptyWalletResult.selectedUTXOs());
        }

        @Test
        void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreEnoughToPayForFees_shouldCreateRefundTx() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(emptyWalletTransaction);
            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            assertEquals(numberOfUtxos, emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 3;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            assertNull(emptyWalletResult.btcTx());
            assertNull(emptyWalletResult.selectedUTXOs());
        }
    }

    @Nested
    class P2shP2wshFederationTests {

        @BeforeEach
        void setup() {
            federation = P2shP2wshErpFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();
            federationOutputScript = federation.getP2SHScript();
            federationRedeemScript = federation.getRedeemScript();
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            setUpWallet(federationUTXOs);
        }

        /**
         * This is an unrealistic scenario. The federation wallet is built from the peg-in UTXOs, so it cannot
         * be empty. If that were the case, the peg-in transaction would fail at the validation point
         * in {@link BridgeSupport#registerBtcTransaction(org.ethereum.core.Transaction, byte[], int, byte[])}.
         */
        @Test
        void buildEmptyWalletTo_whenFedHasNoUTXOs_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            federationUTXOs = List.of();
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            assertNull(emptyWalletResult.btcTx());
            assertNull(emptyWalletResult.selectedUTXOs());
        }

        @Test
        void buildEmptyWalletTo_whenRSKIP201IsNotActive_shouldCreateRefundTxWithBtcVersion1() {
            // Arrange
            setUpActivations(PAPYRUS_ACTIVATIONS);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs1(emptyWalletTransaction);

            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            assertEquals(federationUTXOs.size(), emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenSingleUtxoInWallet_shouldCreateRefundTxSpendingSingleUtxo() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(emptyWalletTransaction);

            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            int expectedNumberOfUtxos = 1;
            assertEquals(expectedNumberOfUtxos, emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenMultipleUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(emptyWalletTransaction);

            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            assertEquals(federationUTXOs.size(), emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
            // Arrange
            int numberOfUtxos = 2438;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, emptyWalletResult);
            assertNull(emptyWalletResult.btcTx());
            assertNull(emptyWalletResult.selectedUTXOs());
        }

        @Test
        void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreEnoughToPayForFees_shouldCreateRefundTx() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction emptyWalletTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(emptyWalletTransaction);
            List<TransactionInput> emptyWalletTransactionInputs = emptyWalletTransaction.getInputs();
            assertEquals(numberOfUtxos, emptyWalletTransactionInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation(
                emptyWalletTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertRefundTxHasOnlyPegoutOutput(emptyWalletTransaction);
            assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), emptyWalletTransactionInputs);
        }

        @Test
        void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 3;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            // Act
            BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            assertNull(emptyWalletResult.btcTx());
            assertNull(emptyWalletResult.selectedUTXOs());
        }
    }

    private void setUpActivations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
    }

    private void setUpFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    private static Address createRecipientAddress() {
        BigInteger seed = BigInteger.valueOf(RECIPIENT_ADDRESS_KEY_OFFSET);
        return BtcECKey.fromPrivate(seed).toAddress(BTC_MAINNET_PARAMS);
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

    private ReleaseTransactionBuilder setupWalletAndCreateReleaseTransactionBuilder(List<UTXO> utxos) {
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

    private void assertRefundTxHasOnlyPegoutOutput(BtcTransaction refundTransaction) {
        List<TransactionOutput> outputs = refundTransaction.getOutputs();
        int expectedNumberOfOutputs = 1;
        assertEquals(expectedNumberOfOutputs, refundTransaction.getOutputs().size());

        TransactionOutput onlyOutput = outputs.get(0);
        assertDestinationAddress(outputs, RECIPIENT_ADDRESS);
        assertTrue(onlyOutput.getValue().isPositive());

        List<TransactionOutput> changeOutputs = outputs.stream()
            .filter(this::isFederationOutput)
            .toList();
        int expectedNumberOfChangeOutputs = 0;
        assertEquals(expectedNumberOfChangeOutputs, changeOutputs.size());

        assertReleaseTxWithNoChangeHasPegoutsAmountWithFeesProperly(refundTransaction, refundTransaction.getInputSum());
    }

    private boolean isFederationOutput(TransactionOutput output) {
        Address destinationAddress = output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
        return destinationAddress.equals(federationAddress);
    }
}
