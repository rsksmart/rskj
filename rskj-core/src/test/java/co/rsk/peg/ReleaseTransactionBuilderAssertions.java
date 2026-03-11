package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;

import java.util.List;
import java.util.function.Predicate;

import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_1;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertP2shP2wshWitnessWithoutSignaturesHasProperFormat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
