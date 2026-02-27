package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_1;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.SUCCESS;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.assertP2shP2wshScriptWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
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
import co.rsk.peg.ReleaseTransactionBuilder.Response;
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
    protected Script federationOutputScript;
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
            federationOutputScript = federation.getP2SHScript();
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federation.getP2SHScript())
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

            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsTransactionInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
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
            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsTransactionInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
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
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateBatchedPegoutsTxWithNoChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federation.getP2SHScript())
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .buildMany(expectedNumberOfUTXOs, i -> createHash(i + 1));

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
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasOnlyPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            int numberOfUtxos = 1;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federation.getP2SHScript())
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
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
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federation.getP2SHScript())
                .withValue(utxoAmount)
                .buildMany(expectedNumberOfUTXOs, i -> createHash(i + 1));
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
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            assertBatchedPegoutsTransactionChangeIsDust(batchedPegoutsTransaction);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
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
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federation.getP2SHScript())
                .withValue(smallAmount)
                .buildMany(LARGE_NUMBER_OF_UTXOS, i -> createHash(i + 1));
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
            int numberOfUtxos = 3;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federation.getP2SHScript())
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
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federation.getP2SHScript())
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
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
        void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateBatchedPegoutsTxWithNoChangeOutput(
            int expectedNumberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federation.getP2SHScript())
                .withValue(Coin.COIN)
                .buildMany(expectedNumberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests);

            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUtxos, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasOnlyPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
        }

        private void assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(BtcTransaction batchedPegoutsTransaction) {
            for (TransactionInput input : batchedPegoutsTransaction.getInputs()) {
                Script inputScriptSig = input.getScriptSig();
                assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat(inputScriptSig,
                    federation.getRedeemScript());

                assertInputIsFromFederationUTXOsWallet(input);
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
            federationOutputScript = federation.getP2SHScript();
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
        void buildBatchedPegouts_whenRSKIP201IsNotActive_shouldCreateBatchedPegoutsTxWithBtcVersion1() {
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

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs1(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();

            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
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
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
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
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateBatchedPegoutsTxWithNoChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .buildMany(expectedNumberOfUTXOs, i -> createHash(i + 1));
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
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasOnlyPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            int numberOfUtxos = 1;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
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
        void buildBatchedPegouts_whenResultChangeOutputWillBeDust_shouldCreateBatchedPegoutsTxWithDustChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(utxoAmount)
                .buildMany(expectedNumberOfUTXOs, i -> createHash(i + 1));

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
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            assertBatchedPegoutsTransactionChangeIsDust(batchedPegoutsTransaction);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
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
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(smallAmount)
                .buildMany(LARGE_NUMBER_OF_UTXOS, i -> createHash(i + 1));
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
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
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
        void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateBatchedPegoutsTxWithNoChangeOutput(int expectedNumberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(expectedNumberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests);

            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUtxos, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasOnlyPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
        }

        private void assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(BtcTransaction batchedPegoutsTransaction) {
            for (TransactionInput input : batchedPegoutsTransaction.getInputs()) {
                Script inputScriptSig = input.getScriptSig();
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
            federationOutputScript = federation.getP2SHScript();
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
        void buildBatchedPegouts_whenRSKIP201IsNotActive_shouldCreateBatchedPegoutsTxWithBtcVersion1() {
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

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs1(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();

            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsInputs.size());

            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
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
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
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
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateBatchedPegoutsTxWithNoChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .buildMany(expectedNumberOfUTXOs, i -> createHash(i + 1));
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
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasOnlyPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            int numberOfUtxos = 1;
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.divide(2);
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(utxoAmount)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

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
        void buildBatchedPegouts_whenResultChangeOutputWillBeDust_shouldCreateBatchedPegoutsTxWithDustChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(utxoAmount)
                .buildMany(expectedNumberOfUTXOs, i -> createHash(i + 1));
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
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUTXOs, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasChangeAndPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            assertBatchedPegoutsTransactionChangeIsDust(batchedPegoutsTransaction);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
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
        void buildBatchedPegouts_whenFedSmallUtxosSumEqualsRequestAmount_shouldCreateBatchedPegoutsTx() {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(smallAmount)
                .buildMany(LARGE_NUMBER_OF_UTXOS, i -> createHash(i + 1));
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

            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasOnlyPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
        }

        @Test
        void buildBatchedPegouts_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
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
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
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
        void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateBatchedPegoutsTxWithNoChangeOutput(
            int expectedNumberOfUtxos, int numberOfPegoutRequests) {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(expectedNumberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

            Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
            Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests);

            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
            BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(batchedPegoutsTransaction);
            List<TransactionInput> batchedPegoutsInputs = batchedPegoutsTransaction.getInputs();
            assertEquals(expectedNumberOfUtxos, batchedPegoutsInputs.size());
            assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(batchedPegoutsTransaction);
            assertBatchedPegoutsTxHasOnlyPegoutOutputs(batchedPegoutsTransaction, pegoutRequests);
            List<UTXO> batchedPegoutsTransactionUTXOs = batchedPegoutsResult.selectedUTXOs();
            assertSelectedUtxosBelongToTheInputs(batchedPegoutsTransactionUTXOs, batchedPegoutsInputs);
        }

        private void assertBatchedPegoutsTxInputsHasProperFormatAndBelongsToFederation(BtcTransaction batchedPegoutsTransaction) {
            List<TransactionInput> batchedPegoutsTransactionInputs = batchedPegoutsTransaction.getInputs();
            for (TransactionInput input : batchedPegoutsTransactionInputs) {
                int inputIndex = batchedPegoutsTransactionInputs.indexOf(input);
                TransactionWitness witness = batchedPegoutsTransaction.getWitness(inputIndex);
                assertP2shP2wshScriptWithoutSignaturesHasProperFormat(witness, federation.getRedeemScript());
            }
        }
    }

    private static void assertSelectedUtxosBelongToTheInputs(List<UTXO> batchedPegoutsTransactionUTXOs,
                                                             List<TransactionInput> batchedPegoutsTransactionInputs) {
        for (UTXO utxo : batchedPegoutsTransactionUTXOs) {
            List<TransactionInput> matchingInputs = batchedPegoutsTransactionInputs.stream().
                filter(input -> input.getOutpoint().getHash().equals(utxo.getHash())
                    && input.getOutpoint().getIndex() == utxo.getIndex()).toList();
            assertEquals(1, matchingInputs.size());
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

    private void assertBuildResultResponseCode(ReleaseTransactionBuilder.Response expectedResponseCode, BuildResult batchedPegoutsResult) {
        Response actualResponseCode = batchedPegoutsResult.responseCode();
        assertEquals(expectedResponseCode, actualResponseCode);
    }

    private void assertBtcTxVersionIs1(BtcTransaction batchedPegoutsTransaction) {
        assertEquals(BTC_TX_VERSION_1, batchedPegoutsTransaction.getVersion());
    }

    private void assertBtcTxVersionIs2(BtcTransaction batchedPegoutsTransaction) {
        assertEquals(BTC_TX_VERSION_2, batchedPegoutsTransaction.getVersion());
    }

    private void assertInputIsFromFederationUTXOsWallet(TransactionInput input) {
        Predicate<UTXO> isUTXOAndReleaseInputFromTheSameOutpoint = utxo ->
            utxo.getHash().equals(input.getOutpoint().getHash())
                && utxo.getIndex() == input.getOutpoint().getIndex();
        List<UTXO> foundUtxo = federationUTXOs.stream()
            .filter(isUTXOAndReleaseInputFromTheSameOutpoint).toList();
        assertEquals(1, foundUtxo.size());
    }

    private void assertFederationChangeOutputHasExpectedAmount(BtcTransaction batchedPegoutsTransaction,
                                                               Coin expectedChangeOutputAmount) {
        List<TransactionOutput> outputsForFederation = getChangeOutputs(batchedPegoutsTransaction);
        assertEquals(1, outputsForFederation.size());

        TransactionOutput federationChangeOutput = outputsForFederation.get(0);
        assertEquals(expectedChangeOutputAmount, federationChangeOutput.getValue());
    }

    private List<TransactionOutput> getChangeOutputs(BtcTransaction batchedPegoutsTransaction) {
        return batchedPegoutsTransaction.getOutputs().stream()
            .filter(this::isFederationOutput)
            .toList();
    }

    private void assertBatchedPegoutsTransactionChangeIsDust(BtcTransaction batchedPegoutsTransaction) {
        List<TransactionOutput> changeOutputs = getChangeOutputs(batchedPegoutsTransaction);
        for(TransactionOutput changeOutput : changeOutputs) {
            boolean isDust = BtcTransaction.MIN_NONDUST_OUTPUT.compareTo(changeOutput.getValue()) > 0;
            assertTrue(isDust);
        }
    }

    private void assertPegoutRequestsAreIncludedInBatchedPegoutsTx(BtcTransaction batchedPegoutsTransaction,
                                                                   List<Entry> pegoutRequests) {
        List<TransactionOutput> pegoutOutputs = batchedPegoutsTransaction.getOutputs().stream()
            .filter(this::isPegoutOutput)
            .toList();

        assertEquals(pegoutRequests.size(), pegoutOutputs.size());
        assertPegoutRequestsAreIncludedAsOutputs(pegoutRequests, pegoutOutputs);

        Optional<Coin> changeOutputs = getChangeOutputs(batchedPegoutsTransaction).stream().map(TransactionOutput::getValue).reduce(Coin::add);
        Coin pegoutOutputsSum = batchedPegoutsTransaction.getOutputSum().subtract(changeOutputs.orElse(Coin.ZERO));
        Coin batchedPegoutsTransactionFees = batchedPegoutsTransaction.getFee();
        Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
        assertEquals(totalPegoutRequestsAmount, batchedPegoutsTransactionFees.add(pegoutOutputsSum));
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

    private void assertBatchedPegoutsTxHasChangeAndPegoutOutputs(BtcTransaction batchedPegoutsTransaction,
                                                                 List<Entry> pegoutRequests) {
        int expectedNumberOfChangeOutputs = 1;
        int expectedNumberOfOutputs = pegoutRequests.size() + expectedNumberOfChangeOutputs;
        List<TransactionOutput> batchedPegoutsTransactionOutputs = batchedPegoutsTransaction.getOutputs();
        assertBatchedPegoutsTransactionNumberOfOutputs(expectedNumberOfOutputs, batchedPegoutsTransactionOutputs);
        assertPegoutRequestsAreIncludedInBatchedPegoutsTx(batchedPegoutsTransaction, pegoutRequests);

        Coin inputTotalAmount = batchedPegoutsTransaction.getInputSum();
        Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
        Coin expectedChangeOutputAmount = inputTotalAmount.subtract(totalPegoutRequestsAmount);
        assertFederationChangeOutputHasExpectedAmount(batchedPegoutsTransaction,
            expectedChangeOutputAmount);
    }

    private static Coin getTotalPegoutRequestsAmount(List<Entry> pegoutRequests) {
        return pegoutRequests.stream().map(Entry::getAmount)
            .reduce(Coin.ZERO, Coin::add);
    }

    private void assertBatchedPegoutsTxHasOnlyPegoutOutputs(BtcTransaction batchedPegoutsTransaction,
                                                            List<Entry> pegoutRequests) {
        int expectedNumberOfOutputs = pegoutRequests.size();
        List<TransactionOutput> batchedPegoutsTransactionOutputs = batchedPegoutsTransaction.getOutputs();
        assertBatchedPegoutsTransactionNumberOfOutputs(expectedNumberOfOutputs, batchedPegoutsTransactionOutputs);
        assertPegoutRequestsAreIncludedInBatchedPegoutsTx(batchedPegoutsTransaction, pegoutRequests);
    }

    private void assertBatchedPegoutsTransactionNumberOfOutputs(int expectedNumberOfOutputs,
                                                                List<TransactionOutput> batchedPegoutsTransactionOutputs) {
        int actualNumberOfOutputs = batchedPegoutsTransactionOutputs.size();
        assertEquals(expectedNumberOfOutputs, actualNumberOfOutputs);
    }
}
