package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.SUCCESS;
import static co.rsk.peg.ReleaseTransactionBuilderTestUtils.assertBtcTxVersionIs1;
import static co.rsk.peg.ReleaseTransactionBuilderTestUtils.assertBtcTxVersionIs2;
import static co.rsk.peg.ReleaseTransactionBuilderTestUtils.assertBuildResultResponseCode;
import static co.rsk.peg.ReleaseTransactionBuilderTestUtils.assertSelectedUtxosBelongToTheInputs;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.assertP2shP2wshScriptWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createUTXOs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import co.rsk.bitcoinj.core.TransactionWitness;
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
import java.util.Optional;
import java.util.function.Predicate;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReleaseTransactionBuilderBuildBatchedPegoutsTest {

    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all()
        .forBlock(0);
    private static final ActivationConfig.ForBlock PAPYRUS_ACTIVATIONS = ActivationConfigsForTest.papyrus200()
        .forBlock(0);

    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();
    private static final Context BTC_CONTEXT = new Context(BTC_MAINNET_PARAMS);

    private static final Coin MINIMUM_PEGOUT_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPegoutTxValue();
    private static final Coin MINIMUM_PEGIN_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(
        ALL_ACTIVATIONS);

    private static final List<ReleaseRequestQueue.Entry> NO_PEGOUT_REQUESTS = Collections.emptyList();

    private static final Coin DUSTY_AMOUNT_SEND_REQUESTED = BtcTransaction.MIN_NONDUST_OUTPUT.minus(
        Coin.SATOSHI);

    private static final Coin HIGH_FEE_PER_KB = Coin.valueOf(1_000_000);

    private static final int RECIPIENT_ADDRESS_KEY_OFFSET = 1100;
    private static final int LARGE_NUMBER_OF_UTXOS = 100;

    protected Federation federation;
    protected int federationFormatVersion;
    protected Address federationAddress;
    protected List<UTXO> federationUTXOs;
    protected Wallet wallet;

    private ActivationConfig.ForBlock activations;
    private Coin feePerKb;
    private Coin smallAmount;

    @BeforeEach
    void setUp() {
        setUpActivations(ALL_ACTIVATIONS);
        setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
        smallAmount = feePerKb.div(2);
    }

    @Nested
    class StandardMultiSigFederationTests {

        @BeforeEach
        void setup() {
            federation = StandardMultiSigFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();
            federationUTXOs = createUTXOs(10, MINIMUM_PEGIN_TX_VALUE, federationAddress);
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
        void buildBatchedPegouts_whenRSKIP201IsNotActive_shouldCreateReleaseTxWithBtcVersion1() {
            // Arrange
            setUpActivations(PAPYRUS_ACTIVATIONS);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs1(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();

            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenSinglePegoutRequest_shouldCreateReleaseTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenMultiplePegoutRequest_shouldCreateReleaseTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(3,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateReleaseTxWithNoChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            federationUTXOs = createUTXOs(expectedNumberOfUTXOs, MINIMUM_PEGOUT_TX_VALUE,
                federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasOnlyPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            federationUTXOs = createUTXOs(1, MINIMUM_PEGOUT_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGIN_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(INSUFFICIENT_MONEY, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @Test
        void buildBatchedPegouts_whenResultChangeOutputWillBeDust_shouldCreateTxWithDustChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            federationUTXOs = createUTXOs(expectedNumberOfUTXOs, utxoAmount,
                federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            assertReleaseTransactionChangeIsDust(releaseTransaction);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenPegoutRequestAmountIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                DUSTY_AMOUNT_SEND_REQUESTED);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @Test
        void buildBatchedPegouts_whenFedSmallUtxosSumEqualsRequestAmount_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            federationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, smallAmount, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @Test
        void buildBatchedPegouts_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            federationUTXOs = createUTXOs(3, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @ParameterizedTest
        @CsvSource({
            "277, 1",
            "276, 11",
        })
        void buildBatchedPegouts_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = createUTXOs(numberOfUtxos, Coin.COIN, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(numberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @ParameterizedTest
        @CsvSource({
            "276, 1",
            "276, 2",
            "276, 10",
        })
        void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateReleaseTxWithNoChangeOutput(int expectedNumberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = createUTXOs(expectedNumberOfUtxos, Coin.COIN, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests);

            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertEquals(expectedNumberOfUtxos, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasOnlyPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        private void assertReleaseTxInputsHasProperFormatAndBelongsToFederation(BtcTransaction releaseTransaction) {
            for (TransactionInput releaseInput : releaseTransaction.getInputs()) {
                Script inputScriptSig = releaseInput.getScriptSig();
                assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat(inputScriptSig,
                    federation.getRedeemScript());

                assertReleaseInputIsFromFederationUTXOsWallet(releaseInput);
            }
        }
    }

    @Nested
    class P2shErpFederationTests {

        @BeforeEach
        void setup() {
            federation = P2shErpFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();

            federationUTXOs = createUTXOs(10, MINIMUM_PEGIN_TX_VALUE, federationAddress);
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
        void buildBatchedPegouts_whenRSKIP201IsNotActive_shouldCreateReleaseTxWithBtcVersion1() {
            // Arrange
            setUpActivations(PAPYRUS_ACTIVATIONS);
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs1(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();

            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenSinglePegoutRequest_shouldCreateReleaseTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(
                1, MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenMultiplePegoutRequest_shouldCreateReleaseTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(3,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateReleaseTxWithNoChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            federationUTXOs = createUTXOs(expectedNumberOfUTXOs, MINIMUM_PEGOUT_TX_VALUE,
                federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasOnlyPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            federationUTXOs = createUTXOs(1, MINIMUM_PEGOUT_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGIN_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(INSUFFICIENT_MONEY, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @Test
        void buildBatchedPegouts_whenResultChangeOutputWillBeDust_shouldCreateTxWithDustChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            federationUTXOs = createUTXOs(expectedNumberOfUTXOs, utxoAmount,
                federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            assertReleaseTransactionChangeIsDust(releaseTransaction);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenPegoutRequestAmountIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                DUSTY_AMOUNT_SEND_REQUESTED);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @Test
        void buildBatchedPegouts_whenFedSmallUtxosSumEqualsRequestAmount_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            federationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, smallAmount, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @Test
        void buildBatchedPegouts_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            federationUTXOs = createUTXOs(3, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @ParameterizedTest
        @CsvSource({
            "196, 1",
            "195, 16",
        })
        void buildBatchedPegouts_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = createUTXOs(numberOfUtxos, Coin.COIN, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(numberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @ParameterizedTest
        @CsvSource({
            "195, 1",
            "195, 2",
            "195, 15",
        })
        void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateReleaseTxWithNoChangeOutput(int expectedNumberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = createUTXOs(expectedNumberOfUtxos, Coin.COIN, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests);

            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertEquals(expectedNumberOfUtxos, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasOnlyPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        private void assertReleaseTxInputsHasProperFormatAndBelongsToFederation(BtcTransaction releaseTransaction) {
            for (TransactionInput releaseInput : releaseTransaction.getInputs()) {
                Script inputScriptSig = releaseInput.getScriptSig();
                assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat(inputScriptSig,
                    federation.getRedeemScript());
            }
        }
    }

    @Nested
    class P2shP2wshErpFederationTests {

        @BeforeEach
        void setup() {
            federation = P2shP2wshErpFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();

            federationUTXOs = createUTXOs(10, MINIMUM_PEGIN_TX_VALUE, federationAddress);
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

            BtcTransaction pegoutBtcTx = batchedPegoutsResult.btcTx();
            assertTrue(pegoutBtcTx.getOutputs().isEmpty());
            assertTrue(pegoutBtcTx.getInputs().isEmpty());
        }

        @Test
        void buildBatchedPegouts_whenRSKIP201IsNotActive_shouldCreateReleaseTxWithBtcVersion1() {
            // Arrange
            setUpActivations(PAPYRUS_ACTIVATIONS);
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs1(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();

            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());

            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenSinglePegoutRequest_shouldCreateReleaseTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenMultiplePegoutRequest_shouldCreateReleaseTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(3,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(releaseTransaction);
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateReleaseTxWithNoChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            federationUTXOs = createUTXOs(expectedNumberOfUTXOs, MINIMUM_PEGOUT_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasOnlyPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.divide(2);
            federationUTXOs = createUTXOs(1, utxoAmount, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(INSUFFICIENT_MONEY, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @Test
        void buildBatchedPegouts_whenResultChangeOutputWillBeDust_shouldCreateTxWithDustChangeOutput() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            int expectedNumberOfUTXOs = 1;
            federationUTXOs = createUTXOs(expectedNumberOfUTXOs, utxoAmount,
                federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasChangeAndPegoutOutputs(releaseTransaction, pegoutRequests);
            assertReleaseTransactionChangeIsDust(releaseTransaction);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenPegoutRequestAmountIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                DUSTY_AMOUNT_SEND_REQUESTED);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @Test
        void buildBatchedPegouts_whenFedSmallUtxosSumEqualsRequestAmount_shouldCreateReleaseTx() {
            // Arrange
            federationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, smallAmount, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            // Unlike P2SH and Standard Multisig, the fees are way lower,
            // so the builder is able to pay for the fees even with dust UTXOs and
            // create the transaction instead of returning COULD_NOT_ADJUST_DOWNWARDS
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasOnlyPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
        }

        @Test
        void buildBatchedPegouts_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            federationUTXOs = createUTXOs(3, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2,
                MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @ParameterizedTest
        @CsvSource({
            "2438, 1",
            "2437, 3",
        })
        void buildBatchedPegouts_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = createUTXOs(numberOfUtxos, Coin.COIN, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(numberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
            assertNull(batchedPegoutsResult.selectedUTXOs());
        }

        @ParameterizedTest
        @CsvSource({
            "2437, 1",
            "2437, 2",
        })
        void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateReleaseTxWithNoChangeOutput(int expectedNumberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = createUTXOs(expectedNumberOfUtxos, Coin.COIN, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests);

            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(releaseTransaction);
            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertEquals(expectedNumberOfUtxos, releaseInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToFederation(releaseTransaction);
            assertReleaseTxHasOnlyPegoutOutputs(releaseTransaction, pegoutRequests);
            List<UTXO> releaseTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(releaseTransactionUTXOs, releaseInputs);
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

    private List<Entry> createPegoutRequests(int count, Coin amount) {
        List<ReleaseRequestQueue.Entry> pegoutRequests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BigInteger seed = BigInteger.valueOf(i + RECIPIENT_ADDRESS_KEY_OFFSET);
            Address recipientAddress = BtcECKey.fromPrivate(seed).toAddress(BTC_MAINNET_PARAMS);
            Entry pegoutEntry = new Entry(
                recipientAddress,
                amount
            );
            pegoutRequests.add(pegoutEntry);
        }
        return pegoutRequests;
    }

    private void assertReleaseInputIsFromFederationUTXOsWallet(TransactionInput releaseInput) {
        Predicate<UTXO> isUTXOAndReleaseInputFromTheSameOutpoint = utxo ->
            utxo.getHash().equals(releaseInput.getOutpoint().getHash())
                && utxo.getIndex() == releaseInput.getOutpoint().getIndex();
        List<UTXO> foundUtxo = federationUTXOs.stream()
            .filter(isUTXOAndReleaseInputFromTheSameOutpoint).toList();
        assertEquals(1, foundUtxo.size());
    }

    private void assertFederationChangeOutputHasExpectedAmount(BtcTransaction releaseTransaction,
        Coin expectedChangeOutputAmount) {
        List<TransactionOutput> outputsForFederation = getChangeOutputs(releaseTransaction);
        assertEquals(1, outputsForFederation.size());

        TransactionOutput federationChangeOutput = outputsForFederation.get(0);
        assertEquals(expectedChangeOutputAmount, federationChangeOutput.getValue());
    }

    private List<TransactionOutput> getChangeOutputs(BtcTransaction releaseTransaction) {
        return releaseTransaction.getOutputs().stream()
            .filter(this::isFederationOutput)
            .toList();
    }

    private void assertReleaseTransactionChangeIsDust(BtcTransaction releaseTransaction) {
        List<TransactionOutput> changeOutputs = getChangeOutputs(releaseTransaction);
        for(TransactionOutput changeOutput : changeOutputs) {
            boolean isDust = BtcTransaction.MIN_NONDUST_OUTPUT.compareTo(changeOutput.getValue()) > 0;
            assertTrue(isDust);
        }
    }

    private void assertPegoutRequestsAreIncludedInReleaseTx(BtcTransaction releaseTransaction,
        List<Entry> pegoutRequests) {
        List<TransactionOutput> pegoutOutputs = releaseTransaction.getOutputs().stream()
            .filter(this::isPegoutOutput)
            .toList();

        assertEquals(pegoutRequests.size(), pegoutOutputs.size());
        assertPegoutRequestsAreIncludedAsOutputs(pegoutRequests, pegoutOutputs);

        Optional<Coin> changeOutputs = getChangeOutputs(releaseTransaction).stream().map(TransactionOutput::getValue).reduce(Coin::add);
        Coin pegoutOutputsSum = releaseTransaction.getOutputSum().subtract(changeOutputs.orElse(Coin.ZERO));
        Coin releaseTransactionFees = releaseTransaction.getFee();
        Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
        assertEquals(totalPegoutRequestsAmount, releaseTransactionFees.add(pegoutOutputsSum));
    }

    private boolean isPegoutOutput(TransactionOutput pegoutOutput) {
        return !isFederationOutput(pegoutOutput);
    }

    private boolean isFederationOutput(TransactionOutput output) {
        Address recipientAddress = getDestinationAddress(output);
        return recipientAddress.equals(federationAddress);
    }

    private Address getDestinationAddress(TransactionOutput transactionOutput) {
        return transactionOutput.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
    }

    private void assertPegoutRequestsAreIncludedAsOutputs(List<Entry> pegoutRequests,
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
            // pegoutRequest == outputAmount + fees
            boolean outputIsBelowPegoutRequest = pegoutRequest.getAmount().compareTo(outputAmount) >= 0;
            assertTrue(outputIsBelowPegoutRequest);
        }

        for (ArrayDeque<Entry> remaining : byDestination.values()) {
            assertTrue(remaining.isEmpty());
        }
    }

    private void assertReleaseTxHasChangeAndPegoutOutputs(BtcTransaction releaseTransaction,
                                                          List<Entry> pegoutRequests) {
        int expectedNumberOfChangeOutputs = 1;
        int expectedNumberOfOutputs = pegoutRequests.size() + expectedNumberOfChangeOutputs;
        List<TransactionOutput> releaseTransactionOutputs = releaseTransaction.getOutputs();
        assertReleaseTransactionNumberOfOutputs(expectedNumberOfOutputs, releaseTransactionOutputs);
        assertPegoutRequestsAreIncludedInReleaseTx(releaseTransaction, pegoutRequests);

        Coin inputTotalAmount = releaseTransaction.getInputSum();
        Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
        Coin expectedChangeOutputAmount = inputTotalAmount.subtract(totalPegoutRequestsAmount);
        assertFederationChangeOutputHasExpectedAmount(releaseTransaction,
            expectedChangeOutputAmount);
    }

    private static Coin getTotalPegoutRequestsAmount(List<Entry> pegoutRequests) {
        return pegoutRequests.stream().map(Entry::getAmount)
            .reduce(Coin.ZERO, Coin::add);
    }

    private void assertReleaseTxHasOnlyPegoutOutputs(BtcTransaction releaseTransaction,
                                                     List<Entry> pegoutRequests) {
        int expectedNumberOfOutputs = pegoutRequests.size();
        List<TransactionOutput> releaseTransactionOutputs = releaseTransaction.getOutputs();
        assertReleaseTransactionNumberOfOutputs(expectedNumberOfOutputs, releaseTransactionOutputs);
        assertPegoutRequestsAreIncludedInReleaseTx(releaseTransaction, pegoutRequests);
    }

    private void assertReleaseTransactionNumberOfOutputs(int expectedNumberOfOutputs, List<TransactionOutput> releaseTransactionOutputs) {
        int actualNumberOfOutputs = releaseTransactionOutputs.size();
        assertEquals(expectedNumberOfOutputs, actualNumberOfOutputs);
    }
}
