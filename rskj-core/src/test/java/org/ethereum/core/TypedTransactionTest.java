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

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RSKIP543 Typed Transaction encoding and decoding
 */
public class TypedTransactionTest {

    private static final byte[] EMPTY_DATA = new byte[0];
    private static final ECKey TEST_KEY = new ECKey();
    private static final RskAddress TEST_ADDRESS = new RskAddress("0x1234567890123456789012345678901234567890");

    /**
     * Test that legacy transactions (Type 0) do NOT have a type prefix
     */
    @Test
    public void testLegacyTransactionEncodingNoPrefix() {
        Transaction tx = createTransaction(TransactionType.LEGACY);
        byte[] encoded = tx.getEncoded();
        
        // Legacy transactions should start with RLP list marker (0xc0-0xff)
        assertTrue(encoded[0] >= (byte) 0xc0, 
            "Legacy transaction should start with RLP list marker, got: 0x" + 
            String.format("%02x", encoded[0]));
    }

    /**
     * Test that Type 1 transactions have the 0x01 prefix
     */
    @Test
    public void testType1TransactionEncodingWithPrefix() {
        Transaction tx = createTransaction(TransactionType.TYPE_1);
        byte[] encoded = tx.getEncoded();
        
        // Type 1 transactions should start with 0x01
        assertEquals((byte) 0x01, encoded[0], 
            "Type 1 transaction should start with 0x01");
        
        // The rest should be RLP encoded
        assertTrue(encoded[1] >= (byte) 0xc0,
            "After type prefix, should be RLP list marker");
    }

    /**
     * Test that Type 2 transactions have the 0x02 prefix
     */
    @Test
    public void testType2TransactionEncodingWithPrefix() {
        Transaction tx = createTransaction(TransactionType.TYPE_2);
        byte[] encoded = tx.getEncoded();
        
        // Type 2 transactions should start with 0x02
        assertEquals((byte) 0x02, encoded[0], 
            "Type 2 transaction should start with 0x02");
        
        // The rest should be RLP encoded
        assertTrue(encoded[1] >= (byte) 0xc0,
            "After type prefix, should be RLP list marker");
    }

    /**
     * Test that Type 3 transactions have the 0x03 prefix
     */
    @Test
    public void testType3TransactionEncodingWithPrefix() {
        Transaction tx = createTransaction(TransactionType.TYPE_3);
        byte[] encoded = tx.getEncoded();
        
        // Type 3 transactions should start with 0x03
        assertEquals((byte) 0x03, encoded[0], 
            "Type 3 transaction should start with 0x03");
        
        // The rest should be RLP encoded
        assertTrue(encoded[1] >= (byte) 0xc0,
            "After type prefix, should be RLP list marker");
    }

    /**
     * Test that Type 4 transactions have the 0x04 prefix
     */
    @Test
    public void testType4TransactionEncodingWithPrefix() {
        Transaction tx = createTransaction(TransactionType.TYPE_4);
        byte[] encoded = tx.getEncoded();
        
        // Type 4 transactions should start with 0x04
        assertEquals((byte) 0x04, encoded[0], 
            "Type 4 transaction should start with 0x04");
        
        // The rest should be RLP encoded
        assertTrue(encoded[1] >= (byte) 0xc0,
            "After type prefix, should be RLP list marker");
    }

    /**
     * Test encoding and decoding round-trip for Legacy transaction
     */
    @Test
    public void testLegacyTransactionRoundTrip() {
        Transaction originalTx = createSignedTransaction(TransactionType.LEGACY);
        byte[] encoded = originalTx.getEncoded();
        
        Transaction decodedTx = new Transaction(encoded);
        
        assertEquals(TransactionType.LEGACY, decodedTx.getType());
        assertArrayEquals(originalTx.getNonce(), decodedTx.getNonce());
        assertEquals(originalTx.getValue(), decodedTx.getValue());
        assertEquals(originalTx.getReceiveAddress(), decodedTx.getReceiveAddress());
        // Note: Empty data arrays may become null after RLP encoding/decoding
        byte[] originalData = originalTx.getData();
        byte[] decodedData = decodedTx.getData();
        if (originalData == null || originalData.length == 0) {
            assertTrue(decodedData == null || decodedData.length == 0,
                "Empty data should remain empty or null");
        } else {
            assertArrayEquals(originalData, decodedData);
        }
    }

