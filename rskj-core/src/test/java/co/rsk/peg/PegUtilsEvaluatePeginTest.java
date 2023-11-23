package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.pegin.PeginEvaluationResult;
import co.rsk.peg.pegin.PeginProcessAction;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static co.rsk.peg.PegTestUtils.createP2shErpFederation;
import static org.mockito.Mockito.*;

public class PegUtilsEvaluatePeginTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final Context context = new Context(bridgeMainnetConstants.getBtcParams());
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.tbd600().forBlock(0);

    private List<BtcECKey> activeFedSigners;
    private Federation activeFederation;

    private static String btcTestAddress = "mpgJ8n2NUf23NHcJs59LgEqQ4yCv7MYGU6";

    private static Stream<Arguments> evaluatePeginArgumentsForWhenParseExceptionAndBtcRefundAddressIsNull() {
        PeginInformation peginInformation = mock(PeginInformation.class);
        when(peginInformation.getBtcRefundAddress()).thenReturn(null);
        return Stream.of(
                Arguments.of(peginInformation, 0),
                Arguments.of(peginInformation, 1)
        );
    }

    private static Stream<Arguments> evaluatePeginArgumentsForWhenParseExceptionAndBtcRefundAddressIsValid() {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        PeginInformation peginInformation = mock(PeginInformation.class);
        Address address = Address.fromBase58(params, btcTestAddress);
        when(peginInformation.getBtcRefundAddress()).thenReturn(address);
        return Stream.of(
                Arguments.of(peginInformation, 0, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER),
                Arguments.of(peginInformation, 1, RejectedPeginReason.PEGIN_V1_INVALID_PAYLOAD)
        );
    }

    private static Stream<Arguments> evaluatePeginArgumentsForWhenProtocolVersionLegacyIs0AndDifferentAddressTypes() {
        PeginInformation peginInformation = mock(PeginInformation.class);
        when(peginInformation.getProtocolVersion()).thenReturn(0);
        return Stream.of(
                Arguments.of(peginInformation, BtcLockSender.TxSenderAddressType.P2PKH, PeginProcessAction.CAN_BE_REGISTERED, null),
                Arguments.of(peginInformation, BtcLockSender.TxSenderAddressType.P2SHP2WPKH, PeginProcessAction.CAN_BE_REGISTERED, null),
                Arguments.of(peginInformation, BtcLockSender.TxSenderAddressType.P2SHMULTISIG, PeginProcessAction.CAN_BE_REFUNDED, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER),
                Arguments.of(peginInformation, BtcLockSender.TxSenderAddressType.P2SHP2WSH, PeginProcessAction.CAN_BE_REFUNDED, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER),
                Arguments.of(peginInformation, BtcLockSender.TxSenderAddressType.UNKNOWN, PeginProcessAction.CANNOT_BE_PROCESSED, RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER)
        );
    }

    @BeforeEach
    void init() {
        activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
                new String[]{"fa01", "fa02", "fa03", "fa04", "fa05"}, true
        );
        activeFederation = createP2shErpFederation(bridgeMainnetConstants, activeFedSigners);
    }

    @Test
    void evaluatePegin_preRSKIP379_throwsException() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        ActivationConfig.ForBlock activationsForFingerrot = ActivationConfigsForTest.fingerroot500().forBlock(0);
        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));
        PeginInformation peginInformation = mock(PeginInformation.class);

        String expectedErrorMessage = "Can't call this method before RSKIP379 activation";

        Throwable thrownException = Assertions.assertThrows(IllegalStateException.class, () -> {
            PegUtils.evaluatePegin(btcTx, peginInformation, minimumPegInTxValue, fedWallet, activationsForFingerrot);
        }, "When calling PegUtils.evaluatePegin before RSKIP379, it throws an exception.");

        Assertions.assertEquals(expectedErrorMessage, thrownException.getMessage());

    }

    @Test
    void evaluatePegin_postRSKIP379MockedAllUTXOsToFedAreAboveMinimumPeginValueReturnsFalse_returnsPeginEvaluationResultWithCannotBeProcessAndInvalidAmount() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        ActivationConfig.ForBlock activationsForTbd = ActivationConfigsForTest.tbd600().forBlock(0);
        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));
        PeginInformation peginInformation = mock(PeginInformation.class);

        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederation.getAddress());

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(btcTx, peginInformation, minimumPegInTxValue, fedWallet, activationsForTbd);

        Assertions.assertEquals(PeginProcessAction.CANNOT_BE_PROCESSED, peginEvaluationResult.getPeginProcessAction());
        Assertions.assertTrue(peginEvaluationResult.getRejectedPeginReason().isPresent());
        Assertions.assertEquals(RejectedPeginReason.INVALID_AMOUNT, peginEvaluationResult.getRejectedPeginReason().get());

    }

    @ParameterizedTest()
    @MethodSource("evaluatePeginArgumentsForWhenParseExceptionAndBtcRefundAddressIsNull")
    void evaluatePegin_BtcRefundAddressNullAndDifferentProtocolVersions(PeginInformation peginInformation, int  protocolVersion) throws PeginInstructionsException {

        when(peginInformation.getProtocolVersion()).thenReturn(protocolVersion);

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        doThrow(new PeginInstructionsException("Could not get peg-in information")).when(peginInformation).parse(btcTx);

        ActivationConfig.ForBlock activationsForTbd = ActivationConfigsForTest.tbd600().forBlock(0);
        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(btcTx, peginInformation, minimumPegInTxValue, fedWallet, activationsForTbd);

        RejectedPeginReason rejectedPeginReason = protocolVersion == 1 ? RejectedPeginReason.PEGIN_V1_INVALID_PAYLOAD : RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER;

        Assertions.assertEquals(PeginProcessAction.CANNOT_BE_PROCESSED, peginEvaluationResult.getPeginProcessAction());
        Assertions.assertTrue(peginEvaluationResult.getRejectedPeginReason().isPresent());
        Assertions.assertEquals(rejectedPeginReason, peginEvaluationResult.getRejectedPeginReason().get());

    }

    @ParameterizedTest()
    @MethodSource("evaluatePeginArgumentsForWhenParseExceptionAndBtcRefundAddressIsValid")
    void evaluatePegin_parseThrowsExceptionAndBtcRefundAddressIsPresent(PeginInformation peginInformation, int  protocolVersion, RejectedPeginReason rejectedPeginReason) throws PeginInstructionsException {

        when(peginInformation.getProtocolVersion()).thenReturn(protocolVersion);

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        doThrow(new PeginInstructionsException("Could not get peg-in information")).when(peginInformation).parse(btcTx);

        ActivationConfig.ForBlock activationsForTbd = ActivationConfigsForTest.tbd600().forBlock(0);
        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(btcTx, peginInformation, minimumPegInTxValue, fedWallet, activationsForTbd);

        Assertions.assertEquals(PeginProcessAction.CAN_BE_REFUNDED, peginEvaluationResult.getPeginProcessAction());
        Assertions.assertTrue(peginEvaluationResult.getRejectedPeginReason().isPresent());
        Assertions.assertEquals(rejectedPeginReason, peginEvaluationResult.getRejectedPeginReason().get());

    }

    @ParameterizedTest()
    @MethodSource("evaluatePeginArgumentsForWhenProtocolVersionLegacyIs0AndDifferentAddressTypes")
    void evaluatePegin_protocolVersionIs0AndDifferentTypesOfAddresses(PeginInformation peginInformation, BtcLockSender.TxSenderAddressType txSenderAddressType, PeginProcessAction peginProcessAction, RejectedPeginReason rejectedPeginReason) {

        when(peginInformation.getSenderBtcAddressType()).thenReturn(txSenderAddressType);

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        ActivationConfig.ForBlock activationsForTbd = ActivationConfigsForTest.tbd600().forBlock(0);
        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(btcTx, peginInformation, minimumPegInTxValue, fedWallet, activationsForTbd);

        Assertions.assertEquals(peginProcessAction, peginEvaluationResult.getPeginProcessAction());

        Assertions.assertEquals(rejectedPeginReason, peginEvaluationResult.getRejectedPeginReason().orElse(null));

    }

    @Test()
    void evaluatePegin_validPeginInformationWithProtocolVersion1_peginProcessActionCanBeRegistered() {

        PeginInformation peginInformation = mock(PeginInformation.class);

        when(peginInformation.getProtocolVersion()).thenReturn(1);

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        ActivationConfig.ForBlock activationsForTbd = ActivationConfigsForTest.tbd600().forBlock(0);
        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(btcTx, peginInformation, minimumPegInTxValue, fedWallet, activationsForTbd);

        Assertions.assertEquals(PeginProcessAction.CAN_BE_REGISTERED, peginEvaluationResult.getPeginProcessAction());

    }

    @Test()
    void evaluatePegin_validPeginInformationWithInvalidProtocolVersion_throwsException() {

        PeginInformation peginInformation = mock(PeginInformation.class);

        int invalidProtocolVersion = -2;

        when(peginInformation.getProtocolVersion()).thenReturn(invalidProtocolVersion);

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress());

        ActivationConfig.ForBlock activationsForTbd = ActivationConfigsForTest.tbd600().forBlock(0);
        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        String expectedErrorMessage = "Invalid state. Unexpected pegin protocol " + invalidProtocolVersion;

        Throwable thrownException = Assertions.assertThrows(IllegalStateException.class, () -> {
            PegUtils.evaluatePegin(btcTx, peginInformation, minimumPegInTxValue, fedWallet, activationsForTbd);
        });

        Assertions.assertEquals(expectedErrorMessage, thrownException.getMessage());

    }

}
