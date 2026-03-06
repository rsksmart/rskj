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
import static org.junit.jupiter.api.Assertions.assertNull;

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

class ReleaseTransactionBuilderBuildAmountToTest {

    private static final int EXPECTED_NUMBER_OF_CHANGE_OUTPUTS = 1;
    private static final ActivationConfig.ForBlock IRIS_ACTIVATIONS = ActivationConfigsForTest.iris300().forBlock(0);
    private static final ActivationConfig.ForBlock PAPYRUS_ACTIVATIONS = ActivationConfigsForTest.papyrus200().forBlock(0);

    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();
    private static final Context BTC_CONTEXT = new Context(BTC_MAINNET_PARAMS);

    private static final Coin MINIMUM_PEGOUT_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPegoutTxValue();
    private static final Coin MINIMUM_PEGIN_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(IRIS_ACTIVATIONS);

    private static final Coin DUSTY_AMOUNT_SEND_REQUESTED = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.minus(Coin.SATOSHI);
    private static final Coin THOUSAND_SATOSHIS = Coin.valueOf(1000);
    private static final int RECIPIENT_ADDRESS_KEY_OFFSET = 2100;

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
        setUpActivations(IRIS_ACTIVATIONS);
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

        @Test
        void buildAmountTo_whenRSKIP201IsNotActive_shouldCreateReleaseTxWithBtcVersion1() {
            setUpActivations(PAPYRUS_ACTIVATIONS);
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, MINIMUM_PEGOUT_TX_VALUE);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs1(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            int numberOfUtxosExpected = 1;
            assertEquals(numberOfUtxosExpected, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenSingleUtxoCanCoverAmount_shouldCreateReleaseTx() {
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, MINIMUM_PEGOUT_TX_VALUE);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            int numberOfUtxosExpected = 1;
            assertEquals(numberOfUtxosExpected, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenMultipleUtxosCanCoverAmount_shouldCreateReleaseTx() {
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            int numberOfUtxosToCoverAmountRequested = 2;
            Coin amountToSend = MINIMUM_PEGOUT_TX_VALUE.add(THOUSAND_SATOSHIS);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            assertEquals(numberOfUtxosToCoverAmountRequested, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, amountToSend);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenWalletHasExactFunds_shouldCreateReleaseTxWithNoChangeOutput() {
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, MINIMUM_PEGOUT_TX_VALUE);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            int numberOfUtxosExpected = 1;
            assertEquals(numberOfUtxosExpected, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutToTxHasOnlyPegoutOutput(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenInsufficientFunds_shouldReturnInsufficientMoney() {
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountExceedingFederationBalance = MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountExceedingFederationBalance);

            assertBuildResultResponseCode(INSUFFICIENT_MONEY, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; real pegouts must be at least
         * {@link BridgeConstants#getMinimumPegoutTxValue()}, but we use it to exercise the
         * DUSTY_SEND_REQUESTED path.
         */
        @Test
        void buildAmountTo_whenAmountIsTooSmall_shouldReturnDustySendRequested() {
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, DUSTY_AMOUNT_SEND_REQUESTED);

            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards() {
            // Spending an input with a p2sh script costs more than MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.
            // Therefore, if the federation has only UTXOs with that minimum non-dust value,
            // it won't be possible to adjust downwards the pegout amount to avoid creating a dust output.
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountToSend = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.multiply(numberOfUtxos);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
            int numberOfUtxos = 277;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountToSend = wallet.getBalance().subtract(THOUSAND_SATOSHIS);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenTxIsAlmostExceedingMaxTxSize_shouldCreateReleaseTx() {
            int numberOfUtxos = 276;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountToSend = wallet.getBalance().subtract(THOUSAND_SATOSHIS);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            assertEquals(numberOfUtxos, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, amountToSend);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
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
        void buildAmountTo_whenRSKIP201IsNotActive_shouldCreateReleaseTxWithBtcVersion1() {
            setUpActivations(PAPYRUS_ACTIVATIONS);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, MINIMUM_PEGOUT_TX_VALUE);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs1(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            int numberOfUtxosExpected = 1;
            assertEquals(numberOfUtxosExpected, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenSingleUtxoCanCoverAmount_shouldCreateReleaseTx() {
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, MINIMUM_PEGOUT_TX_VALUE);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            int numberOfUtxosExpected = 1;
            assertEquals(numberOfUtxosExpected, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );

            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenMultipleUtxosCanCoverAmount_shouldCreateReleaseTx() {
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            int numberOfUtxosToCoverAmountRequested = 2;
            Coin amountToSend = MINIMUM_PEGOUT_TX_VALUE.add(THOUSAND_SATOSHIS);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            assertEquals(numberOfUtxosToCoverAmountRequested, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, amountToSend);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenWalletHasExactFunds_shouldCreateReleaseTxWithNoChangeOutput() {
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, MINIMUM_PEGOUT_TX_VALUE);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            int numberOfUtxosExpected = 1;
            assertEquals(numberOfUtxosExpected, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutToTxHasOnlyPegoutOutput(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenInsufficientFunds_shouldReturnInsufficientMoney() {
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountExceedingFederationBalance = MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountExceedingFederationBalance);

            assertBuildResultResponseCode(INSUFFICIENT_MONEY, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; real pegouts must be at least
         * {@link BridgeConstants#getMinimumPegoutTxValue()}, but we use it to exercise the
         * DUSTY_SEND_REQUESTED path.
         */
        @Test
        void buildAmountTo_whenAmountIsTooSmall_shouldReturnDustySendRequested() {
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, DUSTY_AMOUNT_SEND_REQUESTED);

            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards() {
            // Spending an input with a p2sh script costs more than MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.
            // Therefore, if the federation has only UTXOs with that minimum non-dust value,
            // it won't be possible to adjust downwards the pegout amount to avoid creating a dust output.
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountToSend = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.multiply(numberOfUtxos);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
            int numberOfUtxos = 196;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountToSend = wallet.getBalance().subtract(THOUSAND_SATOSHIS);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenTxIsAlmostExceedingMaxTxSize_shouldCreateReleaseTx() {
            int numberOfUtxos = 195;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountToSend = wallet.getBalance().subtract(THOUSAND_SATOSHIS);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            assertEquals(numberOfUtxos, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, amountToSend);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
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
        void buildAmountTo_whenRSKIP201IsNotActive_shouldCreateReleaseTxWithBtcVersion1() {
            setUpActivations(PAPYRUS_ACTIVATIONS);
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, MINIMUM_PEGOUT_TX_VALUE);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs1(pegoutTransaction);
            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            int numberOfUtxosExpected = 1;
            assertEquals(numberOfUtxosExpected, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenSingleUtxoCanCoverAmount_shouldCreateReleaseTx() {
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, MINIMUM_PEGOUT_TX_VALUE);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            int numberOfUtxosExpected = 1;
            assertEquals(numberOfUtxosExpected, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenMultipleUtxosCanCoverAmount_shouldCreateReleaseTx() {
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            int numberOfUtxosToCoverAmountRequested = 2;
            Coin amountToSend = MINIMUM_PEGOUT_TX_VALUE.add(THOUSAND_SATOSHIS);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            assertEquals(numberOfUtxosToCoverAmountRequested, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, amountToSend);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenWalletHasExactFunds_shouldCreateReleaseTxWithNoChangeOutput() {
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, MINIMUM_PEGOUT_TX_VALUE);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            int numberOfUtxosExpected = 1;
            assertEquals(numberOfUtxosExpected, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutToTxHasOnlyPegoutOutput(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }

        @Test
        void buildAmountTo_whenInsufficientFunds_shouldReturnInsufficientMoney() {
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountExceedingFederationBalance = MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountExceedingFederationBalance);

            assertBuildResultResponseCode(INSUFFICIENT_MONEY, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; real pegouts must be at least
         * {@link BridgeConstants#getMinimumPegoutTxValue()}, but we use it to exercise the
         * DUSTY_SEND_REQUESTED path.
         */
        @Test
        void buildAmountTo_whenAmountIsTooSmall_shouldReturnDustySendRequested() {
            ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
            Address recipientAddress = createRecipientAddress();

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, DUSTY_AMOUNT_SEND_REQUESTED);

            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards() {
            // Spending an input with a p2sh-p2wsh script costs more than MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.
            // Therefore, if the federation has only UTXOs with that minimum non-dust value,
            // it won't be possible to adjust downwards the pegout amount to avoid creating a dust output.
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountToSend = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.multiply(numberOfUtxos);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
            int numberOfUtxos = 2438;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountToSend = wallet.getBalance().subtract(THOUSAND_SATOSHIS);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, amountToResult);
            assertNull(amountToResult.btcTx());
            assertNull(amountToResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenTxIsAlmostExceedingMaxTxSize_shouldCreateReleaseTx() {
            int numberOfUtxos = 2437;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
            Address recipientAddress = createRecipientAddress();
            Coin amountToSend = wallet.getBalance().subtract(THOUSAND_SATOSHIS);

            BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(recipientAddress, amountToSend);

            assertBuildResultResponseCode(SUCCESS, amountToResult);
            BtcTransaction pegoutTransaction = amountToResult.btcTx();
            assertBtcTxVersionIs2(pegoutTransaction);

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            assertEquals(numberOfUtxos, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            assertPegoutTxHasPegoutAndChangeOutputs(pegoutTransaction, amountToSend);
            assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
        }
    }

    private void setUpActivations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
    }

    private void setUpFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    private Address createRecipientAddress() {
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

    private void assertPegoutTxHasPegoutAndChangeOutputs(
        BtcTransaction pegoutTransaction,
        Coin requestedAmount
    ) {
        int expectedNumberOfPegoutOutputs = 1;
        int expectedNumberOfOutputs = expectedNumberOfPegoutOutputs + EXPECTED_NUMBER_OF_CHANGE_OUTPUTS;
        assertEquals(expectedNumberOfOutputs, pegoutTransaction.getOutputs().size());
        List<TransactionOutput> pegoutTransactionChangeOutputs = getChangeOutputs(pegoutTransaction);
        assertEquals(EXPECTED_NUMBER_OF_CHANGE_OUTPUTS, pegoutTransactionChangeOutputs.size());
        assertReleaseTxHasChangeAndPegoutsAmountWithFeesProperly(pegoutTransaction,
            pegoutTransactionChangeOutputs, requestedAmount
        );
        assertDestinationAddress(pegoutTransactionChangeOutputs, federationAddress);
    }

    private void assertPegoutToTxHasOnlyPegoutOutput(
        BtcTransaction pegoutTransaction,
        Coin requestedAmount
    ) {
        int expectedNumberOfPegoutOutputs = 1;
        assertEquals(expectedNumberOfPegoutOutputs, pegoutTransaction.getOutputs().size());
        List<TransactionOutput> changeOutputs = getChangeOutputs(pegoutTransaction);
        int expectedNumberOfChangeOutputs = 0;
        assertEquals(expectedNumberOfChangeOutputs, changeOutputs.size());
        assertReleaseTxWithNoChangeHasPegoutsAmountWithFeesProperly(pegoutTransaction, requestedAmount);
    }

    private List<TransactionOutput> getChangeOutputs(BtcTransaction pegoutTransaction) {
        return pegoutTransaction.getOutputs().stream()
            .filter(this::isFederationOutput)
            .toList();
    }

    private boolean isFederationOutput(TransactionOutput output) {
        Address destination = output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
        return destination.equals(federationAddress);
    }
}
