package co.rsk.peg;

import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertP2shP2wshWitnessWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static co.rsk.peg.bitcoin.BitcoinUtils.BTC_TX_VERSION_1;
import static co.rsk.peg.bitcoin.BitcoinUtils.BTC_TX_VERSION_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.constants.BridgeConstants;
import java.util.List;
import java.util.function.Predicate;

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

    public static void assertOneMigrationTxOutput(
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

    public static void assertMultipleMigrationTxOutputs(
        BtcTransaction migrationTransaction,
        Coin migratedAmount,
        Address destination,
        NetworkParameters networkParameters,
        int expectedNumberOfOutputs,
        BridgeConstants bridgeConstants
    ) {
        List<TransactionOutput> migrationTransactionOutputs = migrationTransaction.getOutputs();
        assertEquals(expectedNumberOfOutputs, migrationTransactionOutputs.size());
        assertDestinationAddress(migrationTransactionOutputs, destination, networkParameters);
        assertOutputsWithNoChange(migrationTransaction, migratedAmount);
        assertEachOutputValueForMultipleOutputs(
            migrationTransaction,
            migratedAmount,
            expectedNumberOfOutputs,
            bridgeConstants
        );
    }

    private static void assertEachOutputValueForMultipleOutputs(
        BtcTransaction migrationTransaction,
        Coin migratedAmount,
        int expectedNumberOfOutputs,
        BridgeConstants bridgeConstants
    ) {
        List<TransactionOutput> outputs = migrationTransaction.getOutputs();
        Coin fees = migrationTransaction.getFee();
        Coin[] feeDistribution = fees.divideAndRemainder(expectedNumberOfOutputs);
        Coin feePerOutput = feeDistribution[0];
        Coin feeRemainder = feeDistribution[1];

        Coin migrationOutputValue = bridgeConstants.getMigrationValueForMultipleOutputsInBtc();
        Coin firstOutputFee = feePerOutput.add(feeRemainder);
        assertEquals(migrationOutputValue, outputs.get(0).getValue().add(firstOutputFee));

        for (int i = 1; i < expectedNumberOfOutputs - 1; i++) {
            assertEquals(migrationOutputValue, outputs.get(i).getValue().add(feePerOutput));
        }

        Coin accumulatedValue = migrationOutputValue.multiply(expectedNumberOfOutputs - 1);
        Coin expectedValueInLastOutput = migratedAmount.subtract(accumulatedValue);
        Coin lastOutput = outputs.get(expectedNumberOfOutputs - 1).getValue();
        assertEquals(expectedValueInLastOutput, lastOutput.add(feePerOutput));
    }

    public static void assertDestinationAddress(List<TransactionOutput> outputs, Address expectedDestinationAddress, NetworkParameters networkParams) {
        for (TransactionOutput output : outputs) {
            Address destinationAddress = output.getScriptPubKey().getToAddress(networkParams);
            assertEquals(expectedDestinationAddress, destinationAddress);
        }
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

    public static void assertBtcTxVersionIs1(BtcTransaction releaseTransaction) {
        assertEquals(BTC_TX_VERSION_1, releaseTransaction.getVersion());
    }

    public static void assertBtcTxVersionIs2(BtcTransaction btcTransaction) {
        assertEquals(BTC_TX_VERSION_2, btcTransaction.getVersion());
    }

    public static void assertOutputsWithDustChange(BtcTransaction releaseTransaction,
        List<TransactionOutput> releaseTransactionChangeOutputs,
        Coin requestedAmount) {
        Coin inputTotalAmount = releaseTransaction.getInputSum();
        Coin originalChangeAmount = inputTotalAmount.subtract(requestedAmount);
        assertTrue(isDust(originalChangeAmount));

        Coin amountToGetNonDustValue = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.subtract(originalChangeAmount);
        Coin amountToSend = requestedAmount.subtract(amountToGetNonDustValue);

        assertOutputsUserAndChangeValues(releaseTransaction, releaseTransactionChangeOutputs, amountToSend, MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);
    }

    public static void assertOutputsWithNonDustChange(BtcTransaction releaseTransaction,
        List<TransactionOutput> releaseTransactionChangeOutputs,
        Coin requestedAmount) {
        Coin inputTotalAmount = releaseTransaction.getInputSum();
        Coin expectedChangeAmount = inputTotalAmount.subtract(requestedAmount);
        assertOutputsUserAndChangeValues(releaseTransaction, releaseTransactionChangeOutputs, requestedAmount, expectedChangeAmount);
    }

    private static void assertOutputsUserAndChangeValues(BtcTransaction releaseTransaction,
        List<TransactionOutput> releaseTransactionChangeOutputs,
        Coin amountToSend,
        Coin expectedChangeAmount) {
        Coin changeOutputsAmount = getOutputsAmount(releaseTransactionChangeOutputs);
        assertEquals(expectedChangeAmount, changeOutputsAmount);

        Coin userOutputsAmount = releaseTransaction.getOutputSum().subtract(changeOutputsAmount);
        Coin releaseTransactionFees = releaseTransaction.getFee();
        Coin userOutputsAndFeesAmount = releaseTransactionFees.add(userOutputsAmount);
        assertEquals(amountToSend, userOutputsAndFeesAmount);
        Coin inputTotalAmount = releaseTransaction.getInputSum();
        assertEquals(inputTotalAmount, userOutputsAndFeesAmount.add(changeOutputsAmount));
    }

    private static Coin getOutputsAmount(List<TransactionOutput> outputs) {
        return outputs.stream()
            .map(TransactionOutput::getValue)
            .reduce(Coin::add)
            .orElse(Coin.ZERO);
    }

    private static boolean isDust(Coin expectedChangeAmount) {
        return expectedChangeAmount.compareTo(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT) < 0;
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

    /** Input count, script format, and selected-UTXO alignment for peg-out / batched flows. */
    public static void assertReleaseTxInputsStandardMultisig(
        BtcTransaction tx,
        int expectedInputCount,
        Script federationRedeemScript,
        List<UTXO> federationUtxos,
        List<UTXO> selectedUtxos) {
        List<TransactionInput> inputs = tx.getInputs();
        assertEquals(expectedInputCount, inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            tx,
            federationRedeemScript,
            federationUtxos
        );
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    public static void assertReleaseTxInputsP2shErp(
        BtcTransaction tx,
        int expectedInputCount,
        Script federationRedeemScript,
        List<UTXO> federationUtxos,
        List<UTXO> selectedUtxos) {
        List<TransactionInput> inputs = tx.getInputs();
        assertEquals(expectedInputCount, inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(tx, federationRedeemScript, federationUtxos);
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
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

    /**
     * Like {@link #assertReleaseTxInputsStandardMultisig} but for migration: all retiring federation UTXOs
     * are spent and {@code selectedUtxos} must match that set exactly.
     */
    public static void assertMigrationReleaseTxInputsStandardMultisig(
        BtcTransaction migrationTransaction,
        Script retiringFederationRedeemScript,
        List<UTXO> retiringFederationUtxos,
        List<UTXO> selectedUtxos) {
        List<TransactionInput> inputs = migrationTransaction.getInputs();
        assertEquals(retiringFederationUtxos.size(), inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            migrationTransaction,
            retiringFederationRedeemScript,
            retiringFederationUtxos
        );
        assertEquals(retiringFederationUtxos, selectedUtxos);
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    public static void assertMigrationReleaseTxInputsP2shErp(
        BtcTransaction migrationTransaction,
        Script retiringFederationRedeemScript,
        List<UTXO> retiringFederationUtxos,
        List<UTXO> selectedUtxos) {
        List<TransactionInput> inputs = migrationTransaction.getInputs();
        assertEquals(retiringFederationUtxos.size(), inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
            migrationTransaction, retiringFederationRedeemScript, retiringFederationUtxos);
        assertEquals(retiringFederationUtxos, selectedUtxos);
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    public static void assertMigrationReleaseTxInputsP2shP2wshErp(
        BtcTransaction migrationTransaction,
        Script retiringFederationRedeemScript,
        List<UTXO> retiringFederationUtxos,
        List<UTXO> selectedUtxos) {
        assertReleaseTxInputsP2shP2wshErp(
            migrationTransaction,
            retiringFederationRedeemScript,
            retiringFederationUtxos,
            selectedUtxos,
            retiringFederationUtxos.size()
        );
        assertEquals(retiringFederationUtxos, selectedUtxos);
    }

    public static void assertReleaseTxNumberOfOutputs(
        int expectedNumberOfOutputs,
        List<TransactionOutput> releaseTransactionOutputs
    ) {
        int actualNumberOfOutputs = releaseTransactionOutputs.size();
        assertEquals(expectedNumberOfOutputs, actualNumberOfOutputs);
    }
}