    /**
     * Test encoding and decoding round-trip for Type 1 transaction
     */
    @Test
    public void testType1TransactionRoundTrip() {
        Transaction originalTx = createSignedTransaction(TransactionType.TYPE_1);
        byte[] encoded = originalTx.getEncoded();
        
        // Verify type prefix is present
        assertEquals((byte) 0x01, encoded[0]);
        
        Transaction decodedTx = new Transaction(encoded);
        
        assertEquals(TransactionType.TYPE_1, decodedTx.getType());
        assertArrayEquals(originalTx.getNonce(), decodedTx.getNonce());
        assertEquals(originalTx.getValue(), decodedTx.getValue());
        assertEquals(originalTx.getReceiveAddress(), decodedTx.getReceiveAddress());
        // Note: Empty data arrays may become null after RLP encoding/decoding
        byte[] originalData = originalTx.getData();
        byte[] decodedData = decodedTx.getData();
        if (originalData == null || originalData.length == 0) {
            assertTrue(decodedData == null || decodedData.length == 0,
                "Empty data should remain empty or null");
        } else {
            assertArrayEquals(originalData, decodedData);
        }
    }

    /**
     * Test encoding and decoding round-trip for Type 2 transaction
     */
    @Test
    public void testType2TransactionRoundTrip() {
        Transaction originalTx = createSignedTransaction(TransactionType.TYPE_2);
        byte[] encoded = originalTx.getEncoded();
        
        // Verify type prefix is present
        assertEquals((byte) 0x02, encoded[0]);
        
        Transaction decodedTx = new Transaction(encoded);
        
        assertEquals(TransactionType.TYPE_2, decodedTx.getType());
        assertArrayEquals(originalTx.getNonce(), decodedTx.getNonce());
        assertEquals(originalTx.getValue(), decodedTx.getValue());
        assertEquals(originalTx.getReceiveAddress(), decodedTx.getReceiveAddress());
        // Note: Empty data arrays may become null after RLP encoding/decoding
        byte[] originalData = originalTx.getData();
        byte[] decodedData = decodedTx.getData();
        if (originalData == null || originalData.length == 0) {
            assertTrue(decodedData == null || decodedData.length == 0,
                "Empty data should remain empty or null");
        } else {
            assertArrayEquals(originalData, decodedData);
        }
    }

    /**
     * Test encoding and decoding round-trip for Type 3 transaction
     */
    @Test
    public void testType3TransactionRoundTrip() {
        Transaction originalTx = createSignedTransaction(TransactionType.TYPE_3);
        byte[] encoded = originalTx.getEncoded();
        
        // Verify type prefix is present
        assertEquals((byte) 0x03, encoded[0]);
        
        Transaction decodedTx = new Transaction(encoded);
        
        assertEquals(TransactionType.TYPE_3, decodedTx.getType());
        assertArrayEquals(originalTx.getNonce(), decodedTx.getNonce());
        assertEquals(originalTx.getValue(), decodedTx.getValue());
        assertEquals(originalTx.getReceiveAddress(), decodedTx.getReceiveAddress());
        // Note: Empty data arrays may become null after RLP encoding/decoding
        byte[] originalData = originalTx.getData();
        byte[] decodedData = decodedTx.getData();
        if (originalData == null || originalData.length == 0) {
            assertTrue(decodedData == null || decodedData.length == 0,
                "Empty data should remain empty or null");
        } else {
            assertArrayEquals(originalData, decodedData);
        }
    }

