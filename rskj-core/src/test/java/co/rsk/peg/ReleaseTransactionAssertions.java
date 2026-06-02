package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;

import java.util.List;
import java.util.function.Predicate;

import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertP2shP2wshWitnessWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinUtils.BTC_TX_VERSION_2;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReleaseTransactionAssertions {

    private ReleaseTransactionAssertions() {
    }

    public static void assertOutputsWithNoChange(BtcTransaction btcTransaction, Coin expectedSentAmount) {
        Coin outputsAmount = btcTransaction.getOutputSum();
        Coin fees = btcTransaction.getFee();
        Coin totalAmountSent = fees.add(outputsAmount);
        assertEquals(expectedSentAmount, totalAmountSent);
        assertEquals(btcTransaction.getInputSum(), totalAmountSent);
    }

    public static void assertDestinationAddress(List<TransactionOutput> outputs, Address expectedDestinationAddress, NetworkParameters networkParams) {
        for (TransactionOutput output : outputs) {
            Address destinationAddress = output.getScriptPubKey().getToAddress(networkParams);
            assertEquals(expectedDestinationAddress, destinationAddress);
        }
    }

    public static void assertMigrationTxWithOnlyMigrationOutputs(
        BtcTransaction migrationTransaction,
        Coin migratedAmount,
        Address destination,
        NetworkParameters networkParameters
    ) {
        int expectedNumberOfOutputs = 1;
        List<TransactionOutput> migrationTransactionOutputs = migrationTransaction.getOutputs();
        assertEquals(expectedNumberOfOutputs, migrationTransactionOutputs.size());
        assertDestinationAddress(migrationTransactionOutputs, destination, networkParameters);
        assertOutputsWithNoChange(migrationTransaction, migratedAmount);
    }

    public static void assertInputIsFromFederationUTXOsWallet(TransactionInput input, List<UTXO> federationUtxos) {
        Predicate<UTXO> isUTXOAndReleaseInputFromTheSameOutpoint = utxo ->
            utxo.getHash().equals(input.getOutpoint().getHash())
                && utxo.getIndex() == input.getOutpoint().getIndex();
        long matchingUtxos = federationUtxos.stream()
            .filter(isUTXOAndReleaseInputFromTheSameOutpoint)
            .count();

        int expectedNumberOfUtxos = 1;
        assertEquals(expectedNumberOfUtxos, matchingUtxos);
    }

    public static void assertBtcTxVersionIs2(BtcTransaction btcTransaction) {
        assertEquals(BTC_TX_VERSION_2, btcTransaction.getVersion());
    }

    public static void assertSelectedUtxosBelongToTheInputs(List<UTXO> selectedUtxos, List<TransactionInput> inputs) {
        assertEquals(inputs.size(), selectedUtxos.size());
        for (UTXO selectedUtxo : selectedUtxos) {
            long matchingInputs = inputs.stream()
                .filter(input -> input.getOutpoint().getHash().equals(selectedUtxo.getHash()) &&
                    input.getOutpoint().getIndex() == selectedUtxo.getIndex())
                .count();
            assertEquals(1, matchingInputs);
        }
    }

    public static void assertReleaseTxInputsP2shP2wshErp(
        BtcTransaction releaseTx,
        Script federationRedeemScript,
        List<UTXO> federationUtxos,
        List<UTXO> selectedUtxos,
        int expectedInputCount) {
        List<TransactionInput> inputs = releaseTx.getInputs();
        assertEquals(expectedInputCount, inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
            releaseTx,
            federationRedeemScript,
            federationUtxos);
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    private static void assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
        BtcTransaction releaseTransaction,
        Script federationRedeemScript,
        List<UTXO> federationUtxos
    ) {
        List<TransactionInput> inputs = releaseTransaction.getInputs();
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            TransactionWitness witness = releaseTransaction.getWitness(inputIndex);
            assertP2shP2wshWitnessWithoutSignaturesHasProperFormat(witness, federationRedeemScript);
            assertInputIsFromFederationUTXOsWallet(inputs.get(inputIndex), federationUtxos);
        }
    }
}
