package org.ethereum.core.transaction.parser.util;


import org.ethereum.core.exception.TransactionException;
import org.ethereum.core.transaction.parser.SignatureState;
import org.ethereum.core.transaction.parser.SignedSignature;
import org.ethereum.core.transaction.parser.UnsignedSignature;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLPList;

public final class Type0SignatureUtils {

    //Since EIP-155, we could encode chainId in V
    private static final byte CHAIN_ID_INC = 35;
    private static final byte LOWER_REAL_V = 27;

    private Type0SignatureUtils() {}


    public static SignatureState parseType0SignatureState(RLPList txFields, int vIndex, int rIndex, int sIndex) {
        byte[] vData = txFields.get(vIndex).getRLPData();

        if (vData == null) {
            return new UnsignedSignature(null);
        }

        if (vData.length != 1) {
            throw new TransactionException("Signature V is invalid");
        }

        byte v = vData[0];
        byte chainId = extractChainIdFromV(v);

        byte[] r = txFields.get(rIndex).getRLPData();
        byte[] s = txFields.get(sIndex).getRLPData();

        return new SignedSignature(chainId, ECDSASignature.fromComponents(r, s, getRealV(v)));
    }

    public static byte extractChainIdFromV(byte v) {
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return 0;
        }
        return (byte) (((0x00FF & v) - CHAIN_ID_INC) / 2);
    }

    public static byte getRealV(byte v) {
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return v;
        }
        int inc = 0;
        if ((int) v % 2 == 0) {
            inc = 1;
        }
        return (byte) (LOWER_REAL_V + inc);
    }

}

