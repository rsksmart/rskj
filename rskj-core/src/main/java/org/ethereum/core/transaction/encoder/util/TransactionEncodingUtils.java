package org.ethereum.core.transaction.encoder.util;

import org.ethereum.util.RLP;

public final class TransactionEncodingUtils {

    private static final byte[] EMPTY_ACCESS_LIST_RLP = new byte[]{(byte) 0xc0};

    private TransactionEncodingUtils() {}

    /** RLP-encoded nonce element (null or single zero byte → empty scalar). */
    public static byte[] encodeNonce(byte[] nonce) {
        if (nonce == null || (nonce.length == 1 && nonce[0] == 0)) {
            return RLP.encodeElement(null);
        }
        return RLP.encodeElement(nonce);
    }

    /** RLP access list bytes for typed txs, or empty list {@link #EMPTY_ACCESS_LIST_RLP} when absent. */
    public static byte[] encodeAccessList(byte[] accessListBytes) {
        return accessListBytes != null ? accessListBytes : EMPTY_ACCESS_LIST_RLP;
    }
}
