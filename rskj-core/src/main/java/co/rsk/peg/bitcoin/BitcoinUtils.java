package co.rsk.peg.bitcoin;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_0;
import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_RETURN;
import static com.google.common.base.Preconditions.checkArgument;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
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
        if (!inputHasWitness(transaction, inputIndex)) {
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

    public static boolean inputHasWitness(BtcTransaction btcTx, int inputIndex) {
        TransactionWitness inputWitness = btcTx.getWitness(inputIndex);
        return !inputWitness.equals(TransactionWitness.getEmpty());
    }

    public static int getSigInsertionIndex(BtcTransaction tx, int inputIndex, Sha256Hash sigHash, BtcECKey signingKey) {
        if (!inputHasWitness(tx, inputIndex)) {
            Script inputScript = tx.getInput(inputIndex).getScriptSig();
            return inputScript.getSigInsertionIndex(sigHash, signingKey);
        }

        TransactionWitness inputWitness = tx.getWitness(inputIndex);
        return getSigInsertionIndexFromWitness(inputWitness, sigHash, signingKey);
    }

    private static int getSigInsertionIndexFromWitness(TransactionWitness inputWitness, Sha256Hash sigHash, BtcECKey signingKey) {
        Optional<Script> redeemScript = extractRedeemScriptFromInputWitness(inputWitness);
        checkArgument(redeemScript.isPresent());

        RedeemScriptParser redeemScriptParser = RedeemScriptParserFactory.get(redeemScript.get().getChunks());

        int sigInsertionIndex = 0;
        int keyIndexInRedeem = redeemScriptParser.findKeyInRedeem(signingKey);

        byte[] emptyByte = new byte[]{};
        // the pushes that should have the signatures
        // are between first one (empty byte for checkmultisig bug)
        // and second to last one (op_notif + redeem script)
        for (int i = 1; i < inputWitness.getPushCount() - 1; i ++) {
            byte[] push = inputWitness.getPush(i);
            Preconditions.checkNotNull(push);
            if (!Arrays.equals(push, emptyByte)) {
                if (keyIndexInRedeem < redeemScriptParser.findSigInRedeem(push, sigHash)) {
                    return sigInsertionIndex;
                }

                sigInsertionIndex++;
            }
        }

        return sigInsertionIndex;
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

        for (int i = 0; i < numberOfSignaturesRequiredToSpend; i++) {
            pushes.add(emptyByte);
        }

        pushes.add(emptyByte); // OP_NOTIF
        pushes.add(redeemScript.getProgram());
        return TransactionWitness.of(pushes);
    }

    public static void addSpendingFederationBaseScript(BtcTransaction btcTx, int inputIndex, Script redeemScript, int federationFormatVersion) {
        TransactionInput input = btcTx.getInput(inputIndex);

        if (federationFormatVersion != FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION.getFormatVersion()) {
            Script inputScript = createBaseInputScriptThatSpendsFromRedeemScript(redeemScript);
            input.setScriptSig(inputScript);
            return;
        }

        TransactionWitness witnessScript = createBaseWitnessThatSpendsFromErpRedeemScript(redeemScript);
        btcTx.setWitness(inputIndex, witnessScript);
        setSegwitScriptSig(input, redeemScript);
    }

    private static void setSegwitScriptSig(TransactionInput txIn, Script redeemScript) {
        byte[] hashedRedeemScript = Sha256Hash.hash(redeemScript.getProgram());
        Script segwitScriptSig = new ScriptBuilder().number(OP_0).data(hashedRedeemScript).build();
        Script oneChunkSegwitScriptSig = new ScriptBuilder().data(segwitScriptSig.getProgram()).build(); // we need it to be in one chunk

        txIn.setScriptSig(oneChunkSegwitScriptSig);
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

    public static Sha256Hash generateSigHashForSegwitTransactionInput(BtcTransaction btcTx, int inputIndex, Coin prevValue) {
        TransactionWitness inputWitness = btcTx.getWitness(inputIndex);
        return extractRedeemScriptFromInputWitness(inputWitness)
            .map(redeemScript -> btcTx.hashForWitnessSignature(inputIndex, redeemScript, prevValue, BtcTransaction.SigHash.ALL, false))
            .orElseThrow(() -> new IllegalArgumentException("Couldn't extract redeem script from segwit input"));
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

    public static void signInput(BtcTransaction btcTx, int inputIndex, TransactionSignature signature, int sigInsertionIndex, Script outputScript) {
        if (!inputHasWitness(btcTx, inputIndex)) {
            // put signature in script sig
            TransactionInput input = btcTx.getInput(inputIndex);
            Script inputScript = input.getScriptSig();
            Script inputScriptWithSignature =
                outputScript.getScriptSigWithSignature(inputScript, signature.encodeToBitcoin(), sigInsertionIndex);
            input.setScriptSig(inputScriptWithSignature);
            return;
        }

        // put signature in witness
        TransactionWitness inputWitness = btcTx.getWitness(inputIndex);

        int sigsPrefixCount = 1;
        int sigsSuffixCount = 1;
        List<byte[]> inputWitnessPushesWithSignature = updateWitnessWithSignature(inputWitness, signature.encodeToBitcoin(), sigInsertionIndex, sigsPrefixCount, sigsSuffixCount);
        inputWitness = TransactionWitness.of(inputWitnessPushesWithSignature);
        btcTx.setWitness(inputIndex, inputWitness);
    }

    public static List<byte[]> updateWitnessWithSignature(TransactionWitness inputWitness, byte[] signature, int targetIndex, int sigsPrefixCount, int sigsSuffixCount) {

        int totalChunks = inputWitness.getPushCount();

        byte[] emptyByte = new byte[] {};
        byte[] secondToLastPush = inputWitness.getPush(totalChunks - sigsSuffixCount - 1);

        boolean hasMissingSigs = Arrays.equals(secondToLastPush, emptyByte);
        Preconditions.checkArgument(hasMissingSigs, "ScriptSig is already filled with signatures");

        List<byte[]> firstPushes = new ArrayList<>();
        for (int pushIndex = 0; pushIndex < sigsPrefixCount; pushIndex++) {
            byte[] push = inputWitness.getPush(pushIndex);
            firstPushes.add(push);
        }
        List<byte[]> finalPushes = new ArrayList<>(firstPushes);

        int pos = 0;
        boolean inserted = false;

        List<byte[]> secondPushes = new ArrayList<>();
        for (int pushIndex = sigsPrefixCount; pushIndex < totalChunks - sigsSuffixCount; pushIndex++) {
            byte[] push = inputWitness.getPush(pushIndex);
            secondPushes.add(push);
        }
        Iterator<byte[]> var11 = secondPushes.iterator();

        byte[] push;
        while (var11.hasNext()) {
            push = var11.next();
            if (pos == targetIndex) {
                inserted = true;
                finalPushes.add(signature);
                ++pos;
            }


            if (!Arrays.equals(push, emptyByte)) {
                finalPushes.add(push);
                ++pos;
            }
        }

        for(; pos < totalChunks - sigsPrefixCount - sigsSuffixCount; ++pos) {
            if (pos == targetIndex) {
                inserted = true;
                finalPushes.add(signature);
            } else {
                finalPushes.add(emptyByte);
            }
        }

        List<byte[]> thirdPushes = new ArrayList<>();
        for (int pushIndex = totalChunks - sigsSuffixCount; pushIndex < totalChunks; pushIndex++) {
            push = inputWitness.getPush(pushIndex);
            thirdPushes.add(push);
        }
        var11 = thirdPushes.iterator();

        while (var11.hasNext()) {
            push = var11.next();
            finalPushes.add(push);
        }

        Preconditions.checkState(inserted);
        return finalPushes;
    }
}
