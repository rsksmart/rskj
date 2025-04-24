package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.ScriptOpCodes;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ErpRedeemScriptTestUtils {

    public static void assertMultiSigRedeemScript(byte[] redeemScript, List<BtcECKey> publicKeys, int startingIndex) {
        int threshold = publicKeys.size() / 2 + 1;

        // Next byte should equal M, from an M/N multisig
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(threshold)), redeemScript[startingIndex]);

        int pubKeysIndex = startingIndex + 1;
        for (BtcECKey btcEcKey : publicKeys) {
            byte[] pubKey = btcEcKey.getPubKey();
            pubKeysIndex = assertPublicKeyAndReturnTheNextIndex(redeemScript, pubKey, pubKeysIndex);
        }

        // Next byte should equal N, from an M/N multisig
        int nIndex = pubKeysIndex;
        int actualN = redeemScript[nIndex];
        int expectedN = publicKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(expectedN)), actualN);

        // Next byte should equal OP_CHECKMULTISIG
        final int opCheckMultiSigIndex = nIndex + 1;
        assertEquals((byte) ScriptOpCodes.OP_CHECKMULTISIG, redeemScript[opCheckMultiSigIndex]);
    }

    public static int assertCsvValue(int csvValueLengthIndex, long csvValue, byte[] script) {
        // Next byte should equal csv value length
        int expectedCsvValueLength =  BigInteger.valueOf(csvValue).toByteArray().length;
        byte[] serializedCsvValue = Utils.signedLongToByteArrayLE(csvValue);

        assertEquals(expectedCsvValueLength, script[csvValueLengthIndex]);

        int csvValueStartIndex = csvValueLengthIndex + 1;
        for (int i = 0; i < expectedCsvValueLength; i++) {
            int currentCsvValueIndex = csvValueStartIndex + i;
            assertEquals(serializedCsvValue[i], script[currentCsvValueIndex]);
        }

        int opCheckSequenceVerifyIndex = csvValueStartIndex + expectedCsvValueLength;
        assertEquals((byte) ScriptOpCodes.OP_CHECKSEQUENCEVERIFY,
            script[opCheckSequenceVerifyIndex]);

        return opCheckSequenceVerifyIndex;
    }

    public static int assertPublicKeyAndReturnTheNextIndex(byte[] p2shp2wshErpCustomRedeemScriptProgram, byte[] expectedPubKey, int startingPubKeyIndex) {
        // First byte should have the pubKey size
        byte actualPubKeyLength = p2shp2wshErpCustomRedeemScriptProgram[startingPubKeyIndex];

        assertEquals(expectedPubKey.length, actualPubKeyLength);

        // Next should have the pubKey
        int pubKeyIndex = startingPubKeyIndex + 1;
        for (byte expectedCharacterPubKey : expectedPubKey) {
            assertEquals(expectedCharacterPubKey, p2shp2wshErpCustomRedeemScriptProgram[pubKeyIndex++]);
        }

        return pubKeyIndex;
    }
}
