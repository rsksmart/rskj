package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class BitcoinUtils {

    private static final Logger logger = LoggerFactory.getLogger(BitcoinUtils.class);
    private static final int FIRST_INPUT_INDEX = 0;

    private BitcoinUtils() { }

    public static Optional<Sha256Hash> getFirstInputSigHash(BtcTransaction btcTx){
        if (btcTx.getInputs().isEmpty()){
            return Optional.empty();
        }
        TransactionInput txInput = btcTx.getInput(FIRST_INPUT_INDEX);
        Optional<Script> redeemScript = extractRedeemScriptFromInput(txInput);

        return redeemScript.map(script -> btcTx.hashForSignature(
            FIRST_INPUT_INDEX,
            script,
            BtcTransaction.SigHash.ALL,
            false
        ));
    }

    public static Optional<Script> extractRedeemScriptFromInput(TransactionInput txInput) {
        Script inputScript = txInput.getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        if (chunks == null || chunks.isEmpty()) {
            return Optional.empty();
        }

        byte[] redeemScriptProgram = chunks.get(chunks.size() - 1).data;
        if (redeemScriptProgram == null) {
            return Optional.empty();
        }

        try {
            Script redeemScript = new Script(redeemScriptProgram);
            return Optional.of(redeemScript);
        } catch (ScriptException e) {
            logger.debug(
                "[extractRedeemScriptFromInput] Failed to extract redeem script from tx input {}. {}",
                txInput,
                e.getMessage()
            );
            return Optional.empty();
        }
    }

    public static void removeSignaturesFromTransactionWithP2shMultiSigInputs(BtcTransaction transaction) {
        if (transaction.hasWitness()) {
            String message = "Removing signatures from SegWit transactions is not allowed.";
            logger.error("[removeSignaturesFromTransactionWithP2shMultiSigInputs] {}", message);
            throw new IllegalArgumentException(message);
        }

        for (TransactionInput input : transaction.getInputs()) {
            Script inputRedeemScript = extractRedeemScriptFromInput(input)
                .orElseThrow(
                    () -> {
                        String message = "Cannot remove signatures from transaction inputs that do not have p2sh multisig input script.";
                        logger.error("[removeSignaturesFromTransactionWithP2shMultiSigInputs] {}", message);
                        return new IllegalArgumentException(message);
                    }
                );
            Script p2shScript = ScriptBuilder.createP2SHOutputScript(inputRedeemScript);
            Script emptyInputScript = p2shScript.createEmptyInputScript(null, inputRedeemScript);
            input.setScriptSig(emptyInputScript);
        }
    }

    public static void addInputFromMatchingOutputScript(BtcTransaction transaction, BtcTransaction sourceTransaction, Script expectedOutputScript) {
        List<TransactionOutput> outputs = sourceTransaction.getOutputs();
        searchForOutput(outputs, expectedOutputScript)
            .ifPresent(transaction::addInput);
    }

    public static Optional<TransactionOutput> searchForOutput(List<TransactionOutput> transactionOutputs, Script outputScriptPubKey) {
        return transactionOutputs.stream()
            .filter(output -> output.getScriptPubKey().equals(outputScriptPubKey))
            .findFirst();
    }
}