    /**
     * Test encoding and decoding round-trip for Type 4 transaction
     */
    @Test
    public void testType4TransactionRoundTrip() {
        Transaction originalTx = createSignedTransaction(TransactionType.TYPE_4);
        byte[] encoded = originalTx.getEncoded();
        
        // Verify type prefix is present
        assertEquals((byte) 0x04, encoded[0]);
        
        Transaction decodedTx = new Transaction(encoded);
        
        assertEquals(TransactionType.TYPE_4, decodedTx.getType());
        assertArrayEquals(originalTx.getNonce(), decodedTx.getNonce());
        assertEquals(originalTx.getValue(), decodedTx.getValue());
        assertEquals(originalTx.getReceiveAddress(), decodedTx.getReceiveAddress());
        // Note: Empty data arrays may become null after RLP encoding/decoding
        byte[] originalData = originalTx.getData();
        byte[] decodedData = decodedTx.getData();
        if (originalData == null || originalData.length == 0) {
            assertTrue(decodedData == null || decodedData.length == 0,
                "Empty data should remain empty or null");
        } else {
            assertArrayEquals(originalData, decodedData);
        }
    }

    /**
     * Test that getEncodedRaw includes type prefix for typed transactions
     * This is important for signing - the signature must cover the type
     */
    @Test
    public void testTypedTransactionRawEncodingIncludesTypePrefix() {
        Transaction tx = createTransaction(TransactionType.TYPE_1);
        byte[] rawEncoded = tx.getEncodedRaw();
        
        // Raw encoding for typed transactions should also include type prefix
        assertEquals((byte) 0x01, rawEncoded[0],
            "Raw encoding for Type 1 transaction should start with 0x01");
    }

    /**
     * Test that legacy transaction raw encoding does NOT include type prefix
     */
    @Test
    public void testLegacyTransactionRawEncodingNoTypePrefix() {
        Transaction tx = createTransaction(TransactionType.LEGACY);
        byte[] rawEncoded = tx.getEncodedRaw();
        
        // Legacy raw encoding should start with RLP list marker
        assertTrue(rawEncoded[0] >= (byte) 0xc0,
            "Legacy raw encoding should start with RLP list marker");
    }

    /**
     * Test that attempting to parse an unknown transaction type throws an exception
     */
    @Test
    public void testUnknownTransactionTypeThrowsException() {
        // Create a fake transaction with an unsupported type in the valid typed range
        // Use 0x7f (127) which is in the typed transaction range [0x00, 0x7f] but not supported
        byte[] fakeTypedTx = new byte[50];
        fakeTypedTx[0] = (byte) 0x7f; // Unsupported but valid typed transaction type
        // Fill rest with dummy RLP data
        fakeTypedTx[1] = (byte) 0xc0; // Empty RLP list
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(fakeTypedTx);
        }, "Should throw exception for unknown transaction type");
    }

    /**
     * Test that all transaction types are correctly identified
     */
    @Test
    public void testTransactionTypeIdentification() {
        Transaction legacyTx = createTransaction(TransactionType.LEGACY);
        assertTrue(legacyTx.getType().isLegacy());
        assertFalse(legacyTx.getType().isTyped());
        
        Transaction type1Tx = createTransaction(TransactionType.TYPE_1);
        assertFalse(type1Tx.getType().isLegacy());
        assertTrue(type1Tx.getType().isTyped());
        
        Transaction type2Tx = createTransaction(TransactionType.TYPE_2);
        assertFalse(type2Tx.getType().isLegacy());
        assertTrue(type2Tx.getType().isTyped());
    }

    // Helper methods

    private Transaction createTransaction(TransactionType type) {
        byte[] nonce = ByteUtil.bigIntegerToBytes(BigInteger.ONE);
        byte[] gasPrice = Coin.valueOf(1000000000).getBytes(); // 1 Gwei
        byte[] gasLimit = ByteUtil.bigIntegerToBytes(BigInteger.valueOf(21000));
        byte[] receiveAddress = TEST_ADDRESS.getBytes();
        byte[] value = Coin.valueOf(1000000000000000000L).getBytes(); // 1 RSK
        byte[] data = EMPTY_DATA;
        
        return new Transaction(nonce, gasPrice, gasLimit, receiveAddress, value, data, type);
    }

    private Transaction createSignedTransaction(TransactionType type) {
        Transaction tx = createTransaction(type);
        tx.sign(TEST_KEY.getPrivKeyBytes());
        return tx;
    }
}
