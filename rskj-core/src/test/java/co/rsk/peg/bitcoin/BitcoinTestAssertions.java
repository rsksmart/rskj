package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.script.ScriptOpCodes;

import java.util.List;

import static co.rsk.bitcoinj.script.ScriptOpCodes.OP_0;
import static co.rsk.peg.bitcoin.BitcoinUtils.extractHashedRedeemScriptProgramFromSegwitScriptSig;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class BitcoinTestAssertions {

    private static final int MIN_SIGNATURE_LENGTH = 71;
    private static final int MAX_SIGNATURE_LENGTH = 73;

    public static void assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat(Script scriptSig, Script redeemScript) {
        List<ScriptChunk> scriptSigChunks = scriptSig.getChunks();
        int numberOfSignaturesRequiredToSpend = redeemScript.getNumberOfSignaturesRequiredToSpend();
        int expectedChunkCount = numberOfSignaturesRequiredToSpend + 2; // OP_0 + redeem script
        assertEquals(expectedChunkCount, scriptSigChunks.size());

        int redeemScriptChunkIndex = scriptSigChunks.size() - 1;

        assertArrayEquals(redeemScript.getProgram(), scriptSigChunks.get(redeemScriptChunkIndex).data); // last chunk should be the redeem script

        List<ScriptChunk> scriptChunks = scriptSigChunks.subList(0, redeemScriptChunkIndex);
        for (ScriptChunk chunk : scriptChunks) { // all the other chunks should be zero
            assertEquals(ScriptOpCodes.OP_0, chunk.opcode);
        }
    }

    public static void assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat(Script scriptSig, Script redeemScript) {
        assertP2shErpScriptSigStructure(scriptSig, redeemScript);

        List<ScriptChunk> scriptSigChunks = scriptSig.getChunks();
        int numberOfSignaturesRequiredToSpend = redeemScript.getNumberOfSignaturesRequiredToSpend();
        int startIndex = 1; // First push is OP_0, next come the signatures

        // An empty chunk for each signature required to spend
        for (int i = startIndex; i <= numberOfSignaturesRequiredToSpend; i++) {
            ScriptChunk signatureChunk = scriptSigChunks.get(i);
            assertEquals(OP_0, signatureChunk.opcode);
        }
    }

    public static void assertP2shErpScriptSigStructure(Script scriptSig, Script redeemScript) {
        List<ScriptChunk> scriptSigChunks = scriptSig.getChunks();

        // Check size first
        int numberOfSignaturesRequiredToSpend = redeemScript.getNumberOfSignaturesRequiredToSpend();
        int expectedChunksCount = numberOfSignaturesRequiredToSpend + 3; // OP_0, OP_NOTIF param, redeem script
        assertEquals(expectedChunksCount, scriptSigChunks.size());

        // Check each chunk
        int chunkIndex = 0;

        // first chunk should be OP_0
        ScriptChunk firstChunk = scriptSigChunks.get(chunkIndex);
        assertEquals(OP_0, firstChunk.opcode);

        // Skip checking the signatures, other methods will handle that depending on whether signatures are expected or not
        chunkIndex += numberOfSignaturesRequiredToSpend;

        // An empty chunk for the OP_NOTIF param
        ScriptChunk flowChunk = scriptSigChunks.get(++chunkIndex);
        assertEquals(OP_0, flowChunk.opcode);

        // Finally, the redeem script program
        ScriptChunk redeemScriptChunk = scriptSigChunks.get(++chunkIndex);
        assertArrayEquals(redeemScript.getProgram(), redeemScriptChunk.data);
    }

    public static void assertScriptSigWithSignaturesHasProperFormat(Script scriptSig, Script redeemScript) {
        assertP2shErpScriptSigStructure(scriptSig, redeemScript);

        List<ScriptChunk> scriptSigChunks = scriptSig.getChunks();
        int numberOfSignaturesRequiredToSpend = redeemScript.getNumberOfSignaturesRequiredToSpend();
        int startIndex = 1; // First push is OP_0, next come the signatures

        // A non-empty chunk for each signature required to spend
        for (int i = startIndex; i < numberOfSignaturesRequiredToSpend; i++) {
            ScriptChunk signatureChunk = scriptSigChunks.get(i);
            int signatureLength = signatureChunk.data.length;
            assertAll(
                () -> assertTrue(signatureLength >= MIN_SIGNATURE_LENGTH, "Signature should be at least " + MIN_SIGNATURE_LENGTH + " bytes long"),
                () -> assertTrue(signatureLength <= MAX_SIGNATURE_LENGTH, "Signature should be at most " + MAX_SIGNATURE_LENGTH + " bytes long")
            );
        }
    }

    public static void assertP2shP2wshScriptWithoutSignaturesHasProperFormat(TransactionWitness witness, Script redeemScript) {
        assertP2shP2wshWitnessHasExpectedStructure(witness, redeemScript);

        int numberOfSignaturesRequiredToSpend = redeemScript.getNumberOfSignaturesRequiredToSpend();
        int startIndex = 1; // First push is OP_0, next come the signatures

        // An empty push for each signature required to spend
        for (int i = startIndex; i <= numberOfSignaturesRequiredToSpend; i++) {
            byte[] signaturePush = witness.getPush(i);
            assertArrayEquals(EMPTY_BYTE_ARRAY, signaturePush);
        }
    }

    public static void assertP2shP2wshScriptWithSignaturesHasProperFormat(TransactionWitness witness, Script redeemScript) {
        assertP2shP2wshWitnessHasExpectedStructure(witness, redeemScript);

        int numberOfSignaturesRequiredToSpend = redeemScript.getNumberOfSignaturesRequiredToSpend();
        int startIndex = 1; // First push is OP_0, next come the signatures

        for (int i = startIndex; i < numberOfSignaturesRequiredToSpend; i++) {
            byte[] signaturePush = witness.getPush(i);
            assertAll(
                () -> assertTrue(signaturePush.length >= MIN_SIGNATURE_LENGTH, "Signature should be at least " + MIN_SIGNATURE_LENGTH + " bytes long"),
                () -> assertTrue(signaturePush.length <= MAX_SIGNATURE_LENGTH, "Signature should be at most " + MAX_SIGNATURE_LENGTH + " bytes long")
            );
        }
    }

    public static void assertP2shP2wshWitnessHasExpectedStructure(TransactionWitness witness, Script redeemScript) {
        assertNotNull(witness);

        // Check size first
        int numberOfSignaturesRequiredToSpend = redeemScript.getNumberOfSignaturesRequiredToSpend();
        int expectedPushCount = numberOfSignaturesRequiredToSpend + 3; // OP_0, OP_NOTIF param, redeem script
        assertEquals(expectedPushCount, witness.getPushCount());

        // Check each push element
        int pushIndex = 0;

        // first push should be OP_0 (empty byte)
        byte[] firstPush = witness.getPush(pushIndex);
        assertArrayEquals(EMPTY_BYTE_ARRAY, firstPush);

        // Skip checking the signatures, other methods will handle that depending on whether signatures are expected or not
        pushIndex += numberOfSignaturesRequiredToSpend;

        // An empty push for the OP_NOTIF param
        byte[] flowPush = witness.getPush(++pushIndex);
        assertArrayEquals(EMPTY_BYTE_ARRAY, flowPush);

        // Finally, the redeem script program
        byte[] lastPush = witness.getPush(++pushIndex);
        assertArrayEquals(redeemScript.getProgram(), lastPush);
    }

    public static void assertSegwitScriptSigContainsHashedRedeemScript(Script segwitScriptSig, Script redeemScript) {
        List<ScriptChunk> chunks = segwitScriptSig.getChunks();
        assertEquals(1, chunks.size());

        ScriptChunk chunk = chunks.get(0);
        assertEquals(34, chunk.opcode); // OP_PUSHBYTES_34, 32 bytes from the redeem script hash + 2 for OP_0 and OP_PUSHBYTES_32

        byte[] segwitScriptSigProgram = segwitScriptSig.getProgram();
        assertEquals(35, segwitScriptSigProgram.length); // OP_PUSHBYTES_34 + the 34 bytes

        // Check the first byte is OP_PUSHBYTES_34
        assertEquals(34, segwitScriptSigProgram[0]);

        // Check the second byte is OP_0
        assertEquals(0, segwitScriptSigProgram[1]);

        // Check the hashed redeem script
        byte[] hashedRedeemScript = extractHashedRedeemScriptProgramFromSegwitScriptSig(segwitScriptSig);
        byte[] expectedHashedRedeemScript = Sha256Hash.hash(redeemScript.getProgram());
        assertArrayEquals(expectedHashedRedeemScript, hashedRedeemScript);
    }
}
