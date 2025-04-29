package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import com.google.common.collect.Lists;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ErpRedeemScriptTestUtils {

    public static int calculateMultiSigLength(List<BtcECKey> keys) {
        int threshold = 1;
        int pubKeysNumber = keys.size();
        int pubKey = calculatePubKeyBytesLength(keys.get(0));
        int opN = 1;
        int opCheckMultisig = 1;
        return threshold + pubKeysNumber * pubKey + opN + opCheckMultisig;
    }

    private static int calculatePubKeyBytesLength(BtcECKey key) {
        int pubKeysLengthByte = 1;
        int pubKey = key.getPubKey().length;
        return pubKeysLengthByte + pubKey;
    }

    public static int calculateCSVValueLength(long csvValue) {
        int opElse = 1;
        int csvLengthByte = 1;
        int csvLength = BigInteger.valueOf(csvValue).toByteArray().length;
        int opCheckSequenceVerify = 1;
        int opDrop = 1;
        return opElse + csvLengthByte + csvLength + opCheckSequenceVerify + opDrop;
    }

    public static int calculateCustomRedeemScriptLength(List<BtcECKey> keys) {
        int opNotIf = 1;
        byte[] publicKey = keys.get(0).getPubKey();
        int opCheckSig = 1;
        int pubKeyLengthByte = 1;
        int pubKey = publicKey.length;
        int pubKeyAndCheckSigLength = pubKeyLengthByte + pubKey + opCheckSig;
        int customRedeemScriptLength = opNotIf + pubKeyAndCheckSigLength;

        int opSwap = 1;
        int opAdd = 1;
        customRedeemScriptLength += (opSwap + pubKeyAndCheckSigLength + opAdd) * (keys.size() - 1);

        int threshold = 1;
        int opNumEqual = 1;
        customRedeemScriptLength += (threshold + opNumEqual);

        return customRedeemScriptLength;
    }

    public static void assertCustomERPRedeemScript(byte[] p2shp2wshErpCustomRedeemScriptProgram, List<BtcECKey> defaultKeys) {
        /*
         * Retrieving the federation public keys returns them in lexicographical order. The same happens with the
         * corresponding signatures. However, the signatures provided are in reverse order than the public keys
         * in the redeem script. Therefore, we are pushing the keys in reverse order to keep the signatures unmodified.
         * This is because when evaluating the script, the push operations are added at the bottom of the stack and
         * compared against the last element in it (i.e., the one that is right above the operation). So the first
         * pushed operation will be compared against the bottom element of the stack.
         */
        List<BtcECKey> reversedDefaultKeys = Lists.reverse(defaultKeys);
        int defaultThreshold = defaultKeys.size() / 2 + 1;

        // redeemScript - First opcode should be the OP_NOTIF
        int programCounter = 0;
        byte actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[programCounter++];
        assertEquals((byte) ScriptOpCodes.OP_NOTIF, actualOpCode);

        // defaultCustomRedeemScript - First bytes should be the PubKey and OP_CHECKSIG
        // It checks the first pubKey separately, since it is not preceded by an OP_SWAP
        BtcECKey expectedPubKey = reversedDefaultKeys.get(0);
        int pubKeyEnd = programCounter + calculatePubKeyBytesLength(expectedPubKey);
        byte[] expectedPubKeyBytes = expectedPubKey.getPubKey();
        byte[] pubKeyProgram = Arrays.copyOfRange(p2shp2wshErpCustomRedeemScriptProgram, programCounter, pubKeyEnd);
        assertPublicKeyInScript(pubKeyProgram, expectedPubKeyBytes);
        programCounter = pubKeyEnd;

        // After the pubKey there should be an OP_CHECKSIG for the pubKey
        actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[programCounter++];
        assertEquals((byte) ScriptOpCodes.OP_CHECKSIG, actualOpCode);

        reversedDefaultKeys = reversedDefaultKeys.subList(1, reversedDefaultKeys.size());

        for (BtcECKey pubKey : reversedDefaultKeys) {
            // defaultCustomRedeemScript - After the OP_CHECKSIG opcode should be the OP_SWAP for the PubKey
            actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[programCounter++];
            assertEquals((byte) ScriptOpCodes.OP_SWAP, actualOpCode);
            // defaultCustomRedeemScript - After the OP_SWAP there should be the pubKey and the OP_CHECKSIG

            pubKeyEnd = programCounter + calculatePubKeyBytesLength(pubKey);
            expectedPubKeyBytes = pubKey.getPubKey();
            pubKeyProgram = Arrays.copyOfRange(p2shp2wshErpCustomRedeemScriptProgram, programCounter, pubKeyEnd);
            assertPublicKeyInScript(pubKeyProgram, expectedPubKeyBytes);
            programCounter = pubKeyEnd;

            // After the pubKey there should be an OP_CHECKSIG for the pubKey
            actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[programCounter++];
            assertEquals((byte) ScriptOpCodes.OP_CHECKSIG, actualOpCode);

            // defaultCustomRedeemScript - After the OP_CHECKSIG opcode there should be the OP_ADD
            // to check total of signatures provided
            actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[programCounter++];
            assertEquals((byte) ScriptOpCodes.OP_ADD, actualOpCode);
        }

        // defaultRedeemScript - After the signatures there should be the number of signatures expected
        byte actualThreshold = p2shp2wshErpCustomRedeemScriptProgram[programCounter++];
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(defaultThreshold)), actualThreshold);

        // defaultCustomRedeemScript - Finally, there should be the OP_NUMEQUAL
        actualOpCode = p2shp2wshErpCustomRedeemScriptProgram[programCounter];
        assertEquals((byte) ScriptOpCodes.OP_NUMEQUAL, actualOpCode);
    }

    private static void assertPublicKeyInScript(byte[] p2shp2wshErpCustomRedeemScriptProgram, byte[] expectedPubKey) {
        int pubKeyIndex = 0;
        // First byte should have the pubKey size
        byte actualPubKeyLength = p2shp2wshErpCustomRedeemScriptProgram[pubKeyIndex++];
        assertEquals(expectedPubKey.length, actualPubKeyLength);

        // Next, it should have the public key
        for (byte expectedCharacterPubKey : expectedPubKey) {
            assertEquals(expectedCharacterPubKey, p2shp2wshErpCustomRedeemScriptProgram[pubKeyIndex++]);
        }
    }

    public static void assertCsvValueSection(byte[] csvScript, long csvValue) {
        // redeemScript - Next byte should equal OP_ELSE
        int programCounter = 0;
        assertEquals((byte) ScriptOpCodes.OP_ELSE, csvScript[programCounter++]);

        // redeemScript - Next bytes should equal the csv value in bytes
        int expectedCsvValueLength =  BigInteger.valueOf(csvValue).toByteArray().length;
        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);

        assertEquals(expectedCsvValueLength, csvScript[programCounter++]);

        for (int i = 0; i < expectedCsvValueLength; i++) {
            int currentCsvValueIndex = programCounter + i;
            assertEquals(serializedCsvValue[i], csvScript[currentCsvValueIndex]);
        }

        programCounter = programCounter + expectedCsvValueLength;
        assertEquals((byte) ScriptOpCodes.OP_CHECKSEQUENCEVERIFY,
            csvScript[programCounter++]);

        // redeemScript - Next bytes should equal OP_DROP
        assertEquals((byte) ScriptOpCodes.OP_DROP, csvScript[programCounter]);
    }

    public static void assertNMultiSig(byte[] nMultiSigProgram, List<BtcECKey> pubKeys) {
        int programCounter = 0;

        // ErpRedeemScript - Next bytes should equal the emergency redeem script
        int threshold = pubKeys.size() / 2 + 1;

        // Next byte should equal M, from an M/N multisig
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(threshold)), nMultiSigProgram[programCounter++]);

        for (BtcECKey btcEcKey : pubKeys) {
            byte[] expectedPubKeyBytes = btcEcKey.getPubKey();
            int pubKeyEnd = programCounter + calculatePubKeyBytesLength(btcEcKey);
            byte[] pubKeyProgram = Arrays.copyOfRange(nMultiSigProgram, programCounter, pubKeyEnd);
            assertPublicKeyInScript(pubKeyProgram, expectedPubKeyBytes);
            programCounter = pubKeyEnd;
        }

        // Next byte should equal N, from an M/N multisig
        int actualN = nMultiSigProgram[programCounter++];
        int expectedN = pubKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(expectedN)), actualN);

        // Next byte should equal OP_CHECKMULTISIG
        assertEquals((byte) ScriptOpCodes.OP_CHECKMULTISIG, nMultiSigProgram[programCounter]);
    }
}
