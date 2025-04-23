package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.ScriptOpCodes;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ErpRedeemScriptTestUtils {
    public static void assertEmergencyRedeemScript(byte[] redeemScript, List<BtcECKey> emergencyPublicKeys, int lastScriptIndex, int erpThreshold) {
        // Next byte should equal M, from an M/N multisig
        int opNumMErpIndex = lastScriptIndex + 1;
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(erpThreshold)), redeemScript[opNumMErpIndex]);

        int erpPubKeysIndex = opNumMErpIndex + 1;
        for (BtcECKey btcErpEcKey : emergencyPublicKeys) {
            byte actualErpKeyLength = redeemScript[erpPubKeysIndex++];

            byte[] erpPubKey = btcErpEcKey.getPubKey();
            assertEquals(erpPubKey.length, actualErpKeyLength);
            for (byte characterErpPubKey : erpPubKey) {
                assertEquals(characterErpPubKey, redeemScript[erpPubKeysIndex++]);
            }
        }

        // Next byte should equal N, from an M/N multisig
        int nERPIndex = erpPubKeysIndex;
        int actualNErpFederation = redeemScript[nERPIndex];
        int expectedNErpFederation = emergencyPublicKeys.size();
        assertEquals(ScriptOpCodes.getOpCode(String.valueOf(expectedNErpFederation)), actualNErpFederation);

        // Next byte should equal OP_CHECKMULTISIG
        final int opCheckMultiSigIndex = nERPIndex + 1;
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
}
