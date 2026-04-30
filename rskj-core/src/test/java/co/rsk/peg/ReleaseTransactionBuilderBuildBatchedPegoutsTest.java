package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.SUCCESS;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.*;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import co.rsk.peg.ReleaseRequestQueue.Entry;
import co.rsk.peg.ReleaseTransactionBuilder.BuildResult;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.HashMap;

import co.rsk.test.builders.UTXOBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReleaseTransactionBuilderBuildBatchedPegoutsTest {

    private static final int EXPECTED_NUMBER_OF_CHANGE_OUTPUTS = 1;
    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all()
        .forBlock(0);
    private static final ActivationConfig.ForBlock PAPYRUS_ACTIVATIONS = ActivationConfigsForTest.papyrus200()
        .forBlock(0);

    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();

    private static final Coin MINIMUM_PEGOUT_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPegoutTxValue();
    private static final Coin MINIMUM_PEGIN_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(
        ALL_ACTIVATIONS);

    private static final List<ReleaseRequestQueue.Entry> NO_PEGOUT_REQUESTS = Collections.emptyList();

    private static final Coin DUST_VALUE = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.minus(Coin.SATOSHI);
    private static final Coin HIGH_FEE_PER_KB = Coin.valueOf(1_000_000);
    public static final Coin THOUSAND_SATOSHIS = Coin.valueOf(1000);

    protected Federation federation;
    protected int federationFormatVersion;
    protected Address federationAddress;
    protected List<UTXO> federationUTXOs;
    protected Script federationOutputScript;
    protected Wallet wallet;

    private ActivationConfig.ForBlock activations;
    private Coin feePerKb;
    private Script federationRedeemScript;

    @BeforeEach
    void setUp() {
        setUpActivations(ALL_ACTIVATIONS);
        setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
    }

    @Nested
    class StandardMultiSigFederationTests {

        @BeforeEach
        void setUp() {
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

        @Test
        void buildBatchedPegouts_whenNoPegoutRequests_shouldThrowIllegalStateException() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();

            // Act & Assert
            assertThrows(IllegalStateException.class,
                () -> releaseTransactionBuilder.buildBatchedPegouts(NO_PEGOUT_REQUESTS));
        }

        @Test
        void buildBatchedPegouts_whenRSKIP201IsNotActive_shouldCreateBatchedPegoutsTxWithBtcVersion1() {
            // Arrange
            setUpActivations(PAPYRUS_ACTIVATIONS);
            Coin minimumPeginTxValue = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(PAPYRUS_ACTIVATIONS);
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(minimumPeginTxValue)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs1(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenSinglePegoutRequest_shouldCreateBatchedPegoutsTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenMultiplePegoutRequest_shouldCreateBatchedPegoutsTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(3,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 3;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateBatchedPegoutsTxWithNoChangeOutput() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
            );

            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasOnlyPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            Coin pegoutRequestAmountExceedingFederationBalance = MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, pegoutRequestAmountExceedingFederationBalance);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, INSUFFICIENT_MONEY);
        }

        @Test
        void buildBatchedPegouts_whenOriginalChangeIsMaxDustValue_shouldCreateBatchedPegoutsTxDecrementingFirstOutputAndSettingNonDustChange() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUST_VALUE);
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(utxoAmount)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenChangeIsMinNonDustValue_shouldCreateBatchedPegoutsTxWithNoModificationInTheValues() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE.add(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT))
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenOriginalChangeIsOneSatoshi_shouldCreateBatchedPegoutsTxDecrementingFirstOutputAndSettingNonDustChange() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI))
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenFedHasOnlyMinimumNonDustUtxos_shouldReturnCouldNotAdjustDownwards() {
            // Spending an input with a p2sh script costs more than MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.
            // Therefore, if the federation has only UTXOs with that minimum non-dust value,
            // it won't be possible to adjust downwards the pegout amount to avoid creating a dust output.
            // Arrange
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin valueRequested = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.multiply(numberOfUtxos);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, valueRequested);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, COULD_NOT_ADJUST_DOWNWARDS);
        }

        /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; real pegouts must be at least
         * {@link BridgeConstants#getMinimumPegoutTxValue()}, but we use it to exercise the
         * DUSTY_SEND_REQUESTED path.
         */
        @Test
        void buildBatchedPegouts_whenPegoutRequestAmountIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                DUST_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, DUSTY_SEND_REQUESTED);
        }

        @Test
        void buildBatchedPegouts_whenEstimatedFeeIsTooHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 3;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, COULD_NOT_ADJUST_DOWNWARDS);
        }

        @ParameterizedTest
        @CsvSource({
            "277, 1",
            "276, 10",
        })
        void buildBatchedPegouts_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(numberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests).subtract(THOUSAND_SATOSHIS);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, EXCEED_MAX_TRANSACTION_SIZE);
        }

        @ParameterizedTest
        @CsvSource({
            "276, 1",
            "276, 9",
            "275, 10",
        })
        void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateBatchedPegoutsTx(
            int expectedNumberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(expectedNumberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests).subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUtxos, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }
    }

    @Nested
    class P2shErpFederationTests {

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

        @Test
        void buildBatchedPegouts_whenNoPegoutRequests_shouldThrowIllegalStateException() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                () -> releaseTransactionBuilder.buildBatchedPegouts(NO_PEGOUT_REQUESTS));
        }

        @Test
        void buildBatchedPegouts_whenSinglePegoutRequest_shouldCreateBatchedPegoutsTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(
                1, MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenMultiplePegoutRequest_shouldCreateBatchedPegoutsTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(3,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            assertInputsHasProperFormatAndBelongsToP2shErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateBatchedPegoutsTxWithNoChangeOutput() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasOnlyPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            Coin pegoutRequestAmountExceedingFederationBalance = MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, pegoutRequestAmountExceedingFederationBalance);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, INSUFFICIENT_MONEY);
        }

        @Test
        void buildBatchedPegouts_whenOriginalChangeIsMaxDustValue_shouldCreateBatchedPegoutsTxDecrementingFirstOutputAndSettingNonDustChange() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUST_VALUE);
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(utxoAmount)
                    .build()
            );

            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenChangeIsMinNonDustValue_shouldCreateBatchedPegoutsTxWithNoModificationInTheValues() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE.add(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT))
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenOriginalChangeIsOneSatoshi_shouldCreateBatchedPegoutsTxDecrementingFirstOutputAndSettingNonDustChange() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI))
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenFedHasOnlyMinimumNonDustUtxos_shouldReturnCouldNotAdjustDownwards() {
            // Spending an input with a p2sh script costs more than MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.
            // Therefore, if the federation has only UTXOs with that minimum non-dust value,
            // it won't be possible to adjust downwards the pegout amount to avoid creating a dust output.
            // Arrange
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin valueRequested = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.multiply(numberOfUtxos);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, valueRequested);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, COULD_NOT_ADJUST_DOWNWARDS);
        }

        /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; real pegouts must be at least
         * {@link BridgeConstants#getMinimumPegoutTxValue()}, but we use it to exercise the
         * DUSTY_SEND_REQUESTED path.
         */
        @Test
        void buildBatchedPegouts_whenPegoutRequestAmountIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                DUST_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, DUSTY_SEND_REQUESTED);
        }

        @Test
        void buildBatchedPegouts_whenEstimatedFeeIsTooHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 3;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, COULD_NOT_ADJUST_DOWNWARDS);
        }

        @ParameterizedTest
        @CsvSource({
            "196, 1",
            "195, 15",
        })
        void buildBatchedPegouts_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(numberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests).subtract(THOUSAND_SATOSHIS);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, EXCEED_MAX_TRANSACTION_SIZE);
        }

        @ParameterizedTest
        @CsvSource({
            "195, 1",
            "195, 14",
            "194, 15",
        })
        void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateBatchedPegoutsTx(int expectedNumberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(expectedNumberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests).subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUtxos, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }
    }

    @Nested
    class P2shP2wshErpFederationTests {

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

        @Test
        void buildBatchedPegouts_whenNoPegoutRequests_returnsAnEmptyTransaction() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();

            // Act & Assert
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                NO_PEGOUT_REQUESTS);
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTx = batchedPegoutsResult.btcTx();
            assertTrue(batchedPegoutsTx.getOutputs().isEmpty());
            assertTrue(batchedPegoutsTx.getInputs().isEmpty());
        }

        @Test
        void buildBatchedPegouts_whenSinglePegoutRequest_shouldCreateBatchedPegoutsTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenMultiplePegoutRequest_shouldCreateBatchedPegoutsTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(3,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);

            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 3;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateBatchedPegoutsTxWithNoChangeOutput() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasOnlyPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);

            Coin pegoutRequestAmountExceedingFederationBalance = MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, pegoutRequestAmountExceedingFederationBalance);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, INSUFFICIENT_MONEY);
        }

        @Test
        void buildBatchedPegouts_whenOriginalChangeIsMaxDust_shouldCreateBatchedPegoutsTxDecrementingFirstOutputAndSettingNonDustChange() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUST_VALUE);
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(utxoAmount)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenChangeIsMinNonDustValue_shouldCreateBatchedPegoutsTxWithNoModificationInTheValues() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE.add(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT))
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenChangeIsOneSatoshi_shouldCreateBatchedPegoutsTxDecrementingFirstOutputAndSettingNonDustChange() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI))
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfInputs = 1;
            assertEquals(expectedNumberOfInputs, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
        }

        @Test
        void buildBatchedPegouts_whenFedHasOnlyMinimumNonDustUtxos_shouldReturnCouldNotAdjustDownwards() {
            // Spending an input with a p2sh-p2wsh script costs more than MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.
            // Therefore, if the federation has only UTXOs with that minimum non-dust value,
            // it won't be possible to adjust downwards the pegout amount to avoid creating a dust output.
            // Arrange
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Coin valueRequested = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.multiply(numberOfUtxos);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, valueRequested);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, COULD_NOT_ADJUST_DOWNWARDS);
        }

        /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; real pegouts must be at least
         * {@link BridgeConstants#getMinimumPegoutTxValue()}, but we use it to exercise the
         * DUSTY_SEND_REQUESTED path.
         */
        @Test
        void buildBatchedPegouts_whenPegoutRequestAmountIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                DUST_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, DUSTY_SEND_REQUESTED);
        }

        @Test
        void buildBatchedPegouts_whenEstimatedFeeIsTooHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 3;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, COULD_NOT_ADJUST_DOWNWARDS);
        }

        @ParameterizedTest
        @CsvSource({
            "2438, 1",
            "2437, 2",
            "2436, 3"
        })
        void buildBatchedPegouts_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(numberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests).subtract(THOUSAND_SATOSHIS);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildFailedWithResponseCode(batchedPegoutsResult, EXCEED_MAX_TRANSACTION_SIZE);
        }

        @ParameterizedTest
        @CsvSource({
            "2437, 1",
            "2436, 2",
            "2435, 3",
        })
        void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateBatchedPegoutsTx(
            int expectedNumberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(expectedNumberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests).subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUtxos, batchedPegoutsTransactionInputs.size());
            assertInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                batchedPegoutsTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsTransactionInputs);
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

        Context btcContext = new Context(BTC_MAINNET_PARAMS);
        wallet = BridgeUtils.getFederationSpendWallet(
            btcContext,
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

    private List<Entry> createPegoutRequests(int count, Coin amount) {
        List<ReleaseRequestQueue.Entry> pegoutRequests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BigInteger seed = BigInteger.valueOf(i + 1100);
            Address recipientAddress = BtcECKey.fromPrivate(seed).toAddress(BTC_MAINNET_PARAMS);
            Entry pegoutEntry = new Entry(
                recipientAddress,
                amount
            );
            pegoutRequests.add(pegoutEntry);
        }
        return pegoutRequests;
    }

    private List<TransactionOutput> getChangeOutputs(BtcTransaction batchedPegoutsTransaction) {
        return batchedPegoutsTransaction.getOutputs().stream()
            .filter(this::isFederationOutput)
            .toList();
    }

    private void assertPegoutRequestsAreIncludedInBatchedPegoutsTx(BtcTransaction batchedPegoutsTransaction,
                                                                   List<Entry> pegoutRequests) {
        List<TransactionOutput> userOutputs = getUserOutputs(batchedPegoutsTransaction);
        assertPegoutRequestsAreIncludedAsUserOutputs(pegoutRequests, userOutputs);
    }

    private List<TransactionOutput> getUserOutputs(BtcTransaction batchedPegoutsTransaction) {
        return batchedPegoutsTransaction.getOutputs().stream()
            .filter(this::isUserOutput)
            .toList();
    }

    private boolean isUserOutput(TransactionOutput output) {
        return !isFederationOutput(output);
    }

    private boolean isFederationOutput(TransactionOutput output) {
        Address recipientAddress = getDestinationAddress(output);
        return recipientAddress.equals(federationAddress);
    }

    private Address getDestinationAddress(TransactionOutput output) {
        return output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
    }

    private void assertPegoutRequestsAreIncludedAsUserOutputs(List<Entry> pegoutRequests,
                                                              List<TransactionOutput> outputs) {
        Map<Address, ArrayDeque<Entry>> byDestination = new HashMap<>();
        for (Entry request : pegoutRequests) {
            byDestination
                .computeIfAbsent(request.getDestination(), k -> new ArrayDeque<>())
                .addLast(request);
        }

        for (TransactionOutput output : outputs) {
            Address outputDestination = getDestinationAddress(output);
            Coin outputAmount = output.getValue();

            ArrayDeque<Entry> queue = byDestination.get(outputDestination);
            Entry pegoutRequest = queue.removeFirst();
            // ensure the transaction output does not exceed the original pegout request amount
            // (outputAmount <= pegoutRequestAmount). The output is below/equal because fees are
            // discounted from the pegout request amount when building the transaction.
            boolean outputIsBelowPegoutRequest = outputAmount.compareTo(pegoutRequest.getAmount()) <= 0;
            assertTrue(outputIsBelowPegoutRequest);
        }

        for (ArrayDeque<Entry> remaining : byDestination.values()) {
            assertTrue(remaining.isEmpty());
        }
    }

    private void assertBatchedPegoutsTxOutputAndChangeOutputsNumbers(BtcTransaction pegoutTransaction,
                                                                     int expectedNumberOfUserOutputs,
                                                                     int expectedNumberOfChangeOutputs) {
        List<TransactionOutput> userOutputs = getUserOutputs(pegoutTransaction);
        assertReleaseTxNumberOfOutputs(expectedNumberOfUserOutputs, userOutputs);

        List<TransactionOutput> pegoutTransactionChangeOutputs = getChangeOutputs(pegoutTransaction);
        assertReleaseTxNumberOfOutputs(expectedNumberOfChangeOutputs, pegoutTransactionChangeOutputs);

        int expectedNumberOfOutputs = expectedNumberOfUserOutputs + expectedNumberOfChangeOutputs;
        assertReleaseTxNumberOfOutputs(expectedNumberOfOutputs, pegoutTransaction.getOutputs());
    }

    private void assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsNonDust(BtcTransaction batchedPegoutsTransaction,
                                                                                            List<Entry> pegoutRequests) {
        int pegoutRequestsNumber = pegoutRequests.size();
        assertBatchedPegoutsTxOutputAndChangeOutputsNumbers(
            batchedPegoutsTransaction,
            pegoutRequestsNumber,
            EXPECTED_NUMBER_OF_CHANGE_OUTPUTS
        );

        assertPegoutRequestsAreIncludedInBatchedPegoutsTx(batchedPegoutsTransaction, pegoutRequests);

        List<TransactionOutput> batchedPegoutsTransactionChangeOutputs = getChangeOutputs(batchedPegoutsTransaction);
        assertDestinationAddress(batchedPegoutsTransactionChangeOutputs, federationAddress, BTC_MAINNET_PARAMS);

        Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
        assertUserAndChangeOutputsValuesWhenOriginalChangeIsNonDust(
            batchedPegoutsTransaction,
            batchedPegoutsTransactionChangeOutputs,
            totalPegoutRequestsAmount
        );
    }

    private void assertBatchedPegoutsTxHasPegoutAndChangeOutputsWhenOriginalChangeIsDust(BtcTransaction batchedPegoutsTransaction,
                                                                                         List<Entry> pegoutRequests) {
        int pegoutRequestsNumber = pegoutRequests.size();
        assertBatchedPegoutsTxOutputAndChangeOutputsNumbers(
            batchedPegoutsTransaction,
            pegoutRequestsNumber,
            EXPECTED_NUMBER_OF_CHANGE_OUTPUTS
        );

        assertPegoutRequestsAreIncludedInBatchedPegoutsTx(batchedPegoutsTransaction, pegoutRequests);

        List<TransactionOutput> batchedPegoutsTransactionChangeOutputs = getChangeOutputs(batchedPegoutsTransaction);
        assertDestinationAddress(batchedPegoutsTransactionChangeOutputs, federationAddress, BTC_MAINNET_PARAMS);

        Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
        assertUserAndChangeOutputsValuesWhenOriginalChangeIsDust(
            batchedPegoutsTransaction,
            batchedPegoutsTransactionChangeOutputs,
            totalPegoutRequestsAmount
        );
    }

    private static Coin getTotalPegoutRequestsAmount(List<Entry> pegoutRequests) {
        return pegoutRequests.stream().map(Entry::getAmount)
            .reduce(Coin.ZERO, Coin::add);
    }

    private void assertBatchedPegoutsTxHasOnlyPegoutOutputs(BtcTransaction batchedPegoutsTransaction,
                                                            List<Entry> pegoutRequests) {
        int expectedNumberOfChangeOutputs = 0;
        int expectedNumberOfOutputs = pegoutRequests.size();
        assertBatchedPegoutsTxOutputAndChangeOutputsNumbers(
            batchedPegoutsTransaction,
            expectedNumberOfOutputs,
            expectedNumberOfChangeOutputs
        );

        assertPegoutRequestsAreIncludedInBatchedPegoutsTx(batchedPegoutsTransaction, pegoutRequests);
        Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
        assertReleaseTxWithOnlyUserOutputsAmounts(batchedPegoutsTransaction, totalPegoutRequestsAmount);
    }
}
