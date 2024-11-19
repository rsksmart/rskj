package co.rsk.peg.bitcoin;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_RETURN;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.*;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.util.*;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BitcoinUtils {
    protected static final byte[]  WITNESS_COMMITMENT_HEADER = Hex.decode("aa21a9ed");
    protected static final int WITNESS_COMMITMENT_LENGTH = WITNESS_COMMITMENT_HEADER.length + Sha256Hash.LENGTH;
    private static final int MINIMUM_WITNESS_COMMITMENT_SIZE = WITNESS_COMMITMENT_LENGTH + 2; // 1 extra by for OP_RETURN and another one for data length
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

    public static Sha256Hash getMultiSigTransactionHashWithoutSignatures(NetworkParameters networkParameters, BtcTransaction transaction) {
        Sha256Hash transactionHash = transaction.getHash();

        if (!transaction.hasWitness()) {
            BtcTransaction transactionCopyWithoutSignatures = new BtcTransaction(networkParameters, transaction.bitcoinSerialize()); // this is needed to not remove signatures from the actual tx
            BitcoinUtils.removeSignaturesFromTransactionWithP2shMultiSigInputs(transactionCopyWithoutSignatures);
            transactionHash = transactionCopyWithoutSignatures.getHash();
        }

        return transactionHash;
    }

    private static void removeSignaturesFromTransactionWithP2shMultiSigInputs(BtcTransaction transaction) {
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

    public static Script createBaseP2SHInputScriptThatSpendsFromRedeemScript(Script redeemScript) {
        Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        return outputScript.createEmptyInputScript(null, redeemScript);
    }

    public static Optional<TransactionOutput> searchForOutput(List<TransactionOutput> transactionOutputs, Script outputScriptPubKey) {
        return transactionOutputs.stream()
            .filter(output -> output.getScriptPubKey().equals(outputScriptPubKey))
            .findFirst();
    }

    public static Sha256Hash generateSigHashForP2SHTransactionInput(BtcTransaction btcTx, int inputIndex) {
        return Optional.ofNullable(btcTx.getInput(inputIndex))
            .flatMap(BitcoinUtils::extractRedeemScriptFromInput)
            .map(redeemScript -> btcTx.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false))
            .orElseThrow(() -> new IllegalArgumentException("Couldn't extract redeem script from p2sh input"));
    }

    public static Optional<Sha256Hash> findWitnessCommitment(BtcTransaction tx) {
        Preconditions.checkState(tx.isCoinBase());
        // If more than one witness commitment, take the last one as the valid one
        List<TransactionOutput> outputsReversed = Lists.reverse(tx.getOutputs());

        for (TransactionOutput output : outputsReversed) {
            Script scriptPubKey = output.getScriptPubKey();
            if (isWitnessCommitment(scriptPubKey)) {
                Sha256Hash witnessCommitment = extractWitnessCommitmentHash(scriptPubKey);
                return Optional.of(witnessCommitment);
            }
        }

        return Optional.empty();
    }

    private static boolean isWitnessCommitment(Script scriptPubKey) {
        byte[] scriptPubKeyProgram = scriptPubKey.getProgram();

        return scriptPubKeyProgram.length >= MINIMUM_WITNESS_COMMITMENT_SIZE
            && hasCommitmentStructure(scriptPubKeyProgram);
    }

    private static boolean hasCommitmentStructure(byte[] scriptPubKeyProgram) {
        return scriptPubKeyProgram[0] == OP_RETURN
            && scriptPubKeyProgram[1] == WITNESS_COMMITMENT_LENGTH
            && hasWitnessCommitmentHeader(Arrays.copyOfRange(scriptPubKeyProgram, 2, 6));
    }

    private static boolean hasWitnessCommitmentHeader(byte[] header) {
        return Arrays.equals(header, WITNESS_COMMITMENT_HEADER);
    }

    /**
     * Retrieves the hash from a segwit commitment (in an output of the coinbase transaction).
     */
    private static Sha256Hash extractWitnessCommitmentHash(Script scriptPubKey) {
        byte[] scriptPubKeyProgram = scriptPubKey.getProgram();
        Preconditions.checkState(scriptPubKeyProgram.length >= MINIMUM_WITNESS_COMMITMENT_SIZE);

        final int WITNESS_COMMITMENT_HASH_START = 6; // 4 bytes for header + OP_RETURN + data length
        byte[] witnessCommitmentHash = Arrays.copyOfRange(
            scriptPubKeyProgram,
            WITNESS_COMMITMENT_HASH_START,
            MINIMUM_WITNESS_COMMITMENT_SIZE
        );

        return Sha256Hash.wrap(witnessCommitmentHash);
    }
}
