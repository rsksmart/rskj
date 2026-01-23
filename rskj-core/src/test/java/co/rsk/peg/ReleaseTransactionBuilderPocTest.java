package co.rsk.peg;

import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createUTXO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.ReleaseRequestQueue.Entry;
import co.rsk.peg.ReleaseTransactionBuilder.BuildResult;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import co.rsk.test.builders.ReleaseTransactionTestBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReleaseTransactionBuilderPocTest {
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);
    private static final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainNetConstants.getBtcParams();
    private static final Context btcContext = new Context(btcMainnetParams);

    private static final Coin minimumPegoutTxValue = bridgeMainNetConstants.getMinimumPegoutTxValue();
    private static final Coin minimumPeginTxValue = bridgeMainNetConstants.getMinimumPeginTxValue(allActivations);

    private static final Federation standardFederation = StandardMultiSigFederationBuilder.builder().build();
    private static final Federation p2shErpFederation = P2shErpFederationBuilder.builder().build();
    private static final Federation p2wshErpFederation = P2shErpFederationBuilder.builder().build();

    private Coin feePerKb;
    private Coin dustAmount;
    private BridgeStorageProvider bridgeStorageProvider;

    private static final ReleaseTransactionTestBuilder releaseTransactionTestBuilder = ReleaseTransactionTestBuilder.builder();

    @BeforeEach
    void setUp() {
        bridgeStorageProvider = mock(BridgeStorageProvider.class);
        feePerKb = Coin.MILLICOIN.multiply(2);
        dustAmount = feePerKb.div(2);
        releaseTransactionTestBuilder
            .withNetworkParameters(btcMainnetParams)
            .withActivations(allActivations)
            .withFeePerKb(feePerKb);
    }

    @ParameterizedTest()
    @MethodSource("federationProvider")
    void buildBatchedPegouts_whenNoPegoutRequests_shouldThrowIllegalStateException(Federation federation) {
        // Arrange
        Address federationAddress = federation.getAddress();
        List<UTXO> utxos = createUTXOs(10, minimumPeginTxValue, federationAddress);
        Wallet wallet = buildWallet(federation, utxos);
        int federationFormatVersion = federation.getFormatVersion();

        ReleaseTransactionBuilder releaseTransactionBuilder = releaseTransactionTestBuilder
            .withWallet(wallet)
            .withFederationFormatVersion(federationFormatVersion)
            .withChangeAddress(federationAddress)
            .build();

        List<ReleaseRequestQueue.Entry> noPegoutRequests = Collections.emptyList();

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> releaseTransactionBuilder.buildBatchedPegouts(noPegoutRequests));
    }

    @ParameterizedTest()
    @MethodSource("federationProvider")
    void buildBatchedPegouts_whenSinglePegoutRequest_shouldCreateReleaseTxWith2Outputs(Federation federation) {
        // Arrange
        Address federationAddress = federation.getAddress();
        List<UTXO> utxos = createUTXOs(10, minimumPeginTxValue, federationAddress);
        Wallet wallet = buildWallet(federation, utxos);
        Coin walletBalance = wallet.getBalance();
        int federationFormatVersion = federation.getFormatVersion();

        ReleaseTransactionBuilder releaseTransactionBuilder = releaseTransactionTestBuilder
            .withWallet(wallet)
            .withFederationFormatVersion(federationFormatVersion)
            .withChangeAddress(federationAddress)
            .build();

        List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, minimumPegoutTxValue);
        Coin expectedChangeAmountToBeGreaterThan = walletBalance.subtract(minimumPegoutTxValue);

        // Act
        BuildResult releaseTransactionResult = releaseTransactionBuilder.buildBatchedPegouts(pegoutRequests);

        // Assert
        assertNotNull(releaseTransactionResult);

        BtcTransaction releaseTransaction = releaseTransactionResult.btcTx();

        assertBtcTxVersionIs2(releaseTransaction);
        int expectedNumberOfOutputs = pegoutRequests.size() + 1; // +1 for change output
        assertReleaseTransactionNumberOfOutputs(releaseTransaction, expectedNumberOfOutputs);
        assertUserOutputs(releaseTransaction, pegoutRequests, federationAddress);
        assertReleaseTransactionHasOneChangeOutput(releaseTransaction, federationAddress, expectedChangeAmountToBeGreaterThan);
    }

    @ParameterizedTest()
    @MethodSource("federationProvider")
    void buildBatchedPegouts_whenMultiplePegoutRequest_shouldCreateReleaseTxWithMultipleUserOutputs(Federation federation) {
        Address federationAddress = federation.getAddress();
        List<UTXO> utxos = createUTXOs(10, minimumPeginTxValue, federationAddress);
        Wallet wallet = buildWallet(federation, utxos);
        Coin walletBalance = wallet.getBalance();
        int federationFormatVersion = federation.getFormatVersion();

        ReleaseTransactionBuilder releaseTransactionBuilder = releaseTransactionTestBuilder
            .withWallet(wallet)
            .withFederationFormatVersion(federationFormatVersion)
            .withChangeAddress(federationAddress)
            .build();

        List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(3, minimumPegoutTxValue);
        Coin pegoutRequestsTotalAmount = pegoutRequests.stream().map(Entry::getAmount).reduce(Coin.ZERO, Coin::add);
        Coin expectedChangeAmountToBeGreaterThan = walletBalance.subtract(pegoutRequestsTotalAmount);

        // Act
        BuildResult releaseTransactionResult = releaseTransactionBuilder.buildBatchedPegouts(pegoutRequests);

        // Assert
        assertNotNull(releaseTransactionResult);

        BtcTransaction releaseTransaction = releaseTransactionResult.btcTx();

        assertBtcTxVersionIs2(releaseTransaction);
        int expectedNumberOfOutputs = pegoutRequests.size() + 1; // +1 for change output
        assertReleaseTransactionNumberOfOutputs(releaseTransaction, expectedNumberOfOutputs);
        assertUserOutputs(releaseTransaction, pegoutRequests, federationAddress);
        assertReleaseTransactionHasOneChangeOutput(releaseTransaction, federationAddress, expectedChangeAmountToBeGreaterThan);
    }

    @ParameterizedTest()
    @MethodSource("federationProvider")
    void buildBatchedPegouts_whenResultChangeOutputWillBeDust_shouldCreateTxWithDustChangeOutput(Federation federation) {
        // Arrange
        Address federationAddress = federation.getAddress();
        Coin utxoAmount = minimumPegoutTxValue.add(dustAmount);
        List<UTXO> utxos = createUTXOs(1, utxoAmount, federationAddress);
        Wallet wallet = buildWallet(federation, utxos);
        Coin walletBalance = wallet.getBalance();
        int federationFormatVersion = federation.getFormatVersion();

        ReleaseTransactionBuilder releaseTransactionBuilder = releaseTransactionTestBuilder
            .withWallet(wallet)
            .withFederationFormatVersion(federationFormatVersion)
            .withChangeAddress(federationAddress)
            .build();

        List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, minimumPegoutTxValue);
        Coin expectedChangeAmountToBeGreaterThan = walletBalance.subtract(minimumPegoutTxValue);
        assertTrue(expectedChangeAmountToBeGreaterThan.isLessThan(feePerKb));

        // Act
        BuildResult releaseTransactionResult = releaseTransactionBuilder.buildBatchedPegouts(pegoutRequests);

        // Assert
        assertNotNull(releaseTransactionResult);

        BtcTransaction releaseTransaction = releaseTransactionResult.btcTx();
        assertBtcTxVersionIs2(releaseTransaction);
        int expectedNumberOfOutputs = pegoutRequests.size() + 1; // +1 for change output
        assertReleaseTransactionNumberOfOutputs(releaseTransaction, expectedNumberOfOutputs);
        assertUserOutputs(releaseTransaction, pegoutRequests, federationAddress);
        assertReleaseTransactionHasOneChangeOutput(releaseTransaction, federationAddress, expectedChangeAmountToBeGreaterThan);
    }

    @ParameterizedTest()
    @MethodSource("federationProvider")
    void buildBatchedPegouts_whenUtxosAreDustButEnoughToPay_shouldReturnCouldNotAdjustDownwards(Federation federation) {
        // Arrange
        Address federationAddress = federation.getAddress();
        List<UTXO> utxos = createUTXOs(100, dustAmount, federationAddress);
        Wallet wallet = buildWallet(federation, utxos);
        int federationFormatVersion = federation.getFormatVersion();

        ReleaseTransactionBuilder releaseTransactionBuilder = releaseTransactionTestBuilder
            .withWallet(wallet)
            .withFederationFormatVersion(federationFormatVersion)
            .withChangeAddress(federationAddress)
            .build();

        List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2, minimumPegoutTxValue);

        // Act
        BuildResult releaseTransactionResult = releaseTransactionBuilder.buildBatchedPegouts(pegoutRequests);

        // Assert
        assertEquals(COULD_NOT_ADJUST_DOWNWARDS, releaseTransactionResult.responseCode());
    }

    @ParameterizedTest()
    @MethodSource("federationProvider")
    void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney(Federation federation) {
        // Arrange
        Address federationAddress = federation.getAddress();
        Coin utxoAmount = minimumPegoutTxValue.divide(2);
        List<UTXO> utxos = createUTXOs(1, utxoAmount, federationAddress);
        Wallet wallet = buildWallet(federation, utxos);
        int federationFormatVersion = federation.getFormatVersion();

        ReleaseTransactionBuilder releaseTransactionBuilder = releaseTransactionTestBuilder
            .withWallet(wallet)
            .withFederationFormatVersion(federationFormatVersion)
            .withChangeAddress(federationAddress)
            .build();

        List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2, minimumPegoutTxValue);

        // Act
        BuildResult releaseTransactionResult = releaseTransactionBuilder.buildBatchedPegouts(pegoutRequests);

        // Assert
        assertEquals(INSUFFICIENT_MONEY, releaseTransactionResult.responseCode());
        assertNull(releaseTransactionResult.btcTx());
    }

    @ParameterizedTest()
    @MethodSource("federationProvider")
    void buildBatchedPegouts_whenEstimatedFeeIsTooHigh_shouldReturnDustySendRequested(Federation federation) {
        // Arrange
        Address federationAddress = federation.getAddress();
        Coin smallAmount = Coin.SATOSHI.multiply(1000);
        List<UTXO> utxos = createUTXOs(1, smallAmount, federationAddress);
        Wallet wallet = buildWallet(federation, utxos);
        int federationFormatVersion = federation.getFormatVersion();

        Coin excessivelyHighFeePerKb = Coin.COIN.multiply(10);
        ReleaseTransactionBuilder releaseTransactionBuilder = releaseTransactionTestBuilder
            .withWallet(wallet)
            .withFederationFormatVersion(federationFormatVersion)
            .withChangeAddress(federationAddress)
            .withFeePerKb(excessivelyHighFeePerKb)
            .build();

        List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, smallAmount.divide(2));

        // Act
        BuildResult releaseTransactionResult = releaseTransactionBuilder.buildBatchedPegouts(pegoutRequests);

        // Assert
        assertEquals(DUSTY_SEND_REQUESTED, releaseTransactionResult.responseCode());
        assertNull(releaseTransactionResult.btcTx());
    }

    @ParameterizedTest()
    @MethodSource("standardMultisigAndP2shErpFederationProvider")
    void buildBatchedPegouts_when200UtxosToPay50PegoutRequests_shouldReturnExceedMaxTransactionSize(Federation federation) {
        // Arrange
        Address federationAddress = federation.getAddress();
        List<UTXO> utxos = createUTXOs(200, minimumPeginTxValue, federationAddress);
        Wallet wallet = buildWallet(federation, utxos);
        int federationFormatVersion = federation.getFormatVersion();

        ReleaseTransactionBuilder releaseTransactionBuilder = releaseTransactionTestBuilder
            .withWallet(wallet)
            .withFederationFormatVersion(federationFormatVersion)
            .withChangeAddress(federationAddress)
            .build();

        List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(50, minimumPegoutTxValue);

        // Act
        BuildResult releaseTransactionResult = releaseTransactionBuilder.buildBatchedPegouts(pegoutRequests);

        // Assert
        assertEquals(EXCEED_MAX_TRANSACTION_SIZE, releaseTransactionResult.responseCode());
        assertNull(releaseTransactionResult.btcTx());
    }

    private void assertReleaseTransactionNumberOfOutputs(BtcTransaction releaseTransaction, int expectedNumberOfOutputs) {
        int actualNumberOfOutputs = releaseTransaction.getOutputs().size();
        assertEquals(expectedNumberOfOutputs, actualNumberOfOutputs);
    }

    private void assertBtcTxVersionIs2(BtcTransaction releaseTransaction) {
        assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
    }

    private void assertUserOutputs(BtcTransaction releaseTransaction, List<Entry> pegoutRequests, Address changeAddress) {
        List<TransactionOutput> userOutputs = releaseTransaction.getOutputs().stream().filter(
            output -> !output.getScriptPubKey().getToAddress(btcMainnetParams).equals(changeAddress)
        ).toList();
        assertEquals(pegoutRequests.size(), userOutputs.size());
        for (Entry pegoutRequest : pegoutRequests) {
            assertUserOutput(pegoutRequest, userOutputs);
        }
    }

    private static void assertUserOutput(Entry pegoutRequest, List<TransactionOutput> userOutputs) {
        Optional<TransactionOutput> userOutput = userOutputs.stream().filter(
            output -> output.getScriptPubKey().getToAddress(btcMainnetParams)
                .equals(pegoutRequest.getDestination())
        ).findFirst();
        Assertions.assertTrue(userOutput.isPresent(),
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

    private void assertReleaseTransactionHasOneChangeOutput(BtcTransaction releaseTransaction, Address changeAddress, Coin expectedChangeAmountToBeGreaterThan) {
        List<TransactionOutput> outputsToChangeAddress = releaseTransaction.getOutputs().stream().filter(
            output -> output.getScriptPubKey().getToAddress(btcMainnetParams)
                .equals(changeAddress)).toList();
        assertEquals(1, outputsToChangeAddress.size());

        TransactionOutput changeOutput = outputsToChangeAddress.get(0);
        assertTrue(changeOutput.getValue().compareTo(expectedChangeAmountToBeGreaterThan) < 1, "Change output amount is greater than expected");
    }

    private List<Entry> createPegoutRequests(int count, Coin amount) {
        List<ReleaseRequestQueue.Entry> pegoutRequests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Address recipientAddress = BtcECKey.fromPrivate(BigInteger.valueOf(i + 1100))
                .toAddress(btcMainnetParams);
            Entry pegoutEntry = new Entry(
                recipientAddress,
                amount
            );
            pegoutRequests.add(pegoutEntry);
        }
        return pegoutRequests;
    }

    private List<UTXO> createUTXOs(int count, Coin amount, Address address) {
        List<UTXO> utxos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            utxos.add(createUTXO(i + 1, 0, amount, address));
        }

        return utxos;
    }

    private static Stream<Arguments> federationProvider() {
        return Stream.of(
            Arguments.of(standardFederation),
            Arguments.of(p2shErpFederation),
            Arguments.of(p2wshErpFederation)
        );
    }

    private static Stream<Arguments> standardMultisigAndP2shErpFederationProvider() {
        return Stream.of(
            Arguments.of(standardFederation),
            Arguments.of(p2shErpFederation)
        );
    }

    private Wallet buildWallet(Federation federation, List<UTXO> utxos) {
        return BridgeUtils.getFederationSpendWallet(
            btcContext,
            federation,
            utxos,
            true,
            bridgeStorageProvider
        );
    }
}
