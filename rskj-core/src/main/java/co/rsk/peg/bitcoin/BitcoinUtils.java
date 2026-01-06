package co.rsk.peg.bitcoin;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_0;
import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_RETURN;

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
    protected static final byte[] WITNESS_COMMITMENT_HEADER = Hex.decode("aa21a9ed");
    protected static final int WITNESS_COMMITMENT_LENGTH = WITNESS_COMMITMENT_HEADER.length + Sha256Hash.LENGTH;
    private static final int MINIMUM_WITNESS_COMMITMENT_SIZE = WITNESS_COMMITMENT_LENGTH + 2; // 1 extra byte for OP_RETURN and another one for data length
    private static final Logger logger = LoggerFactory.getLogger(BitcoinUtils.class);
    private static final int FIRST_INPUT_INDEX = 0;

    private BitcoinUtils() { }

    public static Optional<Sha256Hash> getSigHashForPegoutIndex(BtcTransaction btcTx) {
        if (btcTx.getInputs().isEmpty()){
            return Optional.empty();
        }

        Optional<Script> redeemScript = extractRedeemScriptFromInput(btcTx, FIRST_INPUT_INDEX);
        // if we cannot extract the rs from the first input,
        // we can be sure that the tx is not a pegout.
        if (redeemScript.isEmpty()) {
            return Optional.empty();
        }

        try {
            // to be able to recognize a segwit pegout,
            // we need to manually get the tx without signatures,
            // since the legacy sig hash calculation impl
            // removes them from the script sig, not from the witness.
            BtcTransaction btcTxWithoutSignatures = getMultiSigTransactionWithoutSignatures(btcTx);
            Sha256Hash firstInputLegacySigHash = btcTxWithoutSignatures.hashForSignature(
                FIRST_INPUT_INDEX,
                redeemScript.get(),
                BtcTransaction.SigHash.ALL,
                false
            );
            return Optional.of(firstInputLegacySigHash);
        } catch (IllegalArgumentException e) {
            logger.warn("Tx {} is not a pegout. Reason: {}", btcTx.getHash(), e.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<Script> extractRedeemScriptFromInput(BtcTransaction transaction, int inputIndex) {
        if (!inputHasWitness(transaction, inputIndex)) {
            return extractRedeemScriptFromInputScriptSig(transaction.getInput(inputIndex));
        }

        return extractRedeemScriptFromInputWitness(transaction.getWitness(inputIndex));
    }

    /**
     * Extracts the redeem script from the input's scriptSig, if it's a P2SH input.
     *
     * For some P2PKH inputs, the scriptSig contains a public key as the last chunk of the scriptSig that is parsed as a redeem script.
     * This is a known issue, pending review for a permanent solution. In the meantime, always call `isSentToMultiSig()` on the returned redeem script
     *
     * @param txInput The input of the transaction from which to extract the redeem script.
     * @return An Optional containing the redeem script if it can be extracted, or an empty Optional if it cannot be extracted
     */
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

    public static int calculateBtcTxVirtualSize(BtcTransaction btcTx) {
        int baseSize = 0;
        List<TransactionInput> inputs = btcTx.getInputs();
        baseSize += 1; // input count
        for (TransactionInput input : inputs) {
            baseSize += input.bitcoinSerialize().length;
        }

        List<TransactionOutput> outputs = btcTx.getOutputs();
        baseSize += 1; // output count
        for (TransactionOutput output : outputs) {
            baseSize += output.bitcoinSerialize().length;
        }

        baseSize += 4; // version
        baseSize += 4; // locktime

        int totalSize = btcTx.bitcoinSerialize().length;
        return calculateVirtualBytes(totalSize, baseSize);
    }

    // As described in BIP141
    public static int calculateVirtualBytes(int totalSize, int baseSize) {
        int txWeight = totalSize + (baseSize * 3);
        return txWeight / 4;
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
        return inputWitness.getSigInsertionIndex(sigHash, signingKey);
    }

    /**
     * Returns the hash of a Bitcoin transaction that has all its inputs from a multiSig,
     * with the signatures removed.
     * If the transaction is legacy, the method returns the hash of the transaction
     * after removing all signatures from the inputs script sigs.
     * If the transaction is segwit, it simply returns the current transaction hash.
     *
     * @param transaction transaction
     * @return the hash of the transaction without signatures from the input
     */
    public static Sha256Hash getMultiSigTransactionHashWithoutSignatures(BtcTransaction transaction) {
        if (!transaction.hasWitness()) {
            BtcTransaction multiSigTransactionWithoutSignatures = getMultiSigTransactionWithoutSignatures(transaction);
            return multiSigTransactionWithoutSignatures.getHash();
        }

        return transaction.getHash();
    }

    /**
     * Returns a Bitcoin transaction that has all its inputs from a multiSig,
     * with the signatures removed.
     * @param transaction transaction
     * @return a transaction copy without the signatures
     */
    public static BtcTransaction getMultiSigTransactionWithoutSignatures(BtcTransaction transaction) {
        NetworkParameters networkParameters = transaction.getParams();
        BtcTransaction transactionCopy = new BtcTransaction(networkParameters, transaction.bitcoinSerialize()); // this is needed to not remove signatures from the original tx
        removeSignaturesFromMultiSigTransaction(transactionCopy);
        return transactionCopy;
    }

    /**
     * Removes the signatures of a Bitcoin transaction
     * that has all its inputs from a multiSig.
     * If the transaction is legacy, the method
     * removes all the signatures from the inputs script sigs.
     * If the transaction is segwit, it
     * removes all the signatures from the transaction witnesses.
     * If the transaction has one input that is not from a multiSig,
     * it throws an IAE.
     *
     * @param transaction transaction
     */
    public static void removeSignaturesFromMultiSigTransaction(BtcTransaction transaction) {
        List<TransactionInput> inputs = transaction.getInputs();
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            Script redeemScript = extractRedeemScriptFromInput(transaction, inputIndex)
                .filter(Script::isSentToMultiSig)
                .orElseThrow(() -> {
                    String message = "Cannot remove signatures from transaction inputs that do not have P2SH multisig input script.";
                    logger.error("[removeSignaturesFromMultiSigTransaction] {}", message);
                    return new IllegalArgumentException(message);
                });

            boolean inputHasWitness = inputHasWitness(transaction, inputIndex);
            if (inputHasWitness) {
                setSpendingBaseScriptSegwit(transaction, inputIndex, redeemScript);
            } else {
                setSpendingBaseScriptLegacy(transaction, inputIndex, redeemScript);
            }
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
        if (federationFormatVersion != FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION.getFormatVersion()) {
            setSpendingBaseScriptLegacy(btcTx, inputIndex, redeemScript);
            return;
        }
        setSpendingBaseScriptSegwit(btcTx, inputIndex, redeemScript);
    }

    protected static void setSpendingBaseScriptLegacy(BtcTransaction btcTx, int inputIndex, Script redeemScript) {
        TransactionInput input = btcTx.getInput(inputIndex);
        Script inputScript = createBaseInputScriptThatSpendsFromRedeemScript(redeemScript);
        input.setScriptSig(inputScript);
    }

    protected static void setSpendingBaseScriptSegwit(BtcTransaction btcTx, int inputIndex, Script redeemScript) {
        TransactionInput input = btcTx.getInput(inputIndex);
        TransactionWitness witnessScript = createBaseWitnessThatSpendsFromErpRedeemScript(redeemScript);
        btcTx.setWitness(inputIndex, witnessScript);

        Script segwitScriptSig = buildSegwitScriptSig(redeemScript);
        input.setScriptSig(segwitScriptSig);
    }

    public static Script buildSegwitScriptSig(Script redeemScript) {
        if (redeemScript == null) {
            throw new IllegalArgumentException("redeemScript must not be null");
        }
        // we need the hashed redeem script to be in one chunk
        byte[] hashedRedeemScript = Sha256Hash.hash(redeemScript.getProgram());
        Script segwitScriptSig = new ScriptBuilder()
            .number(OP_0)
            .data(hashedRedeemScript)
            .build();

        return new ScriptBuilder()
            .data(segwitScriptSig.getProgram())
            .build();
    }

    public static BtcTransaction getTransactionWithoutWitness(BtcTransaction btcTransaction) {
        NetworkParameters params = btcTransaction.getParams();
        BtcTransaction transactionCopy = new BtcTransaction(params, btcTransaction.bitcoinSerialize());

        for (int i = 0; i < transactionCopy.getInputs().size(); i++) {
            transactionCopy.setWitness(i, TransactionWitness.getEmpty());
        }
        return transactionCopy;
    }

    public static byte[] extractHashedRedeemScriptProgramFromSegwitScriptSig(Script segwitScriptSig) {
        if (segwitScriptSig == null) {
            throw new IllegalArgumentException("SegwitScriptSig must not be null");
        }
        byte[] segwitScriptSigProgram = segwitScriptSig.getProgram();
        // The whole program is [22 00 20 + redeemScriptHash], so we just need to skip the first 3 bytes.
        return Arrays.copyOfRange(segwitScriptSigProgram, 3, segwitScriptSigProgram.length);
    }

    public static Optional<TransactionOutput> searchForOutput(List<TransactionOutput> transactionOutputs, Script outputScriptPubKey) {
        return transactionOutputs.stream()
            .filter(output -> output.getScriptPubKey().equals(outputScriptPubKey))
            .findFirst();
    }

    public static Sha256Hash generateSigHashForLegacyTransactionInput(BtcTransaction btcTx, int inputIndex) {
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

    public static void signInput(
        BtcTransaction btcTx,
        int inputIndex,
        TransactionSignature signature,
        int sigInsertionIndex,
        Script outputScript
    ) {
        if (inputHasWitness(btcTx, inputIndex)) {
            addSignatureToWitness(btcTx, inputIndex, signature, sigInsertionIndex, outputScript);
            return;
        }
        addSignatureToScriptSig(btcTx, inputIndex, signature, sigInsertionIndex, outputScript);
    }

    private static void addSignatureToScriptSig(
        BtcTransaction btcTx,
        int inputIndex,
        TransactionSignature signature,
        int sigInsertionIndex,
        Script outputScript
    ) {
        Script inputScript = btcTx.getInput(inputIndex).getScriptSig();
        Script inputScriptWithSignature = outputScript.getScriptSigWithSignature(
            inputScript,
            signature.encodeToBitcoin(),
            sigInsertionIndex
        );
        btcTx.getInput(inputIndex).setScriptSig(inputScriptWithSignature);
    }

    private static void addSignatureToWitness(
        BtcTransaction btcTx,
        int inputIndex,
        TransactionSignature signature,
        int sigInsertionIndex,
        Script outputScript
    ) {
        TransactionWitness inputWitness = btcTx.getWitness(inputIndex);
        TransactionWitness inputWitnessWithSignature = inputWitness.updateWitnessWithSignature(
            outputScript,
            signature.encodeToBitcoin(),
            sigInsertionIndex
        );
        btcTx.setWitness(inputIndex, inputWitnessWithSignature);
    }
}
