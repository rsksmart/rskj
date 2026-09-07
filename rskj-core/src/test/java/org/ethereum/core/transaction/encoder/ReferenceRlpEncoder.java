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
package org.ethereum.core.transaction.encoder;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Independent RLP + typed-envelope encoder used to (re)generate the pinned hex in
 * {@link GoldenTransactionVectors}, following the canonical RSKIP-543 typed-transaction
 * envelope: legacy (EIP-155), RSKIP-546 (type 1 and type 2) and RSKIP-545 (type 4).
 *
 * <p>This is a deliberate second implementation written straight from the specs. It MUST NOT
 * delegate to production code ({@code org.ethereum.util.RLP} or the {@code *TransactionEncoder}s).
 * Expected wire bytes live as immutable constants in {@link GoldenTransactionVectors};
 * {@link GoldenTransactionEncoderTest} asserts both production encoders and this class against
 * those constants so either drifting fails the suite.
 *
 * <p>The field values mirror {@link EncoderTestSupport}; if you change a fixture there, change it
 * here too, re-run {@link #goldenVectors()} offline, and update {@link GoldenTransactionVectors}
 * deliberately — do not sync either side from production encoder output.
 */
final class ReferenceRlpEncoder {

    private static final int CHAIN_ID = 33;
    private static final int HIGH_CHAIN_ID = 200;
    private static final byte[] NONCE = {0x01};
    private static final long GAS_PRICE = 1_000_000_000L;
    private static final long MAX_PRIORITY_FEE = 1_000_000_000L;
    private static final long MAX_FEE = 2_000_000_000L;
    private static final long GAS_LIMIT = 21_000L;
    private static final byte[] TO = hex("1234567890123456789012345678901234567890");
    private static final byte[] EMPTY_TO = new byte[0];
    private static final BigInteger VALUE = BigInteger.TEN.pow(18);
    private static final byte[] DATA = new byte[0];
    private static final byte[] LONG_DATA = repeat((byte) 0xaa, 56);
    private static final byte[] EMPTY_ACCESS_LIST = list();
    // One entry: zero address + one zero storage key (mirrors EncoderTestSupport.NON_EMPTY_ACCESS_LIST).
    private static final byte[] NON_EMPTY_ACCESS_LIST = list(list(
            bytes(new byte[20]), list(bytes(new byte[32]))));

    // Fixed outer signature shared with EncoderTestSupport: r=0x11*32, s=0x22*32.
    private static final byte[] SIG_R = repeat((byte) 0x11, 32);
    private static final byte[] SIG_S = repeat((byte) 0x22, 32);
    private static final int SIG_V_RAW = 28;
    private static final int Y_PARITY = SIG_V_RAW - 27;
    private static final int Y_PARITY_0 = 0;

    // Deterministic RSKIP-545 authorization tuple: [chainId, address, nonce, yParity, r, s].
    private static final byte[] AUTH_DELEGATE = hex("0000000000000000000000000000000000000003");

    private ReferenceRlpEncoder() {
    }

    /** Golden vectors keyed by case id: {@code {encodeForSigningHex, encodeSignedHex}}. */
    static Map<String, String[]> goldenVectors() {
        Map<String, String[]> vectors = new LinkedHashMap<>();
        vectors.put("legacy-chain0", legacy(0));
        vectors.put("legacy-chain33", legacy(CHAIN_ID));
        vectors.put("legacy-chain200", legacy(HIGH_CHAIN_ID));
        vectors.put("legacy-contract-creation", legacy(CHAIN_ID, EMPTY_TO, DATA));
        vectors.put("legacy-long-data", legacy(CHAIN_ID, TO, LONG_DATA));
        vectors.put("type1-chain0", type1(0));
        vectors.put("type1-chain33", type1(CHAIN_ID));
        vectors.put("type1-chain200", type1(HIGH_CHAIN_ID));
        vectors.put("type1-access-list", type1(CHAIN_ID, NON_EMPTY_ACCESS_LIST));
        vectors.put("type2-chain0", type2(0));
        vectors.put("type2-chain33", type2(CHAIN_ID));
        vectors.put("type2-chain33-yParity0", type2(CHAIN_ID, Y_PARITY_0));
        vectors.put("type2-chain200", type2(HIGH_CHAIN_ID));
        // Type4 auth list uses the same chainId as the transaction body.
        vectors.put("type4-chain0", type4(0));
        vectors.put("type4-chain33", type4(CHAIN_ID));
        vectors.put("type4-chain200", type4(HIGH_CHAIN_ID));
        return vectors;
    }

    private static String[] legacy(int chainId) {
        return legacy(chainId, TO, DATA);
    }

    private static String[] legacy(int chainId, byte[] to, byte[] data) {
        byte[][] body = {
                bytes(NONCE), integer(GAS_PRICE), integer(GAS_LIMIT),
                bytes(to), integer(VALUE), bytes(data)
        };
        byte[] forSigning = chainId == 0
                ? list(body)
                : list(append(body, integer(chainId), integer(0), integer(0)));
        int signedV = chainId == 0 ? SIG_V_RAW : Y_PARITY + chainId * 2 + 35;
        byte[] signed = list(append(body, integer(signedV), bytes(SIG_R), bytes(SIG_S)));
        return new String[]{hex(forSigning), hex(signed)};
    }

    private static String[] type1(int chainId) {
        return type1(chainId, EMPTY_ACCESS_LIST);
    }

    private static String[] type1(int chainId, byte[] accessList) {
        byte[][] body = {
                integer(chainId), bytes(NONCE), integer(GAS_PRICE), integer(GAS_LIMIT),
                bytes(TO), integer(VALUE), bytes(DATA), accessList
        };
        return typed(0x01, body);
    }

    private static String[] type2(int chainId) {
        return type2(chainId, Y_PARITY);
    }

    private static String[] type2(int chainId, int yParity) {
        return typed(0x02, type2Body(chainId), yParity);
    }

    private static String[] type4(int chainId) {
        byte[] authList = list(list(
                integer(chainId), bytes(AUTH_DELEGATE), integer(1), integer(0), integer(1), integer(1)));
        byte[][] body = append(type2Body(chainId), authList);
        return typed(0x04, body, Y_PARITY);
    }

    private static byte[][] type2Body(int chainId) {
        return new byte[][]{
                integer(chainId), bytes(NONCE), integer(MAX_PRIORITY_FEE), integer(MAX_FEE),
                integer(GAS_LIMIT), bytes(TO), integer(VALUE), bytes(DATA), EMPTY_ACCESS_LIST
        };
    }

    private static String[] typed(int typeByte, byte[][] body) {
        return typed(typeByte, body, Y_PARITY);
    }

    private static String[] typed(int typeByte, byte[][] body, int yParity) {
        return new String[]{hex(typed(typeByte, list(body))), hex(typedSigned(typeByte, body, yParity))};
    }

    private static byte[] typed(int typeByte, byte[] rlp) {
        return concat(new byte[]{(byte) typeByte}, rlp);
    }

    private static byte[] typedSigned(int typeByte, byte[][] body, int yParity) {
        return typed(typeByte, list(append(body, integer(yParity), bytes(SIG_R), bytes(SIG_S))));
    }

    // ---------------------------------------------------------------------
    // Minimal, self-contained RLP primitives
    // ---------------------------------------------------------------------

    private static byte[] bytes(byte[] value) {
        if (value.length == 1 && (value[0] & 0xFF) < 0x80) {
            return value.clone();
        }
        return concat(encodeLength(value.length, 0x80), value);
    }

    private static byte[] integer(long value) {
        return integer(BigInteger.valueOf(value));
    }

    private static byte[] integer(BigInteger value) {
        return bytes(minimalBytes(value));
    }

    private static byte[] list(byte[]... items) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (byte[] item : items) {
            payload.writeBytes(item);
        }
        byte[] body = payload.toByteArray();
        return concat(encodeLength(body.length, 0xC0), body);
    }

    private static byte[] encodeLength(int length, int offset) {
        if (length < 56) {
            return new byte[]{(byte) (offset + length)};
        }
        byte[] lengthBytes = minimalBytes(BigInteger.valueOf(length));
        return concat(new byte[]{(byte) (offset + 55 + lengthBytes.length)}, lengthBytes);
    }

    private static byte[] minimalBytes(BigInteger value) {
        if (value.signum() == 0) {
            return new byte[0];
        }
        byte[] raw = value.toByteArray();
        if (raw.length > 1 && raw[0] == 0) {
            byte[] trimmed = new byte[raw.length - 1];
            System.arraycopy(raw, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return raw;
    }

    private static byte[][] append(byte[][] head, byte[]... tail) {
        byte[][] all = new byte[head.length + tail.length][];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        return all;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static byte[] repeat(byte value, int length) {
        byte[] result = new byte[length];
        java.util.Arrays.fill(result, value);
        return result;
    }

    private static byte[] hex(String value) {
        int length = value.length() / 2;
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String hex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte b : value) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
