package co.rsk.peg;

import static co.rsk.peg.PegTestUtils.createP2shErpFederation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.pegin.PeginEvaluationResult;
import co.rsk.peg.pegin.PeginProcessAction;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PegUtilsEvaluatePeginTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters networkParameters = bridgeMainnetConstants.getBtcParams();
    private static final Context context = new Context(networkParameters);
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

    private Federation activeFederation;
    private Coin minimumPegInTxValue;
    private Wallet activeFedWallet;

    private static Stream<Arguments> protocolVersionArgs() {
        return Stream.of(
            Arguments.of(0),
            Arguments.of(1)
        );
    }

    private static Stream<Arguments> argumentsForWhenProtocolVersionLegacyIs0AndDifferentAddressTypes() {
        return Stream.of(
            Arguments.of(BtcLockSender.TxSenderAddressType.P2PKH, PeginProcessAction.CAN_BE_REGISTERED, null),
            Arguments.of(BtcLockSender.TxSenderAddressType.P2SHP2WPKH, PeginProcessAction.CAN_BE_REGISTERED, null),
            Arguments.of(BtcLockSender.TxSenderAddressType.P2SHMULTISIG, PeginProcessAction.CAN_BE_REFUNDED, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER),
            Arguments.of(BtcLockSender.TxSenderAddressType.P2SHP2WSH, PeginProcessAction.CAN_BE_REFUNDED, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER),
            Arguments.of(BtcLockSender.TxSenderAddressType.UNKNOWN, PeginProcessAction.CANNOT_BE_PROCESSED, RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER)
        );
    }

    @BeforeEach
    void init() {
        List<BtcECKey> activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05"}, true
        );
        activeFederation = createP2shErpFederation(bridgeMainnetConstants, activeFedSigners);

        minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        activeFedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));
    }

    @Test
    void evaluatePegin_preRSKIP379_throwsException() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        ActivationConfig.ForBlock activationsForFingerrot = ActivationConfigsForTest.fingerroot500().forBlock(0);
        PeginInformation peginInformation = mock(PeginInformation.class);

        String expectedErrorMessage = "Can't call this method before RSKIP379 activation";

        Throwable thrownException = assertThrows(IllegalStateException.class, () -> PegUtils.evaluatePegin(
            btcTx,
            peginInformation,
            minimumPegInTxValue,
            activeFedWallet,
            activationsForFingerrot
        ), "When calling PegUtils.evaluatePegin before RSKIP379, it throws an exception.");

        assertEquals(expectedErrorMessage, thrownException.getMessage());
    }

    @Test
    void evaluatePegin_valueBelowMinimum_returnsPeginEvaluationResultWithCannotBeProcessAndInvalidAmount() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        PeginInformation peginInformation = mock(PeginInformation.class);
        Coin belowMinimum = minimumPegInTxValue.minus(Coin.SATOSHI);

        btcTx.addOutput(belowMinimum, activeFederation.getAddress());

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(
            btcTx,
            peginInformation,
            minimumPegInTxValue,
            activeFedWallet,
            activations
        );

        assertEquals(PeginProcessAction.CANNOT_BE_PROCESSED, peginEvaluationResult.getPeginProcessAction());
        assertTrue(peginEvaluationResult.getRejectedPeginReason().isPresent());
        assertEquals(RejectedPeginReason.INVALID_AMOUNT, peginEvaluationResult.getRejectedPeginReason().get());
    }

    @ParameterizedTest()
    @MethodSource("protocolVersionArgs")
    void evaluatePegin_btcRefundAddressNullAndDifferentProtocolVersions(int protocolVersion) throws PeginInstructionsException {
        PeginInformation peginInformation = mock(PeginInformation.class);
        when(peginInformation.getBtcRefundAddress()).thenReturn(null);
        when(peginInformation.getProtocolVersion()).thenReturn(protocolVersion);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        doThrow(new PeginInstructionsException("Could not get peg-in information")).when(peginInformation).parse(btcTx);

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(
            btcTx,
            peginInformation,
            minimumPegInTxValue,
            activeFedWallet,
            activations
        );

        assertEquals(PeginProcessAction.CANNOT_BE_PROCESSED, peginEvaluationResult.getPeginProcessAction());
        assertTrue(peginEvaluationResult.getRejectedPeginReason().isPresent());
        assertEquals(RejectedPeginReason.PEGIN_V1_INVALID_PAYLOAD, peginEvaluationResult.getRejectedPeginReason().get());
    }

    @ParameterizedTest()
    @MethodSource("protocolVersionArgs")
    void evaluatePegin_parseThrowsExceptionAndBtcRefundAddressIsPresent(int protocolVersion) throws PeginInstructionsException {
        Address btcRefundAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "test");;
        PeginInformation peginInformation = mock(PeginInformation.class);
        when(peginInformation.getBtcRefundAddress()).thenReturn(btcRefundAddress);

        when(peginInformation.getProtocolVersion()).thenReturn(protocolVersion);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        doThrow(new PeginInstructionsException("Could not get peg-in information")).when(peginInformation).parse(btcTx);

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(
            btcTx,
            peginInformation,
            minimumPegInTxValue,
            activeFedWallet,
            activations
        );

        assertEquals(PeginProcessAction.CAN_BE_REFUNDED, peginEvaluationResult.getPeginProcessAction());
        assertTrue(peginEvaluationResult.getRejectedPeginReason().isPresent());
        assertEquals(RejectedPeginReason.PEGIN_V1_INVALID_PAYLOAD, peginEvaluationResult.getRejectedPeginReason().get());
    }

    @ParameterizedTest()
    @MethodSource("argumentsForWhenProtocolVersionLegacyIs0AndDifferentAddressTypes")
    void evaluatePegin_protocolVersionIs0AndDifferentTypesOfAddresses(
        BtcLockSender.TxSenderAddressType txSenderAddressType,
        PeginProcessAction peginProcessAction,
        RejectedPeginReason rejectedPeginReason
    ) {
        PeginInformation peginInformation = mock(PeginInformation.class);
        when(peginInformation.getProtocolVersion()).thenReturn(0);
        when(peginInformation.getSenderBtcAddressType()).thenReturn(txSenderAddressType);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(
            btcTx,
            peginInformation,
            minimumPegInTxValue,
            activeFedWallet,
            activations
        );

        assertEquals(peginProcessAction, peginEvaluationResult.getPeginProcessAction());
        assertEquals(rejectedPeginReason, peginEvaluationResult.getRejectedPeginReason().orElse(null));
    }

    @Test
    void evaluatePegin_validPeginInformationWithProtocolVersion1_peginProcessActionCanBeRegistered() {
        PeginInformation peginInformation = mock(PeginInformation.class);
        when(peginInformation.getProtocolVersion()).thenReturn(1);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(
            btcTx,
            peginInformation,
            minimumPegInTxValue,
            activeFedWallet,
            activations
        );

        assertEquals(PeginProcessAction.CAN_BE_REGISTERED, peginEvaluationResult.getPeginProcessAction());
    }

    @Test()
    void evaluatePegin_validPeginInformationWithInvalidProtocolVersion_throwsException() {
        PeginInformation peginInformation = mock(PeginInformation.class);
        int invalidProtocolVersion = -2;

        when(peginInformation.getProtocolVersion()).thenReturn(invalidProtocolVersion);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        String expectedErrorMessage = "Invalid state. Unexpected pegin protocol " + invalidProtocolVersion;

        Throwable thrownException = assertThrows(IllegalStateException.class, () -> PegUtils.evaluatePegin(
            btcTx,
            peginInformation,
            minimumPegInTxValue,
            activeFedWallet,
            activations
        ));

        assertEquals(expectedErrorMessage, thrownException.getMessage());
    }

    @Test
    void evaluatePegin_pegout_peginProcessActionCanBeRegistered() {
        PeginInformation peginInformation = mock(PeginInformation.class);
        when(peginInformation.getProtocolVersion()).thenReturn(1);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        Address randomAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "add1");

        btcTx.addInput(
                BitcoinTestUtils.createHash(1),
                0,
                ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
        ); // Fed spending utxo

        btcTx.addOutput(minimumPegInTxValue, randomAddress); // Output to random address
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // "change" output to fed

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(
                btcTx,
                peginInformation,
                minimumPegInTxValue,
                activeFedWallet,
                activations
        );

        assertEquals(PeginProcessAction.CAN_BE_REGISTERED, peginEvaluationResult.getPeginProcessAction());
    }

    @Test
    void evaluatePegin_migration_peginProcessActionCanBeRegistered() {
        PeginInformation peginInformation = mock(PeginInformation.class);
        when(peginInformation.getProtocolVersion()).thenReturn(1);

        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        Address randomAddress = BitcoinTestUtils.createP2PKHAddress(networkParameters, "add1");

        btcTx.addInput(
                BitcoinTestUtils.createHash(1),
                0,
                ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
        ); // Fed spending utxo

        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(
                btcTx,
                peginInformation,
                minimumPegInTxValue,
                activeFedWallet,
                activations
        );

        assertEquals(PeginProcessAction.CAN_BE_REGISTERED, peginEvaluationResult.getPeginProcessAction());
    }

}
