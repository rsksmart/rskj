package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;

import java.util.List;
import java.util.function.Predicate;

import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_1;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertP2shP2wshWitnessWithoutSignaturesHasProperFormat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ReleaseTransactionBuilderAssertions {

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

    public static void assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
        BtcTransaction releaseTransaction,
        Script federationRedeemScript,
        List<UTXO> federationUTXOs) {
        for (TransactionInput input : releaseTransaction.getInputs()) {
            Script scriptSig = input.getScriptSig();
            assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat(scriptSig, federationRedeemScript);
            assertInputIsFromFederationUTXOsWallet(input, federationUTXOs);
        }
    }

    public static void assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
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
                                                Address expectedDestinationAddress,
                                                NetworkParameters networkParameters) {
        for (TransactionOutput output : releaseTransactionOutputs) {
            Address destinationAddress = output.getScriptPubKey().getToAddress(networkParameters);
            assertEquals(expectedDestinationAddress, destinationAddress);
        }
    }

    public static void assertUserAndChangeOutputsValuesWhenOriginalChangeIsDust(BtcTransaction releaseTransaction,
                                                                                List<TransactionOutput> releaseTransactionChangeOutputs,
                                                                                Coin requestedAmount) {
        Coin inputTotalAmount = releaseTransaction.getInputSum();
        Coin originalChangeAmount = inputTotalAmount.subtract(requestedAmount);
        assertTrue(isDust(originalChangeAmount));

        Coin amountToGetNonDustValue = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.subtract(originalChangeAmount);
        requestedAmount = requestedAmount.subtract(amountToGetNonDustValue);

        assertUserAndChangeOutputsValues(releaseTransaction, releaseTransactionChangeOutputs, requestedAmount, MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);
    }

    public static void assertUserAndChangeOutputsValuesWhenOriginalChangeIsNonDust(BtcTransaction releaseTransaction,
                                                                                   List<TransactionOutput> releaseTransactionChangeOutputs,
                                                                                   Coin requestedAmount) {
        Coin inputTotalAmount = releaseTransaction.getInputSum();
        Coin expectedChangeAmount = inputTotalAmount.subtract(requestedAmount);
        assertUserAndChangeOutputsValues(releaseTransaction, releaseTransactionChangeOutputs, requestedAmount, expectedChangeAmount);
    }

    private static void assertUserAndChangeOutputsValues(BtcTransaction releaseTransaction,
                                                         List<TransactionOutput> releaseTransactionChangeOutputs,
                                                         Coin requestedAmount,
                                                         Coin expectedChangeAmount) {
        Coin changeOutputsAmount = getChangeOutputsAmount(releaseTransactionChangeOutputs);
        assertEquals(expectedChangeAmount, changeOutputsAmount);

        Coin userOutputsAmount = releaseTransaction.getOutputSum().subtract(changeOutputsAmount);
        Coin releaseTransactionFees = releaseTransaction.getFee();
        Coin userOutputsAndFeesAmount = releaseTransactionFees.add(userOutputsAmount);
        assertEquals(requestedAmount, userOutputsAndFeesAmount);
        Coin inputTotalAmount = releaseTransaction.getInputSum();
        assertEquals(inputTotalAmount, userOutputsAndFeesAmount.add(changeOutputsAmount));
    }

    private static Coin getChangeOutputsAmount(List<TransactionOutput> outputs) {
        return outputs.stream()
            .map(TransactionOutput::getValue)
            .reduce(Coin::add)
            .orElse(Coin.ZERO);
    }

    private static boolean isDust(Coin expectedChangeAmount) {
        return expectedChangeAmount.compareTo(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT) < 0;
    }

    public static void assertReleaseTxWithOnlyUserOutputsAmounts(BtcTransaction releaseTransaction,
                                                                 Coin expectedSentAmount) {
        Coin outputsAmount = releaseTransaction.getOutputSum();
        Coin fees = releaseTransaction.getFee();
        Coin totalAmountSent = fees.add(outputsAmount);
        assertEquals(expectedSentAmount, totalAmountSent);

        Coin inputTotalAmount = releaseTransaction.getInputSum();
        assertEquals(inputTotalAmount, totalAmountSent);
    }

    public static void assertReleaseTxNumberOfOutputs(int expectedNumberOfOutputs,
                                                      List<TransactionOutput> releaseTransactionOutputs) {
        int actualNumberOfOutputs = releaseTransactionOutputs.size();
        assertEquals(expectedNumberOfOutputs, actualNumberOfOutputs);
    }

}
