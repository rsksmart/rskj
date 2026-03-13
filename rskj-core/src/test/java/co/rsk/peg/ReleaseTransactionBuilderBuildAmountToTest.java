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
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import co.rsk.test.builders.UTXOBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReleaseTransactionBuilderBuildAmountToTest {

    private static final int EXPECTED_NUMBER_OF_CHANGE_OUTPUTS = 1;
    private static final ActivationConfig.ForBlock IRIS_ACTIVATIONS = ActivationConfigsForTest.iris300().forBlock(0);
    private static final ActivationConfig.ForBlock PAPYRUS_ACTIVATIONS = ActivationConfigsForTest.papyrus200().forBlock(0);

    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();

    private static final Coin MINIMUM_PEGOUT_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPegoutTxValue();
    private static final Coin MINIMUM_PEGIN_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(IRIS_ACTIVATIONS);

    private static final Coin HIGH_FEE_PER_KB = Coin.valueOf(1_000_000);
    private static final Coin DUST_VALUE = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.minus(Coin.SATOSHI);
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
    void setup() {
        setUpActivations(IRIS_ACTIVATIONS);
        setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
        federation = StandardMultiSigFederationBuilder.builder().build();
        federationFormatVersion = federation.getFormatVersion();
        federationAddress = federation.getAddress();
        federationOutputScript = federation.getP2SHScript();
        federationRedeemScript = federation.getRedeemScript();
    }

    @Test
    void buildAmountTo_whenFedHasNoUTXOs_shouldReturnInsufficientMoney() {
        // Arrange
        federationUTXOs = new ArrayList<>();
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

        // Assert
        assertBuildResultResponseCode(INSUFFICIENT_MONEY, amountToResult);
        assertNull(amountToResult.btcTx());
        assertNull(amountToResult.selectedUTXOs());
    }

    @Test
    void buildAmountTo_whenRSKIP201IsNotActive_shouldCreatePegoutTxWithBtcVersion1() {
        // Arrange
        setUpActivations(PAPYRUS_ACTIVATIONS);
        int numberOfUtxos = 10;
        Coin minimumPeginTxValue = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(PAPYRUS_ACTIVATIONS);
        federationUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(federationOutputScript)
            .withValue(minimumPeginTxValue)
            .buildMany(numberOfUtxos, i -> createHash(i + 1));
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

        // Assert
        assertBuildResultResponseCode(SUCCESS, amountToResult);
        BtcTransaction pegoutTransaction = amountToResult.btcTx();
        assertBtcTxVersionIs1(pegoutTransaction);

        List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
        int expectedNumberOfInputs = 1;
        assertEquals(expectedNumberOfInputs, pegoutInputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            pegoutTransaction,
            federationRedeemScript,
            federationUTXOs
        );
        assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsNonDust(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
        assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
    }

    @Test
    void buildAmountTo_whenSingleUtxoCanCoverAmount_shouldCreatePegoutTx() {
        // Arrange
        int numberOfUtxos = 10;
        federationUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(federationOutputScript)
            .withValue(MINIMUM_PEGIN_TX_VALUE)
            .buildMany(numberOfUtxos, i -> createHash(i + 1));
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

        // Assert
        assertBuildResultResponseCode(SUCCESS, amountToResult);
        BtcTransaction pegoutTransaction = amountToResult.btcTx();
        assertBtcTxVersionIs2(pegoutTransaction);

        List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
        int expectedNumberOfInputs = 1;
        assertEquals(expectedNumberOfInputs, pegoutInputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            pegoutTransaction,
            federationRedeemScript,
            federationUTXOs
        );
        assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsNonDust(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
        assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
    }

    @Test
    void buildAmountTo_whenMultipleUtxosCanCoverAmount_shouldCreatePegoutTx() {
        // Arrange
        int numberOfUtxos = 10;
        federationUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(federationOutputScript)
            .withValue(MINIMUM_PEGOUT_TX_VALUE)
            .buildMany(numberOfUtxos, i -> createHash(i + 1));
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
        Coin amountToSend = MINIMUM_PEGOUT_TX_VALUE.add(Coin.valueOf(1000));

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, amountToSend);

        // Assert
        assertBuildResultResponseCode(SUCCESS, amountToResult);
        BtcTransaction pegoutTransaction = amountToResult.btcTx();
        assertBtcTxVersionIs2(pegoutTransaction);

        List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
        int expectedNumberOfInputs = 2;
        assertEquals(expectedNumberOfInputs, pegoutInputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            pegoutTransaction,
            federationRedeemScript,
            federationUTXOs
        );
        assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsNonDust(pegoutTransaction, amountToSend);
        assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
    }

    @Test
    void buildAmountTo_whenWalletHasExactFundsForPegoutRequest_shouldCreatePegoutTxWithNoChangeOutput() {
        // Arrange
        federationUTXOs = List.of(
            UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .build()
        );
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

        // Assert
        assertBuildResultResponseCode(SUCCESS, amountToResult);
        BtcTransaction pegoutTransaction = amountToResult.btcTx();
        assertBtcTxVersionIs2(pegoutTransaction);

        List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
        int expectedNumberOfInputs = federationUTXOs.size();
        assertEquals(expectedNumberOfInputs, pegoutInputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            pegoutTransaction,
            federationRedeemScript,
            federationUTXOs
        );
        assertPegoutTxWithOnlyUserOutputs(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
        assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
    }

    @Test
    void buildAmountTo_whenInsufficientFundsForPegoutRequest_shouldReturnInsufficientMoney() {
        // Arrange
        federationUTXOs = List.of(
            UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .build()
        );
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
        Coin amountExceedingFederationBalance = MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, amountExceedingFederationBalance);

        // Assert
        assertBuildResultResponseCode(INSUFFICIENT_MONEY, amountToResult);
        assertNull(amountToResult.btcTx());
        assertNull(amountToResult.selectedUTXOs());
    }

    @Test
    void buildAmountTo_whenOriginalChangeIsMaxDustValue_shouldCreatePegoutTxDecrementingFirstOutputAndSettingNonDustChange() {
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

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

        // Assert
        assertBuildResultResponseCode(SUCCESS, amountToResult);
        BtcTransaction pegoutTransaction = amountToResult.btcTx();
        assertBtcTxVersionIs2(pegoutTransaction);

        List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
        int expectedNumberOfInputs = 1;
        assertEquals(expectedNumberOfInputs, pegoutInputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            pegoutTransaction,
            federationRedeemScript,
            federationUTXOs
        );
        assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsDust(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
        assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
    }

    @Test
    void buildAmountTo_whenChangeIsNonDustForOneSatoshi_shouldCreatePegoutTxWithNoModificationInTheValues() {
        // Arrange
        federationUTXOs = List.of(
            UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE.add(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT))
                .build()
        );
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

        // Assert
        assertBuildResultResponseCode(SUCCESS, amountToResult);
        BtcTransaction pegoutTransaction = amountToResult.btcTx();
        assertBtcTxVersionIs2(pegoutTransaction);

        List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
        int expectedNumberOfInputs = 1;
        assertEquals(expectedNumberOfInputs, pegoutInputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            pegoutTransaction,
            federationRedeemScript,
            federationUTXOs
        );
        assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsNonDust(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
        assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
    }

    @Test
    void buildAmountTo_whenOriginalChangeIsOneSatoshi_shouldCreatePegoutTxDecrementingFirstOutputAndSettingNonDustChange() {
        // Arrange
        federationUTXOs = List.of(
            UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI))
                .build()
        );
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

        // Assert
        assertBuildResultResponseCode(SUCCESS, amountToResult);
        BtcTransaction pegoutTransaction = amountToResult.btcTx();
        assertBtcTxVersionIs2(pegoutTransaction);

        List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
        int expectedNumberOfInputs = 1;
        assertEquals(expectedNumberOfInputs, pegoutInputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            pegoutTransaction,
            federationRedeemScript,
            federationUTXOs
        );
        assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsDust(pegoutTransaction, MINIMUM_PEGOUT_TX_VALUE);
        assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
    }

    @Test
    void buildAmountTo_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards() {
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
        Coin amountToSend = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.multiply(numberOfUtxos);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, amountToSend);

        // Assert
        assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, amountToResult);
        assertNull(amountToResult.btcTx());
        assertNull(amountToResult.selectedUTXOs());
    }

    /** DUST_VALUE is unrealistic; real pegouts must be at least
     * {@link BridgeConstants#getMinimumPegoutTxValue()}, but we use it to exercise the
     * DUSTY_SEND_REQUESTED path.
     */
    @Test
    void buildAmountTo_whenAmountIsTooSmall_shouldReturnDustySendRequested() {
        // Arrange
        int numberOfUtxos = 10;
        federationUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(federationOutputScript)
            .withValue(MINIMUM_PEGIN_TX_VALUE)
            .buildMany(numberOfUtxos, i -> createHash(i + 1));
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, DUST_VALUE);

        // Assert
        assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, amountToResult);
        assertNull(amountToResult.btcTx());
        assertNull(amountToResult.selectedUTXOs());
    }

    @Test
    void buildAmountTo_whenEstimatedFeeIsTooHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
        // Arrange
        setUpFeePerKb(HIGH_FEE_PER_KB);
        int numberOfUtxos = 3;
        federationUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(federationOutputScript)
            .withValue(MINIMUM_PEGIN_TX_VALUE)
            .buildMany(numberOfUtxos, i -> createHash(i + 1));
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
            federationUTXOs);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS,
            MINIMUM_PEGOUT_TX_VALUE);

        // Assert
        assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, amountToResult);
        assertNull(amountToResult.btcTx());
        assertNull(amountToResult.selectedUTXOs());
    }

    @Test
    void buildAmountTo_whenEstimatedFeeIsHighAndUtxosAreEnough_shouldCreatePegoutTx() {
        // Arrange
        setUpFeePerKb(HIGH_FEE_PER_KB);
        int numberOfUtxos = 10;
        federationUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(federationOutputScript)
            .withValue(Coin.COIN)
            .buildMany(numberOfUtxos, i -> createHash(i + 1));
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
            federationUTXOs);
        Coin requestedAmount = Coin.COIN.subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, requestedAmount);

        // Assert
        assertBuildResultResponseCode(SUCCESS, amountToResult);
        BtcTransaction pegoutTransaction = amountToResult.btcTx();
        assertBtcTxVersionIs2(pegoutTransaction);

        List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
        int expectedNumberOfInputs = 1;
        assertEquals(expectedNumberOfInputs, pegoutInputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            pegoutTransaction,
            federationRedeemScript,
            federationUTXOs
        );
        assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsNonDust(pegoutTransaction, requestedAmount);
        assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
    }

    @Test
    void buildAmountTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
        // Arrange
        int numberOfUtxos = 277;
        federationUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(federationOutputScript)
            .withValue(Coin.COIN)
            .buildMany(numberOfUtxos, i -> createHash(i + 1));
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
        Coin amountToSend = wallet.getBalance().subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, amountToSend);

        // Assert
        assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, amountToResult);
        assertNull(amountToResult.btcTx());
        assertNull(amountToResult.selectedUTXOs());
    }

    @Test
    void buildAmountTo_whenTxIsAlmostExceedingMaxTxSize_shouldCreatePegoutTx() {
        // Arrange
        int numberOfUtxos = 276;
        federationUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(federationOutputScript)
            .withValue(Coin.COIN)
            .buildMany(numberOfUtxos, i -> createHash(i + 1));
        ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
        Coin requestedAmount = wallet.getBalance().subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

        // Act
        BuildResult amountToResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, requestedAmount);

        // Assert
        assertBuildResultResponseCode(SUCCESS, amountToResult);
        BtcTransaction pegoutTransaction = amountToResult.btcTx();
        assertBtcTxVersionIs2(pegoutTransaction);

        List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
        int expectedNumberOfInputs = federationUTXOs.size();
        assertEquals(expectedNumberOfInputs, pegoutInputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            pegoutTransaction,
            federationRedeemScript,
            federationUTXOs
        );
        assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsNonDust(pegoutTransaction, requestedAmount);
        assertSelectedUtxosBelongToTheInputs(amountToResult.selectedUTXOs(), pegoutInputs);
    }

    private void setUpActivations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
    }

    private void setUpFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    private static Address createRecipientAddress() {
        int keyOffset = 2100;
        BigInteger seed = BigInteger.valueOf(keyOffset);
        return BtcECKey.fromPrivate(seed).toAddress(BTC_MAINNET_PARAMS);
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

    private void assertPegoutTxOutputAndChangeOutputsNumbers(BtcTransaction pegoutTransaction,
                                                             int expectedNumberOfUserOutputs,
                                                             int expectedNumberOfChangeOutputs) {
        List<TransactionOutput> userOutputs = getUserOutputs(pegoutTransaction);
        assertReleaseTxNumberOfOutputs(expectedNumberOfUserOutputs, userOutputs);

        List<TransactionOutput> pegoutTransactionChangeOutputs = getChangeOutputs(pegoutTransaction);
        assertReleaseTxNumberOfOutputs(expectedNumberOfChangeOutputs, pegoutTransactionChangeOutputs);

        int expectedNumberOfOutputs = expectedNumberOfUserOutputs + expectedNumberOfChangeOutputs;
        assertReleaseTxNumberOfOutputs(expectedNumberOfOutputs, pegoutTransaction.getOutputs());
    }

    private void assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsNonDust(BtcTransaction pegoutTransaction,
                                                                                   Coin requestedAmount
    ) {
        int expectedNumberOfUserOutputs = 1;
        assertPegoutTxOutputAndChangeOutputsNumbers(pegoutTransaction, expectedNumberOfUserOutputs, EXPECTED_NUMBER_OF_CHANGE_OUTPUTS);

        List<TransactionOutput> userOutputs = getUserOutputs(pegoutTransaction);
        assertDestinationAddress(userOutputs, RECIPIENT_ADDRESS, BTC_MAINNET_PARAMS);

        List<TransactionOutput> changeOutputs = getChangeOutputs(pegoutTransaction);
        assertDestinationAddress(changeOutputs, federationAddress, BTC_MAINNET_PARAMS);

        assertUserAndChangeOutputsValuesWhenOriginalChangeIsNonDust(
            pegoutTransaction,
            changeOutputs,
            requestedAmount
        );
    }

    private void assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsDust(
        BtcTransaction pegoutTransaction,
        Coin requestedAmount
    ) {
        int expectedNumberOfUserOutputs = 1;
        assertPegoutTxOutputAndChangeOutputsNumbers(pegoutTransaction, expectedNumberOfUserOutputs, EXPECTED_NUMBER_OF_CHANGE_OUTPUTS);

        List<TransactionOutput> changeOutputs = getChangeOutputs(pegoutTransaction);
        assertDestinationAddress(changeOutputs, federationAddress, BTC_MAINNET_PARAMS);

        List<TransactionOutput> userOutputs = getUserOutputs(pegoutTransaction);
        assertDestinationAddress(userOutputs, RECIPIENT_ADDRESS, BTC_MAINNET_PARAMS);

        assertUserAndChangeOutputsValuesWhenOriginalChangeIsDust(
            pegoutTransaction,
            changeOutputs,
            requestedAmount
        );
    }

    private void assertPegoutTxWithOnlyUserOutputs(
        BtcTransaction pegoutTransaction,
        Coin requestedAmount
    ) {
        int expectedNumberOfUserOutputs = 1;
        int expectedNumberOfChangeOutputs = 0;
        assertPegoutTxOutputAndChangeOutputsNumbers(pegoutTransaction, expectedNumberOfUserOutputs, expectedNumberOfChangeOutputs);

        List<TransactionOutput> pegoutTransactionOutputs = pegoutTransaction.getOutputs();
        assertDestinationAddress(pegoutTransactionOutputs, RECIPIENT_ADDRESS, BTC_MAINNET_PARAMS);

        assertReleaseTxWithNoChangeHasUserOutputsAmountWithFeesProperly(pegoutTransaction, requestedAmount);
    }

    private List<TransactionOutput> getUserOutputs(BtcTransaction pegoutTransaction) {
        return pegoutTransaction.getOutputs().stream()
            .filter(output ->
                output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS).equals(RECIPIENT_ADDRESS))
            .toList();
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
