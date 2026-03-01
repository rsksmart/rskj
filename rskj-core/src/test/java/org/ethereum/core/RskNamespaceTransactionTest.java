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
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for RSKIP543 RSK Namespace Transactions (0x02 || rsk-tx-type || payload)
 */
class RskNamespaceTransactionTest {

    private static final byte[] TEST_NONCE = BigInteger.ONE.toByteArray();
    private static final byte[] TEST_GAS_LIMIT = BigInteger.valueOf(21000).toByteArray();
    private static final byte[] TEST_DATA = new byte[]{0x01, 0x02, 0x03};
    private static final RskAddress TEST_ADDRESS = new RskAddress("0x0000000000000000000000000000000001000006");
    private static final Coin TEST_GAS_PRICE = Coin.valueOf(1000);
    private static final Coin TEST_VALUE = Coin.ZERO;
    private static final byte TEST_CHAIN_ID = 33;
    private static final byte[] TEST_PRIVATE_KEY = buildTestPrivateKey();

    private static byte[] buildTestPrivateKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) (i + 1);
        }
        return key;
    }

    private Transaction createRskTransaction(byte rskSubtype) {
        return new Transaction(
            TEST_NONCE,
            TEST_GAS_PRICE,
            TEST_GAS_LIMIT,
            TEST_ADDRESS,
            TEST_VALUE,
            TEST_DATA,
            TEST_CHAIN_ID,
            false,
            TransactionType.TYPE_2,
            rskSubtype
        );
    }

    private Transaction createSignedRskTransaction(byte rskSubtype) {
        Transaction tx = createRskTransaction(rskSubtype);
        tx.sign(TEST_PRIVATE_KEY);
        return tx;
    }

    @Test
    void rskNamespaceTransactionCreation() {
        byte rskSubtype = 0x03;
        Transaction tx = createRskTransaction(rskSubtype);

        assertEquals(TransactionType.TYPE_2, tx.getType());
        assertTrue(tx.isRskNamespaceTransaction());
        assertEquals(rskSubtype, tx.getRskSubtype());
    }

    @Test
    void rskNamespaceEncoding() {
        byte rskSubtype = 0x03;
        Transaction tx = createSignedRskTransaction(rskSubtype);
        byte[] encoded = tx.getEncoded();

        assertNotNull(encoded);
        assertTrue(encoded.length > 2);
        assertEquals(0x02, encoded[0]);
        assertEquals(rskSubtype, encoded[1]);
    }

    @Test
    void rskNamespaceDecoding() {
        byte rskSubtype = 0x05;
        Transaction original = createSignedRskTransaction(rskSubtype);

        byte[] encoded = original.getEncoded();
        Transaction decoded = new Transaction(encoded);

        assertEquals(TransactionType.TYPE_2, decoded.getType());
        assertTrue(decoded.isRskNamespaceTransaction());
        assertEquals(rskSubtype, decoded.getRskSubtype());
    }

    @Test
    void rskNamespaceRoundTrip() {
        byte rskSubtype = 0x07;
        Transaction original = createSignedRskTransaction(rskSubtype);

        byte[] encoded = original.getEncoded();
        Transaction decoded = new Transaction(encoded);

        assertEquals(original.getType(), decoded.getType());
        assertTrue(decoded.isRskNamespaceTransaction());
        assertEquals(original.getRskSubtype(), decoded.getRskSubtype());
        assertArrayEquals(original.getNonce(), decoded.getNonce());
        assertEquals(original.getGasPrice(), decoded.getGasPrice());
        assertArrayEquals(original.getGasLimit(), decoded.getGasLimit());
    }

    @Test
    void allRskSubtypes() {
        for (int i = 0x00; i <= 0x7f; i++) {
            byte subtype = (byte) i;
            Transaction tx = createRskTransaction(subtype);

            assertTrue(tx.isRskNamespaceTransaction());
            assertEquals(TransactionType.TYPE_2, tx.getType());
            assertEquals(subtype, tx.getRskSubtype());
        }
    }

    @Test
    void rskVsEip1559Detection() {
        Transaction rskTx = createSignedRskTransaction((byte) 0x03);

        byte[] encoded = rskTx.getEncoded();
        Transaction decoded = new Transaction(encoded);

        assertTrue(decoded.isRskNamespaceTransaction());
        assertEquals((byte) 0x03, decoded.getRskSubtype());
    }

    @Test
    void rskSubtypeValidation() {
        assertDoesNotThrow(() -> createRskTransaction((byte) 0x00));
        assertDoesNotThrow(() -> createRskTransaction((byte) 0x7f));

        assertThrows(IllegalArgumentException.class, () -> new Transaction(
            TEST_NONCE,
            TEST_GAS_PRICE,
            TEST_GAS_LIMIT,
            TEST_ADDRESS,
            TEST_VALUE,
            TEST_DATA,
            TEST_CHAIN_ID,
            false,
            TransactionType.TYPE_2,
            (byte) 0x80
        ));
    }

    @Test
    void rskSubtypeOnlyWithType2() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
            TEST_NONCE,
            TEST_GAS_PRICE,
            TEST_GAS_LIMIT,
            TEST_ADDRESS,
            TEST_VALUE,
            TEST_DATA,
            TEST_CHAIN_ID,
            false,
            TransactionType.TYPE_1,
            (byte) 0x03
        ));
    }

    @Test
    void getFullTypeString() {
        Transaction rskTx = createRskTransaction((byte) 0x03);
        assertEquals("0x0203", rskTx.getFullTypeString());

        rskTx = createRskTransaction((byte) 0x0a);
        assertEquals("0x020a", rskTx.getFullTypeString());

        Transaction type1Tx = new Transaction(
            TEST_NONCE,
            TEST_GAS_PRICE,
            TEST_GAS_LIMIT,
            TEST_ADDRESS,
            TEST_VALUE,
            TEST_DATA,
            TEST_CHAIN_ID,
            false,
            TransactionType.TYPE_1,
            null
        );
        assertEquals("0x01", type1Tx.getFullTypeString());

        Transaction legacyTx = new Transaction(
            TEST_NONCE,
            TEST_GAS_PRICE.getBytes(),
            TEST_GAS_LIMIT,
            TEST_ADDRESS.getBytes(),
            TEST_VALUE.getBytes(),
            TEST_DATA
        );
        assertEquals("0x00", legacyTx.getFullTypeString());
    }

    @Test
    void getRskSubtypeThrowsForNonRskTransaction() {
        Transaction legacyTx = new Transaction(
            TEST_NONCE,
            TEST_GAS_PRICE.getBytes(),
            TEST_GAS_LIMIT,
            TEST_ADDRESS.getBytes(),
            TEST_VALUE.getBytes(),
            TEST_DATA
        );

        assertFalse(legacyTx.isRskNamespaceTransaction());
        assertThrows(IllegalStateException.class, legacyTx::getRskSubtype);

        Transaction type1Tx = new Transaction(
            TEST_NONCE,
            TEST_GAS_PRICE,
            TEST_GAS_LIMIT,
            TEST_ADDRESS,
            TEST_VALUE,
            TEST_DATA,
            TEST_CHAIN_ID,
            false,
            TransactionType.TYPE_1,
            null
        );

        assertFalse(type1Tx.isRskNamespaceTransaction());
        assertThrows(IllegalStateException.class, type1Tx::getRskSubtype);
    }

    @Test
    void rskNamespaceReceiptEncoding() {
        byte rskSubtype = 0x03;
        Transaction tx = createSignedRskTransaction(rskSubtype);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransaction(tx);

        byte[] encoded = receipt.getEncoded();

        assertNotNull(encoded);
        assertTrue(encoded.length > 2);
        assertEquals(0x02, encoded[0]);
        assertEquals(rskSubtype, encoded[1]);
    }

    @Test
    void rskNamespaceReceiptDecoding() {
        byte rskSubtype = 0x05;
        Transaction tx = createSignedRskTransaction(rskSubtype);

        TransactionReceipt originalReceipt = new TransactionReceipt();
        originalReceipt.setTransaction(tx);
        originalReceipt.setCumulativeGas(21000);
        originalReceipt.setGasUsed(21000);
        originalReceipt.setTxStatus(true);

        byte[] encoded = originalReceipt.getEncoded();
        TransactionReceipt decodedReceipt = new TransactionReceipt(encoded);

        assertArrayEquals(encoded, decodedReceipt.getEncoded());
    }

    @Test
    void rskNamespaceReceiptRoundTrip() {
        byte[] subtypes = {0x00, 0x03, 0x0f};

        for (byte subtype : subtypes) {
            Transaction tx = createSignedRskTransaction(subtype);

            TransactionReceipt original = new TransactionReceipt();
            original.setTransaction(tx);
            original.setCumulativeGas(21000);
            original.setGasUsed(21000);
            original.setTxStatus(true);

            byte[] encoded = original.getEncoded();

            assertEquals(0x02, encoded[0]);
            assertEquals(subtype, encoded[1]);
            assertNotNull(encoded);
            assertTrue(encoded.length > 2);
        }
    }

    @Test
    void backwardCompatibilityLegacy() {
        Transaction legacy = new Transaction(
            TEST_NONCE,
            TEST_GAS_PRICE.getBytes(),
            TEST_GAS_LIMIT,
            TEST_ADDRESS.getBytes(),
            TEST_VALUE.getBytes(),
            TEST_DATA
        );

        assertFalse(legacy.isRskNamespaceTransaction());
        assertEquals(TransactionType.LEGACY, legacy.getType());

        legacy.sign(TEST_PRIVATE_KEY);

        byte[] encoded = legacy.getEncoded();
        assertTrue((encoded[0] & 0xFF) >= 0xc0);
    }

    @Test
    void backwardCompatibilityStandardTyped() {
        Transaction type1 = new Transaction(
            TEST_NONCE,
            TEST_GAS_PRICE,
            TEST_GAS_LIMIT,
            TEST_ADDRESS,
            TEST_VALUE,
            TEST_DATA,
            TEST_CHAIN_ID,
            false,
            TransactionType.TYPE_1,
            null
        );

        assertFalse(type1.isRskNamespaceTransaction());
        assertEquals(TransactionType.TYPE_1, type1.getType());

        type1.sign(TEST_PRIVATE_KEY);

        byte[] encoded = type1.getEncoded();
        assertEquals(0x01, encoded[0]);
    }
}
