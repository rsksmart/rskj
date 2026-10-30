/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.core.transaction.encoder.util;

import co.rsk.core.Coin;
import org.bouncycastle.util.BigIntegers;
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

    /** Typed fee scalar: zero is the empty string, not {@code 0x00}. Legacy keeps {@code encodeCoinNonNullZero}. */
    public static byte[] encodeFeeScalar(Coin coin) {
        if (coin == null || coin.asBigInteger().signum() == 0) {
            return RLP.encodeElement(null);
        }
        return RLP.encodeElement(BigIntegers.asUnsignedByteArray(coin.asBigInteger()));
    }

    /** RLP access list bytes for typed txs, or empty list {@link #EMPTY_ACCESS_LIST_RLP} when absent. */
    public static byte[] encodeAccessList(byte[] accessListBytes) {
        return accessListBytes != null ? accessListBytes : EMPTY_ACCESS_LIST_RLP;
    }
}
