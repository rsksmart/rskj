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
package org.ethereum.core;

import org.bouncycastle.util.encoders.Hex;

/**
 * Hand-assembled RLP encodings of regtest legacy transactions whose r or s is zero. RLP writes zero as the
 * empty item {@code 0x80}.
 *
 * <p>The {@code ZERO_R}, {@code ZERO_S} and {@code ZERO_R_AND_S} encodings are each a list with a 35-byte payload
 * ({@code 0xe3}) of: nonce 1 ({@code 01}), gas price 1 gwei
 * ({@code 843b9aca00}), gas limit 21000 ({@code 825208}), destination 0x...1234, value 1 ({@code 01}), empty data
 * ({@code 80}), v = 101 ({@code 65}, chain id 33 per EIP-155), then r and s.
 */
public final class ZeroSignatureComponentTransactions {

    private static final String FIELDS_UP_TO_V =
            "e3" + "01" + "843b9aca00" + "825208" + "940000000000000000000000000000000000001234" + "01" + "80" + "65";

    /** r is the empty item (zero), s is 1. */
    public static final String ZERO_R_HEX = FIELDS_UP_TO_V + "80" + "01";

    /** r is 1, s is the empty item (zero). */
    public static final String ZERO_S_HEX = FIELDS_UP_TO_V + "01" + "80";

    /** r and s are both the empty item (zero). */
    public static final String ZERO_R_AND_S_HEX = FIELDS_UP_TO_V + "80" + "80";

    /**
     * Last-position shape of a REMASC transaction: a list with a 29-byte payload ({@code 0xdd}) of an empty nonce
     * ({@code 80}), gas price and gas limit each as the single byte {@code 00}, destination REMASC
     * ({@code 0x...01000008}), and empty value and data ({@code 80}). Those are the bytes the node writes for the
     * REMASC transaction of block 1. It is signed, though, with v = 101 ({@code 65}), an empty r (zero) and s = 1.
     * Because it carries a signature, it is not a REMASC transaction.
     */
    public static final String ZERO_R_TO_REMASC_HEX =
            "dd" + "80" + "00" + "00" + "940000000000000000000000000000000001000008" + "80" + "80" + "65" + "80" + "01";

    private ZeroSignatureComponentTransactions() {
    }

    public static byte[] zeroR() {
        return Hex.decode(ZERO_R_HEX);
    }

    public static byte[] zeroS() {
        return Hex.decode(ZERO_S_HEX);
    }

    public static byte[] zeroRToRemasc() {
        return Hex.decode(ZERO_R_TO_REMASC_HEX);
    }
}
