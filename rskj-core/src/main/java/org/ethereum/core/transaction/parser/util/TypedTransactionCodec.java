package org.ethereum.core.transaction.parser.util;

import co.rsk.util.HexUtils;
import org.ethereum.core.transaction.parser.SignatureState;
import org.ethereum.core.transaction.parser.SignedSignature;
import org.ethereum.core.transaction.parser.UnsignedSignature;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLPList;

import java.math.BigInteger;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public final class TypedTransactionCodec {

    private static final byte LOWER_REAL_V = 27;

    private TypedTransactionCodec() {
    }


    public static SignatureState parseTypedSignatureState(RLPList txFields,
                                                      int chainIdIndex,
                                                      int yParityIndex,
                                                      int rIndex,
                                                      int sIndex) {
        byte[] r = txFields.get(rIndex).getRLPData();
        byte[] s = txFields.get(sIndex).getRLPData();

        if (r == null && s == null) {
            byte chainId = parseTypedTxChainId(txFields.get(chainIdIndex).getRLPData());
            return new UnsignedSignature(chainId);
        }

        if (r == null || s == null) {
            throw new IllegalArgumentException("Typed transaction signature is incomplete");
        }
        byte yParity = parseTypedYParity(txFields.get(yParityIndex).getRLPData());
        byte v = (byte) (LOWER_REAL_V + yParity);
        byte chainId = parseTypedTxChainId(txFields.get(chainIdIndex).getRLPData());
        return new SignedSignature(chainId, ECDSASignature.fromComponents(r, s, v));
    }

    public static byte parseRequiredTypedChainId(String chainIdHex) {
        if (chainIdHex == null) {
            throw invalidParamError("Typed transaction requires chainId");
        }

        return parseTypedTxChainId(HexUtils.strHexOrStrNumberToByteArray(chainIdHex));
    }

    private static byte parseTypedYParity(byte[] yParityData) {
        byte yParity = (yParityData != null && yParityData.length > 0) ? yParityData[0] : 0;
        if (yParity != 0 && yParity != 1) {
            throw new IllegalArgumentException("Typed transaction yParity must be 0 or 1, got: " + (yParity & 0xFF));
        }
        return yParity;
    }


    /**
     * Parses the chain ID for typed transactions (Type 1 / Type 2).
     * Per EIP-2718, the chain ID must be a canonical integer that fits in a single unsigned byte
     * (values 1–255). A zero chain ID is rejected: typed transactions require a chain ID.
     * Values larger than 255 are rejected to prevent cross-chain replay via silent truncation.
     */
    private static byte parseTypedTxChainId(byte[] chainIdData) {
        if (chainIdData == null || chainIdData.length == 0) {
            throw new IllegalArgumentException("Typed transaction chainId must not be zero or absent");
        }
        BigInteger chainIdValue = new BigInteger(1, chainIdData);
        if (chainIdValue.signum() == 0) {
            throw new IllegalArgumentException("Typed transaction chainId must not be zero");
        }
        if (chainIdValue.compareTo(BigInteger.valueOf(255)) > 0) {
            throw new IllegalArgumentException("Typed transaction chainId exceeds maximum supported value of 255, got: " + chainIdValue);
        }
        return chainIdValue.byteValue();
    }
}
