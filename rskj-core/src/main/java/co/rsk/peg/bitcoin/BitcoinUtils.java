package co.rsk.peg.bitcoin;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_RETURN;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.*;
import co.rsk.peg.federation.FederationFormatVersion;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.util.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BitcoinUtils {
    protected static final byte[]  WITNESS_COMMITMENT_HEADER = Hex.decode("aa21a9ed");
    protected static final int WITNESS_COMMITMENT_LENGTH = WITNESS_COMMITMENT_HEADER.length + Sha256Hash.LENGTH;
    private static final int MINIMUM_WITNESS_COMMITMENT_SIZE = WITNESS_COMMITMENT_LENGTH + 2; // 1 extra byte for OP_RETURN and another one for data length
    private static final Logger logger = LoggerFactory.getLogger(BitcoinUtils.class);
    private static final int FIRST_INPUT_INDEX = 0;

    private BitcoinUtils() { }

    public static Optional<Sha256Hash> getFirstInputSigHash(BtcTransaction btcTx){
        if (btcTx.getInputs().isEmpty()){
            return Optional.empty();
        }

        Optional<Script> redeemScript = extractRedeemScriptFromInput(btcTx, FIRST_INPUT_INDEX);
        return redeemScript.map(script -> btcTx.hashForSignature(
            FIRST_INPUT_INDEX,
            script,
            BtcTransaction.SigHash.ALL,
            false
        ));
    }

    public static Optional<Script> extractRedeemScriptFromInput(BtcTransaction transaction, int inputIndex) {
        TransactionWitness inputWitness = transaction.getWitness(inputIndex);

        if (inputWitness.equals(TransactionWitness.getEmpty())) {
            return extractRedeemScriptFromInputScriptSig(transaction.getInput(inputIndex));
        }

        return extractRedeemScriptFromInputWitness(transaction.getWitness(inputIndex));
    }

    private static Optional<Script> extractRedeemScriptFromInputScriptSig(TransactionInput txInput) {
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
                "[extractRedeemScriptFromInputScriptSig] Failed to extract redeem script from tx input {}. {}",
                txInput,
                e.getMessage()
            );
            return Optional.empty();
        }
    }

    private static Optional<Script> extractRedeemScriptFromInputWitness(TransactionWitness txInputWitness) {
        int witnessSize = txInputWitness.getPushCount();
        int redeemScriptIndex = witnessSize - 1;
        try {
            byte[] redeemScriptData = txInputWitness.getPush(redeemScriptIndex);
            Script redeemScript = new Script(redeemScriptData);
            return Optional.of(redeemScript);
        } catch (Exception e) {
            logger.debug(
                "[extractRedeemScriptFromInputWitness] Failed to extract redeem script from tx input {}. {}",
                txInputWitness,
                e.getMessage()
            );
            return Optional.empty();
        }
    }

    public static Sha256Hash getMultiSigTransactionHashWithoutSignatures(NetworkParameters networkParameters, BtcTransaction transaction) {
        if (!transaction.hasWitness()) {
            BtcTransaction transactionCopyWithoutSignatures = new BtcTransaction(networkParameters, transaction.bitcoinSerialize()); // this is needed to not remove signatures from the actual tx
            BitcoinUtils.removeSignaturesFromTransactionWithP2shMultiSigInputs(transactionCopyWithoutSignatures);
            return transactionCopyWithoutSignatures.getHash();
        }

        return transaction.getHash();
    }

    public static void removeSignaturesFromTransactionWithP2shMultiSigInputs(BtcTransaction transaction) {
        if (transaction.hasWitness()) {
            String message = "Removing signatures from SegWit transactions is not allowed.";
            logger.error("[removeSignaturesFromTransactionWithP2shMultiSigInputs] {}", message);
            throw new IllegalArgumentException(message);
        }

        List<TransactionInput> inputs = transaction.getInputs();
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            TransactionInput input = inputs.get(inputIndex);
            Script inputRedeemScript = extractRedeemScriptFromInput(transaction, inputIndex)
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

    public static Script createBaseInputScriptThatSpendsFromRedeemScript(Script redeemScript) {
        Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        return outputScript.createEmptyInputScript(null, redeemScript);
    }

    public static TransactionWitness createBaseWitnessThatSpendsFromErpRedeemScript(Script redeemScript) {
        int pushForEmptyByte = 1;
        int pushForOpNotif = 1;
        int pushForRedeemScript = 1;
        int numberOfSignaturesRequiredToSpend = redeemScript.getNumberOfSignaturesRequiredToSpend();
        int witnessSize = pushForRedeemScript + pushForOpNotif + numberOfSignaturesRequiredToSpend + pushForEmptyByte;

        List<byte[]> pushes = new ArrayList<>(witnessSize);
        byte[] emptyByte = {};
        pushes.add(emptyByte); // OP_0

        byte[] bytesForSignature = new byte[72];
        for (int i = 0; i < numberOfSignaturesRequiredToSpend; i++) {
            pushes.add(bytesForSignature);
        }

        pushes.add(emptyByte); // OP_NOTIF
        pushes.add(redeemScript.getProgram());
        return TransactionWitness.of(pushes);
    }

    public static void addSpendingFederationBaseScript(BtcTransaction btcTx, int inputIndex, Script redeemScript, int federationFormatVersion) {
        if (federationFormatVersion != FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION.getFormatVersion()) {
            Script inputScript = createBaseInputScriptThatSpendsFromRedeemScript(redeemScript);
            btcTx.getInput(inputIndex).setScriptSig(inputScript);
            return;
        }

        TransactionWitness witnessScript = createBaseWitnessThatSpendsFromErpRedeemScript(redeemScript);
        btcTx.setWitness(inputIndex, witnessScript);
    }

    public static Optional<TransactionOutput> searchForOutput(List<TransactionOutput> transactionOutputs, Script outputScriptPubKey) {
        return transactionOutputs.stream()
            .filter(output -> output.getScriptPubKey().equals(outputScriptPubKey))
            .findFirst();
    }

    public static Sha256Hash generateSigHashForP2SHTransactionInput(BtcTransaction btcTx, int inputIndex) {
        return Optional.ofNullable(btcTx.getInput(inputIndex))
            .flatMap(BitcoinUtils::extractRedeemScriptFromInputScriptSig)
            .map(redeemScript -> btcTx.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false))
            .orElseThrow(() -> new IllegalArgumentException("Couldn't extract redeem script from p2sh input"));
    }

    public static Optional<Sha256Hash> findWitnessCommitment(BtcTransaction tx, ActivationConfig.ForBlock activations) {
        Preconditions.checkState(tx.isCoinBase());
        // If more than one witness commitment, take the last one as the valid one
        List<TransactionOutput> outputsReversed = Lists.reverse(tx.getOutputs());

        for (TransactionOutput output : outputsReversed) {
            byte[] scriptPubKeyBytes = getOutputScriptPubKeyBytes(activations, output);

            if (isWitnessCommitment(scriptPubKeyBytes)) {
                Sha256Hash witnessCommitment = extractWitnessCommitmentHash(scriptPubKeyBytes);
                return Optional.of(witnessCommitment);
            }
        }

        return Optional.empty();
    }

    private static byte[] getOutputScriptPubKeyBytes(ActivationConfig.ForBlock activations, TransactionOutput output) {
        if (!activations.isActive(ConsensusRule.RSKIP460)) {
            /*
                Calling `getScriptPubKey` pre RSKIP460 keeps consensus by throwing a ScripException
                when the output has a non-standard format that bitcoinj-thin is not able to parse
            */
            return output.getScriptPubKey().getProgram();
        }
        return output.getScriptBytes();
    }

    private static boolean isWitnessCommitment(byte[] scriptPubKeyProgram) {
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
    private static Sha256Hash extractWitnessCommitmentHash(byte[] scriptPubKeyProgram) {
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
