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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RSKIP543 Typed Transaction encoding and decoding.
 */
class TypedTransactionTest {

    private static final byte[] EMPTY_DATA = new byte[0];
    private static final ECKey TEST_KEY = new ECKey();
    private static final RskAddress TEST_ADDRESS = new RskAddress("0x1234567890123456789012345678901234567890");

    // ========================================================================
    // Encoding prefix
    // ========================================================================

    @Test
    void legacyTransactionEncoding_startsWithRlpListMarker() {
        Transaction tx = createTransaction(TransactionType.LEGACY, EMPTY_DATA);
        byte[] encoded = tx.getEncoded();

        assertTrue((encoded[0] & 0xFF) >= 0xc0,
            "Legacy transaction should start with RLP list marker, got: 0x" +
            String.format("%02x", encoded[0]));
    }

    @ParameterizedTest
    @EnumSource(value = TransactionType.class, names = "LEGACY", mode = EnumSource.Mode.EXCLUDE)
    void typedTransactionEncoding_startsWithCorrectTypePrefix(TransactionType type) {
        Transaction tx = createTransaction(type, EMPTY_DATA);
        byte[] encoded = tx.getEncoded();

        assertEquals(type.getByteCode(), encoded[0],
            "Typed transaction should start with type byte 0x" +
            String.format("%02x", type.getByteCode()));
        assertTrue((encoded[1] & 0xFF) >= 0xc0,
            "After type prefix, should be RLP list marker");
    }

    // ========================================================================
    // Round-trip (encode -> decode) for all types
    // ========================================================================

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void signedTransactionRoundTrip_preservesCoreFields(TransactionType type) {
        Transaction original = createSignedTransaction(type, EMPTY_DATA);
        byte[] encoded = original.getEncoded();

        if (type.isTyped()) {
            assertEquals(type.getByteCode(), encoded[0]);
        }

        Transaction decoded = new Transaction(encoded);

        assertEquals(type, decoded.getType());
        assertArrayEquals(original.getNonce(), decoded.getNonce());
        assertEquals(original.getValue(), decoded.getValue());
        assertEquals(original.getReceiveAddress(), decoded.getReceiveAddress());
        assertDataEquals(original.getData(), decoded.getData());
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void signedTransactionRoundTrip_preservesGasFields(TransactionType type) {
        Transaction original = createSignedTransaction(type, EMPTY_DATA);
        Transaction decoded = new Transaction(original.getEncoded());

        assertEquals(original.getGasPrice(), decoded.getGasPrice());
        assertArrayEquals(original.getGasLimit(), decoded.getGasLimit());
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void signedTransactionRoundTrip_withNonEmptyData(TransactionType type) {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        Transaction original = createSignedTransaction(type, data);

        Transaction decoded = new Transaction(original.getEncoded());

        assertEquals(type, decoded.getType());
        assertArrayEquals(data, decoded.getData());
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void signedTransactionRoundTrip_withLargeData(TransactionType type) {
        byte[] largeData = new byte[1024];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i & 0xff);
        }
        Transaction original = createSignedTransaction(type, largeData);

        Transaction decoded = new Transaction(original.getEncoded());

        assertEquals(type, decoded.getType());
        assertArrayEquals(largeData, decoded.getData());
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void signedTransactionRoundTrip_withZeroNonce(TransactionType type) {
        Transaction original = createSignedTransactionWith(type, BigInteger.ZERO,
            Coin.valueOf(1_000_000_000_000_000_000L), EMPTY_DATA);

        Transaction decoded = new Transaction(original.getEncoded());

        assertEquals(type, decoded.getType());
        assertArrayEquals(original.getNonce(), decoded.getNonce());
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void signedTransactionRoundTrip_withHighNonce(TransactionType type) {
        BigInteger highNonce = BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE);
        Transaction original = createSignedTransactionWith(type, highNonce,
            Coin.valueOf(1_000_000_000_000_000_000L), EMPTY_DATA);

        Transaction decoded = new Transaction(original.getEncoded());

        assertEquals(type, decoded.getType());
        assertArrayEquals(original.getNonce(), decoded.getNonce());
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void signedTransactionRoundTrip_withZeroValue(TransactionType type) {
        Transaction original = createSignedTransactionWith(type, BigInteger.ONE,
            Coin.ZERO, EMPTY_DATA);

        Transaction decoded = new Transaction(original.getEncoded());

        assertEquals(type, decoded.getType());
    }

    // ========================================================================
    // Double-encode stability
    // ========================================================================

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void doubleEncode_producesIdenticalBytes(TransactionType type) {
        Transaction original = createSignedTransaction(type, EMPTY_DATA);
        byte[] firstEncode = original.getEncoded();

        Transaction decoded = new Transaction(firstEncode);
        byte[] secondEncode = decoded.getEncoded();

        assertArrayEquals(firstEncode, secondEncode,
            "Re-encoding a decoded transaction should produce identical bytes");
    }

    // ========================================================================
    // Raw encoding (used for signing digest)
    // ========================================================================

    @ParameterizedTest
    @EnumSource(value = TransactionType.class, names = "LEGACY", mode = EnumSource.Mode.EXCLUDE)
    void typedTransactionRawEncoding_startsWithTypePrefix(TransactionType type) {
        Transaction tx = createTransaction(type, EMPTY_DATA);
        byte[] rawEncoded = tx.getEncodedRaw();

        assertEquals(type.getByteCode(), rawEncoded[0],
            "Raw encoding should start with type byte 0x" +
            String.format("%02x", type.getByteCode()));
    }

    @Test
    void legacyTransactionRawEncoding_startsWithRlpListMarker() {
        Transaction tx = createTransaction(TransactionType.LEGACY, EMPTY_DATA);
        byte[] rawEncoded = tx.getEncodedRaw();

        assertTrue((rawEncoded[0] & 0xFF) >= 0xc0,
            "Legacy raw encoding should start with RLP list marker");
    }

    // ========================================================================
    // Type identification
    // ========================================================================

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void transactionType_isCorrectlyIdentified(TransactionType type) {
        Transaction tx = createTransaction(type, EMPTY_DATA);

        assertEquals(type, tx.getType());
        assertEquals(type == TransactionType.LEGACY, tx.getType().isLegacy());
        assertEquals(type != TransactionType.LEGACY, tx.getType().isTyped());
    }

    // ========================================================================
    // Encoding length: typed txs are 1 byte longer than the same legacy tx
    // ========================================================================

    @ParameterizedTest
    @EnumSource(value = TransactionType.class, names = "LEGACY", mode = EnumSource.Mode.EXCLUDE)
    void typedTransactionEncoding_isOneByteLongerThanLegacy(TransactionType type) {
        Transaction legacyTx = createSignedTransaction(TransactionType.LEGACY, EMPTY_DATA);
        Transaction typedTx = createSignedTransaction(type, EMPTY_DATA);

        int legacyLen = legacyTx.getEncoded().length;
        int typedLen = typedTx.getEncoded().length;

        assertEquals(legacyLen + 1, typedLen,
            type + " encoded length should be legacy length + 1 (for type prefix)");
    }

    // ========================================================================
    // Invalid / unknown raw data
    // ========================================================================

    @ParameterizedTest
    @ValueSource(bytes = {0x05, 0x06, 0x10, 0x50, 0x7f})
    void unknownTypeByte_throwsException(byte unknownType) {
        byte[] fakeTypedTx = new byte[50];
        fakeTypedTx[0] = unknownType;
        fakeTypedTx[1] = (byte) 0xc0;

        assertThrows(IllegalArgumentException.class,
            () -> new Transaction(fakeTypedTx));
    }

    @Test
    void nullRawData_throwsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new Transaction((byte[]) null));
    }

    @Test
    void emptyRawData_throwsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new Transaction(new byte[0]));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static void assertDataEquals(byte[] expected, byte[] actual) {
        if (expected == null || expected.length == 0) {
            assertTrue(actual == null || actual.length == 0,
                "Empty data should remain empty or null");
        } else {
            assertArrayEquals(expected, actual);
        }
    }

