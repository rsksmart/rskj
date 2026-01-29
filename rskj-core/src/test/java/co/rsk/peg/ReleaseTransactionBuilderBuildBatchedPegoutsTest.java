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
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createUTXOs;
import static org.ethereum.vm.PrecompiledContracts.BRIDGE_ADDR;
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
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    private static final int LARGE_NUMBER_OF_UTXOS = 200;
    private static final int LARGE_NUMBER_OF_PEGOUT_REQUESTS = 50;

    protected Federation federation;
    protected int federationFormatVersion;
    protected Address federationAddress;
    protected Wallet wallet;

    private ActivationConfig.ForBlock activations;
    private Coin feePerKb;
    private Coin dustAmount;

    @BeforeEach
    void setUp() {
        setUpActivations(ALL_ACTIVATIONS);
        setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
        dustAmount = feePerKb.div(2);
    }

    @Nested
    class StandardMultiSigFederationTests {

        @BeforeEach
        void setup() {
            federation = StandardMultiSigFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();

            List<UTXO> federationUTXOs = createUTXOs(10, MINIMUM_PEGIN_TX_VALUE, federationAddress);
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
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
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
            int expectedNumberOfUTXOs = 1;
            assertEquals(expectedNumberOfUTXOs, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
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
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateReleaseTxWithNoChangeOutput() {
            int expectedNumberOfUTXOs = 1;
            List<UTXO> federationUTXOs = createUTXOs(expectedNumberOfUTXOs, MINIMUM_PEGOUT_TX_VALUE,
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
            assertEquals(expectedNumberOfUTXOs, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasOnlyUserOutputs(releaseTransaction, pegoutRequests);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            List<UTXO> federationUTXOs = createUTXOs(1, MINIMUM_PEGOUT_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2,
                MINIMUM_PEGIN_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(INSUFFICIENT_MONEY, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
        }

        @Test
        void buildBatchedPegouts_whenResultChangeOutputWillBeDust_shouldCreateTxWithDustChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            List<UTXO> federationUTXOs = createUTXOs(expectedNumberOfUTXOs, utxoAmount,
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
            assertEquals(expectedNumberOfUTXOs, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
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
        }

        @Test
        void buildBatchedPegouts_whenUtxosAreDustButExactToPay_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            List<UTXO> federationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, dustAmount,
                federationAddress);
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
        }

        @Test
        void buildBatchedPegouts_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            List<UTXO> federationUTXOs = createUTXOs(3, MINIMUM_PEGIN_TX_VALUE, federationAddress);
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
        }

        @Test
        void buildBatchedPegouts_when200UtxosToPay50PegoutRequests_shouldCreateReleaseTx() {
            // Arrange
            List<UTXO> federationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, MINIMUM_PEGIN_TX_VALUE,
                federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            Coin pegoutRequestAmount = MINIMUM_PEGIN_TX_VALUE.multiply(4);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(
                LARGE_NUMBER_OF_PEGOUT_REQUESTS, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);

            List<TransactionInput> releaseInputs = releaseTransaction.getInputs();
            assertEquals(LARGE_NUMBER_OF_UTXOS, releaseInputs.size());
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasOnlyUserOutputs(releaseTransaction, pegoutRequests);
        }

        private void assertReleaseTxInputsHasProperFormat(BtcTransaction releaseTransaction) {
            for (TransactionInput releaseInput : releaseTransaction.getInputs()) {
                Script inputScriptSig = releaseInput.getScriptSig();
                assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat(inputScriptSig,
                    federation.getRedeemScript());
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

            List<UTXO> federationUTXOs = createUTXOs(10, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            setUpWallet(federationUTXOs);
        }

        @Test
        void buildBatchedPegouts_whenNoPegoutRequests_shouldThrowIllegalStateException() {
            // Arrange
            List<UTXO> federationUTXOs = createUTXOs(10, MINIMUM_PEGIN_TX_VALUE, federationAddress);
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
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
        }

        @Test
        void buildBatchedPegouts_whenSinglePegoutRequest_shouldCreateReleaseTx() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            int expectedNumberOfUTXOs = 1;
            int numberOfPegoutRequests = 1;
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(
                numberOfPegoutRequests, MINIMUM_PEGOUT_TX_VALUE);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();

            assertBtcTxVersionIs2(releaseTransaction);
            assertEquals(expectedNumberOfUTXOs, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
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
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateReleaseTxWithNoChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            List<UTXO> federationUTXOs = createUTXOs(expectedNumberOfUTXOs, MINIMUM_PEGOUT_TX_VALUE,
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
            assertEquals(expectedNumberOfUTXOs, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasOnlyUserOutputs(releaseTransaction, pegoutRequests);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            List<UTXO> federationUTXOs = createUTXOs(1, MINIMUM_PEGOUT_TX_VALUE, federationAddress);
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
        }

        @Test
        void buildBatchedPegouts_whenResultChangeOutputWillBeDust_shouldCreateTxWithDustChangeOutput() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            int expectedNumberOfUTXOs = 1;
            List<UTXO> federationUTXOs = createUTXOs(expectedNumberOfUTXOs, utxoAmount,
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
            assertEquals(expectedNumberOfUTXOs, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
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
        }

        @Test
        void buildBatchedPegouts_whenUtxosAreDustButExactToPay_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            List<UTXO> federationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, dustAmount,
                federationAddress);
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
        }

        @Test
        void buildBatchedPegouts_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            List<UTXO> federationUTXOs = createUTXOs(3, MINIMUM_PEGIN_TX_VALUE, federationAddress);
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
        }

        @Test
        void buildBatchedPegouts_when200UtxosToPay50PegoutRequests_shouldReturnExceedMaxTransactionSize() {
            // Arrange
            List<UTXO> federationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, MINIMUM_PEGIN_TX_VALUE,
                federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            Coin pegoutRequestAmount = MINIMUM_PEGIN_TX_VALUE.multiply(4);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(
                LARGE_NUMBER_OF_PEGOUT_REQUESTS, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, batchedPegoutsResult);
            assertNull(batchedPegoutsResult.btcTx());
        }

        private void assertReleaseTxInputsHasProperFormat(BtcTransaction releaseTransaction) {
            for (TransactionInput releaseInput : releaseTransaction.getInputs()) {
                Script inputScriptSig = releaseInput.getScriptSig();
                assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat(inputScriptSig,
                    federation.getRedeemScript());
            }
        }
    }

    @Nested
    class P2wshErpFederationTests {

        @BeforeEach
        void setup() {
            federation = P2shP2wshErpFederationBuilder.builder()
                .build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();

            List<UTXO> federationUTXOs = createUTXOs(10, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            setUpWallet(federationUTXOs);
        }

        @Test
        void buildBatchedPegouts_whenNoPegoutRequests_returnsAnEmptyTransaction() {
            // Arrange
            List<UTXO> federationUTXOs = createUTXOs(10, MINIMUM_PEGIN_TX_VALUE, federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);

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
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
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
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
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
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
        }

        @Test
        void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateReleaseTxWithNoChangeOutput() {
            List<UTXO> federationUTXOs = createUTXOs(1, MINIMUM_PEGOUT_TX_VALUE, federationAddress);
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
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasOnlyUserOutputs(releaseTransaction, pegoutRequests);
        }

        @Test
        void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.divide(2);
            List<UTXO> federationUTXOs = createUTXOs(1, utxoAmount, federationAddress);
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
        }

        @Test
        void buildBatchedPegouts_whenResultChangeOutputWillBeDust_shouldCreateTxWithDustChangeOutput() {
            // Arrange
            int expectedNumberOfUTXOs = 1;
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUSTY_AMOUNT_SEND_REQUESTED);
            List<UTXO> federationUTXOs = createUTXOs(expectedNumberOfUTXOs, utxoAmount,
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
            assertEquals(expectedNumberOfUTXOs, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasChangeAndUserOutputs(releaseTransaction, pegoutRequests);
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
        }

        @Test
        void buildBatchedPegouts_whenUtxosAreDustButExactToPay_shouldCreateReleaseTx() {
            // Arrange
            List<UTXO> federationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, dustAmount,
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
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasOnlyUserOutputs(releaseTransaction, pegoutRequests);
        }

        @Test
        void buildBatchedPegouts_whenEstimatedFeeIsTooHigh_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            List<UTXO> federationUTXOs = createUTXOs(3, MINIMUM_PEGIN_TX_VALUE, federationAddress);
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
        }

        @Test
        void buildBatchedPegouts_when200UtxosToPay50PegoutRequests_shouldCreateReleaseTx() {
            // Arrange
            List<UTXO> federationUTXOs = createUTXOs(LARGE_NUMBER_OF_UTXOS, MINIMUM_PEGIN_TX_VALUE,
                federationAddress);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                federationUTXOs);
            Coin pegoutRequestAmount = MINIMUM_PEGIN_TX_VALUE.multiply(4);
            List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(
                LARGE_NUMBER_OF_PEGOUT_REQUESTS, pegoutRequestAmount);

            // Act
            BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                pegoutRequests);

            // Assert
            assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

            BtcTransaction releaseTransaction = batchedPegoutsResult.btcTx();
            assertBtcTxVersionIs2(releaseTransaction);
            assertEquals(LARGE_NUMBER_OF_UTXOS, releaseTransaction.getInputs().size());
            assertReleaseTxInputsHasProperFormat(releaseTransaction);
            assertReleaseTxHasOnlyUserOutputs(releaseTransaction, pegoutRequests);
        }

        private void assertReleaseTxInputsHasProperFormat(BtcTransaction releaseTransaction) {
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
            BRIDGE_ADDR,
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

    private void assertBtcTxVersionIs1(BtcTransaction releaseTransaction) {
        assertEquals(BTC_TX_VERSION_1, releaseTransaction.getVersion());
    }

    private void assertBtcTxVersionIs2(BtcTransaction releaseTransaction) {
        assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
    }

    private void assertFederationChangeOutputHasExpectedAmount(BtcTransaction releaseTransaction,
        Coin expectedChangeOutputAmount) {
        List<TransactionOutput> outputsForFederation = releaseTransaction.getOutputs().stream()
            .filter(this::isRecipientAddressForFederation)
            .toList();
        assertEquals(1, outputsForFederation.size());

        TransactionOutput federationChangeOutput = outputsForFederation.get(0);
        assertEquals(expectedChangeOutputAmount, federationChangeOutput.getValue());
    }

    private void assertPegoutRequestsAreIncludedInReleaseTx(BtcTransaction releaseTransaction,
        List<Entry> pegoutRequests) {
        List<TransactionOutput> onlyUserOutputs = releaseTransaction.getOutputs().stream().filter(
            this::isRecipientAddressForAnUser
        ).toList();

        assertEquals(pegoutRequests.size(), onlyUserOutputs.size());
        for (Entry pegoutRequest : pegoutRequests) {
            assertPegoutRequestIsIncludedInUserOutputs(pegoutRequest, onlyUserOutputs);
        }
    }

    private boolean isRecipientAddressForAnUser(TransactionOutput userOutput) {
        return !isRecipientAddressForFederation(userOutput);
    }

    private boolean isRecipientAddressForFederation(TransactionOutput output) {
        Address recipientAddress = getDestinationAddress(output);
        return recipientAddress.equals(federationAddress);
    }

    private Address getDestinationAddress(TransactionOutput transactionOutput) {
        return transactionOutput.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
    }

    private void assertPegoutRequestIsIncludedInUserOutputs(Entry pegoutRequest,
        List<TransactionOutput> userOutputs) {
        Optional<TransactionOutput> userOutput = userOutputs.stream().filter(
            output -> getDestinationAddress(output)
                .equals(pegoutRequest.getDestination())
        ).findFirst();
        assertTrue(userOutput.isPresent(),
            String.format("No matching output found for pegout request to address %s",
                pegoutRequest.getDestination().toString()
            )
        );
        assertTrue(pegoutRequest.getAmount().compareTo(userOutput.get().getValue()) > -1,
            String.format("Output amount %s is less than requested amount %s for address %s",
                userOutput.get().getValue().toString(),
                pegoutRequest.getAmount().toString(),
                pegoutRequest.getDestination().toString()
            )
        );
    }

    private void assertReleaseTxHasChangeAndUserOutputs(BtcTransaction releaseTransaction,
        List<Entry> pegoutRequests) {
        int expectedNumberOfOutputs = pegoutRequests.size() + 1;
        assertReleaseTransactionNumberOfOutputs(releaseTransaction, expectedNumberOfOutputs);
        assertPegoutRequestsAreIncludedInReleaseTx(releaseTransaction, pegoutRequests);

        Coin totalPegoutRequestsAmount = pegoutRequests.stream().map(Entry::getAmount)
            .reduce(Coin.ZERO, Coin::add);
        Coin inputTotalAmount = releaseTransaction.getInputSum();

        Coin expectedChangeOutputAmount = inputTotalAmount.subtract(totalPegoutRequestsAmount);
        assertFederationChangeOutputHasExpectedAmount(releaseTransaction,
            expectedChangeOutputAmount);
    }

    private void assertReleaseTxHasOnlyUserOutputs(BtcTransaction releaseTransaction,
        List<Entry> pegoutRequests) {
        int expectedNumberOfOutputs = pegoutRequests.size();
        assertReleaseTransactionNumberOfOutputs(releaseTransaction, expectedNumberOfOutputs);
        assertPegoutRequestsAreIncludedInReleaseTx(releaseTransaction, pegoutRequests);
    }

    private void assertReleaseTransactionNumberOfOutputs(BtcTransaction releaseTransaction,
        int expectedNumberOfOutputs) {
        int actualNumberOfOutputs = releaseTransaction.getOutputs().size();
        assertEquals(expectedNumberOfOutputs, actualNumberOfOutputs);
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

    private void assertBuildResultResponseCode(ReleaseTransactionBuilder.Response expectedResponseCode,
        BuildResult batchedPegoutsResult) {
        assertEquals(expectedResponseCode, batchedPegoutsResult.responseCode());
    }
}
