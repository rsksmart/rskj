package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;

import java.util.List;
import java.util.function.Predicate;

import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_1;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.*;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReleaseTransactionBuilderAssertions {

    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();

    public static void assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
        BtcTransaction releaseTransaction,
        Script federationRedeemScript,
        List<UTXO> federationUTXOs) {
        for (TransactionInput input : releaseTransaction.getInputs()) {
            Script scriptSig = input.getScriptSig();
            assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat(scriptSig, federationRedeemScript);
            assertInputIsFromFederationUTXOsWallet(input, federationUTXOs);
        }
    }

    public static void assertReleaseTxInputsHasProperFormatAndBelongsToP2shFederation(
        BtcTransaction releaseTransaction,
        Script federationRedeemScript,
        List<UTXO> federationUTXOs) {
        for (TransactionInput input : releaseTransaction.getInputs()) {
            Script scriptSig = input.getScriptSig();
            assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat(scriptSig, federationRedeemScript);
            assertInputIsFromFederationUTXOsWallet(input, federationUTXOs);
        }
    }

    public static void assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshFederation(
        BtcTransaction releaseTransaction,
        Script federationRedeemScript,
        List<UTXO> federationUTXOs) {
        List<TransactionInput> releaseTransactionInputs = releaseTransaction.getInputs();
        for (int inputIndex = 0; inputIndex < releaseTransactionInputs.size(); inputIndex++) {
            TransactionWitness witness = releaseTransaction.getWitness(inputIndex);
            assertP2shP2wshWitnessWithoutSignaturesHasProperFormat(witness, federationRedeemScript);
            TransactionInput input = releaseTransactionInputs.get(inputIndex);
            assertInputIsFromFederationUTXOsWallet(input, federationUTXOs);
        }
    }

    public static void assertInputIsFromFederationUTXOsWallet(TransactionInput input, List<UTXO> federationUtxos) {
        Predicate<UTXO> isUTXOAndReleaseInputFromTheSameOutpoint = utxo ->
            utxo.getHash().equals(input.getOutpoint().getHash())
                && utxo.getIndex() == input.getOutpoint().getIndex();
        List<UTXO> foundUtxo = federationUtxos.stream()
            .filter(isUTXOAndReleaseInputFromTheSameOutpoint).toList();
        int expectedNumberOfUtxos = 1;
        assertEquals(expectedNumberOfUtxos, foundUtxo.size());
    }

    public static void assertSelectedUtxosBelongToTheInputs(List<UTXO> selectedUtxos,
                                                             List<TransactionInput> releaseTransactionInputs) {
        assertEquals(releaseTransactionInputs.size(), selectedUtxos.size());
        for (UTXO utxo : selectedUtxos) {
            List<TransactionInput> matchingInputs = releaseTransactionInputs.stream().
                filter(input -> input.getOutpoint().getHash().equals(utxo.getHash())
                    && input.getOutpoint().getIndex() == utxo.getIndex()).toList();
            assertEquals(1, matchingInputs.size());
        }
    }

    public static void assertBuildResultResponseCode(ReleaseTransactionBuilder.Response expectedResponseCode,
                                                     ReleaseTransactionBuilder.BuildResult buildResult) {
        ReleaseTransactionBuilder.Response actualResponseCode = buildResult.responseCode();
        assertEquals(expectedResponseCode, actualResponseCode);
    }

    public static void assertBtcTxVersionIs1(BtcTransaction releaseTransaction) {
        assertEquals(BTC_TX_VERSION_1, releaseTransaction.getVersion());
    }

    public static void assertBtcTxVersionIs2(BtcTransaction releaseTransaction) {
        assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
    }

    public static void assertDestinationAddress(List<TransactionOutput> releaseTransactionOutputs,
                                                Address expectedDestinationAddress) {
        for (TransactionOutput output : releaseTransactionOutputs) {
            Address destinationAddress = output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
            assertEquals(expectedDestinationAddress, destinationAddress);
        }
    }

    public static void assertReleaseTxHasChangeAndPegoutsAmountWithFeesProperly(BtcTransaction releaseTransaction,
                                                                                List<TransactionOutput> releaseTransactionChangeOutputs,
                                                                                Coin expectedSentPegoutAmount) {
        Coin inputTotalAmount = releaseTransaction.getInputSum();
        Coin expectedChangeAmount = inputTotalAmount.subtract(expectedSentPegoutAmount);

        if (isDust(expectedChangeAmount)) {
            Coin amountToGetNonDustValue = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.subtract(expectedChangeAmount);
            expectedSentPegoutAmount = expectedSentPegoutAmount.subtract(amountToGetNonDustValue);
            expectedChangeAmount = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
        }

        int expectedNumberOfOutputs = 1;
        assertEquals(expectedNumberOfOutputs, releaseTransactionChangeOutputs.size());
        Coin changeOutputsAmount = releaseTransactionChangeOutputs.stream()
            .map(TransactionOutput::getValue)
            .reduce(Coin::add)
            .orElse(Coin.ZERO);
        assertEquals(expectedChangeAmount, changeOutputsAmount);

        Coin pegoutOutputsAmount = releaseTransaction.getOutputSum().subtract(changeOutputsAmount);
        Coin releaseTransactionFees = releaseTransaction.getFee();
        Coin pegoutsAndFeesAmount = releaseTransactionFees.add(pegoutOutputsAmount);
        assertEquals(expectedSentPegoutAmount, pegoutsAndFeesAmount);
        assertEquals(inputTotalAmount, pegoutsAndFeesAmount.add(changeOutputsAmount));
    }

    private static boolean isDust(Coin expectedChangeAmount) {
        return expectedChangeAmount.compareTo(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT) < 0;
    }

    public static void assertReleaseTxWithNoChangeHasPegoutsAmountWithFeesProperly(BtcTransaction releaseTransaction,
                                                                                   Coin expectedSentAmount) {
        Coin pegoutOutputsAmount = releaseTransaction.getOutputSum();
        Coin releaseTransactionFees = releaseTransaction.getFee();
        Coin outputTotalAmount = releaseTransactionFees.add(pegoutOutputsAmount);
        assertEquals(expectedSentAmount, outputTotalAmount);

        Coin inputTotalAmount = releaseTransaction.getInputSum();
        assertEquals(inputTotalAmount, outputTotalAmount);
    }

}