    private Transaction createTransaction(TransactionType type, byte[] data) {
        byte[] nonce = ByteUtil.bigIntegerToBytes(BigInteger.ONE);
        byte[] gasPrice = Coin.valueOf(1_000_000_000).getBytes();
        byte[] gasLimit = ByteUtil.bigIntegerToBytes(BigInteger.valueOf(21_000));
        byte[] receiveAddress = TEST_ADDRESS.getBytes();
        byte[] value = Coin.valueOf(1_000_000_000_000_000_000L).getBytes();

        return new Transaction(nonce, gasPrice, gasLimit, receiveAddress, value, data, type);
    }

    private Transaction createSignedTransaction(TransactionType type, byte[] data) {
        Transaction tx = createTransaction(type, data);
        tx.sign(TEST_KEY.getPrivKeyBytes());
        return tx;
    }

    private Transaction createSignedTransactionWith(TransactionType type, BigInteger nonce,
                                                    Coin value, byte[] data) {
        byte[] nonceBytes = ByteUtil.bigIntegerToBytes(nonce);
        byte[] gasPrice = Coin.valueOf(1_000_000_000).getBytes();
        byte[] gasLimit = ByteUtil.bigIntegerToBytes(BigInteger.valueOf(21_000));
        byte[] receiveAddress = TEST_ADDRESS.getBytes();
        byte[] valueBytes = value.getBytes();

        Transaction tx = new Transaction(nonceBytes, gasPrice, gasLimit, receiveAddress,
            valueBytes, data, type);
        tx.sign(TEST_KEY.getPrivKeyBytes());
        return tx;
    }
}
