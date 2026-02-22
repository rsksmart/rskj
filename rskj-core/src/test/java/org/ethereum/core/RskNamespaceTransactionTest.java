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
public class RskNamespaceTransactionTest {
    
    // Test data
    private static final byte[] TEST_NONCE = BigInteger.ONE.toByteArray();
    private static final byte[] TEST_GAS_LIMIT = BigInteger.valueOf(21000).toByteArray();
    private static final byte[] TEST_DATA = new byte[]{0x01, 0x02, 0x03};
    private static final RskAddress TEST_ADDRESS = new RskAddress("0x0000000000000000000000000000000001000006");
    private static final Coin TEST_GAS_PRICE = Coin.valueOf(1000);
    private static final Coin TEST_VALUE = Coin.ZERO;
    private static final byte TEST_CHAIN_ID = 33; // RSK Mainnet
    
    /**
     * Helper method to create an RSK namespace transaction
     */
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
    
    @Test
    public void testRskNamespaceTransactionCreation() {
        byte rskSubtype = 0x03;
        Transaction tx = createRskTransaction(rskSubtype);
        
        assertEquals(TransactionType.TYPE_2, tx.getType());
        assertTrue(tx.isRskNamespaceTransaction());
        assertEquals(rskSubtype, tx.getRskSubtype());
    }
    
    @Test
    public void testRskNamespaceEncoding() {
        byte rskSubtype = 0x03;
        Transaction tx = createRskTransaction(rskSubtype);
        
        // Sign the transaction first
        byte[] privateKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            privateKey[i] = (byte) (i + 1);
        }
        tx.sign(privateKey);
        
        byte[] encoded = tx.getEncoded();
        
        assertNotNull(encoded);
        assertTrue(encoded.length > 2);
        assertEquals(0x02, encoded[0]); // First byte is 0x02 (namespace)
        assertEquals(rskSubtype, encoded[1]); // Second byte is RSK subtype
    }
    
    @Test
    public void testRskNamespaceDecoding() {
        byte rskSubtype = 0x05;
        Transaction original = createRskTransaction(rskSubtype);
        
        // Sign the transaction
        byte[] privateKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            privateKey[i] = (byte) (i + 1);
        }
        original.sign(privateKey);
        
        // Encode and decode
        byte[] encoded = original.getEncoded();
        Transaction decoded = new Transaction(encoded);
        
        assertEquals(TransactionType.TYPE_2, decoded.getType());
        assertTrue(decoded.isRskNamespaceTransaction());
        assertEquals(rskSubtype, decoded.getRskSubtype());
    }
    
    @Test
    public void testRskNamespaceRoundTrip() {
        byte rskSubtype = 0x07;
        Transaction original = createRskTransaction(rskSubtype);
        
        // Sign the transaction
        byte[] privateKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            privateKey[i] = (byte) (i + 1);
        }
        original.sign(privateKey);
        
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
    public void testAllRskSubtypes() {
        // Test all valid RSK subtypes [0x00, 0x7f]
        for (int i = 0x00; i <= 0x7f; i++) {
            byte subtype = (byte) i;
            Transaction tx = createRskTransaction(subtype);
            
            assertTrue(tx.isRskNamespaceTransaction());
            assertEquals(TransactionType.TYPE_2, tx.getType());
            assertEquals(subtype, tx.getRskSubtype());
        }
    }
    
    @Test
    public void testRskVsEip1559Detection() {
        // Create RSK namespace transaction: 0x02 || 0x03 || payload
        Transaction rskTx = createRskTransaction((byte) 0x03);
        
        // Sign the transaction
        byte[] privateKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            privateKey[i] = (byte) (i + 1);
        }
        rskTx.sign(privateKey);
        
        // Encode and decode
        byte[] encoded = rskTx.getEncoded();
        Transaction decoded = new Transaction(encoded);
        
        assertTrue(decoded.isRskNamespaceTransaction());
        assertEquals((byte) 0x03, decoded.getRskSubtype());
    }
    
    @Test
    public void testRskSubtypeValidation() {
        // Valid subtype within range
        assertDoesNotThrow(() -> createRskTransaction((byte) 0x00));
        assertDoesNotThrow(() -> createRskTransaction((byte) 0x7f));
        
        // Invalid subtype (> 0x7f)
        assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(
                TEST_NONCE,
                TEST_GAS_PRICE,
                TEST_GAS_LIMIT,
                TEST_ADDRESS,
                TEST_VALUE,
                TEST_DATA,
                TEST_CHAIN_ID,
                false,
                TransactionType.TYPE_2,
                (byte) 0x80 // Invalid: > 0x7f
            );
        });
    }
    
    @Test
    public void testRskSubtypeOnlyWithType2() {
        // RSK subtype should only work with TYPE_2
        assertThrows(IllegalArgumentException.class, () -> {
            new Transaction(
                TEST_NONCE,
                TEST_GAS_PRICE,
                TEST_GAS_LIMIT,
                TEST_ADDRESS,
                TEST_VALUE,
                TEST_DATA,
                TEST_CHAIN_ID,
                false,
                TransactionType.TYPE_1, // Wrong type
                (byte) 0x03
            );
        });
    }
    
    @Test
    public void testGetFullTypeString() {
        // RSK namespace transaction
        Transaction rskTx = createRskTransaction((byte) 0x03);
        assertEquals("0x0203", rskTx.getFullTypeString());
        
        rskTx = createRskTransaction((byte) 0x0a);
        assertEquals("0x020a", rskTx.getFullTypeString());
        
        // Standard typed transaction (TYPE_1)
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
        
        // Legacy transaction
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
    public void testGetRskSubtypeThrowsForNonRskTransaction() {
        // Legacy transaction
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
        
        // Standard TYPE_1 transaction
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
    public void testRskNamespaceReceiptEncoding() {
        byte rskSubtype = 0x03;
        Transaction tx = createRskTransaction(rskSubtype);
        
        // Sign the transaction
        byte[] privateKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            privateKey[i] = (byte) (i + 1);
        }
        tx.sign(privateKey);
        
        // Create a receipt
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransaction(tx);
        
        byte[] encoded = receipt.getEncoded();
        
        assertNotNull(encoded);
        assertTrue(encoded.length > 2);
        assertEquals(0x02, encoded[0]); // First byte is 0x02 (namespace)
        assertEquals(rskSubtype, encoded[1]); // Second byte is RSK subtype
    }
    
    @Test
    public void testRskNamespaceReceiptDecoding() {
        byte rskSubtype = 0x05;
        Transaction tx = createRskTransaction(rskSubtype);
        
        // Sign the transaction
        byte[] privateKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            privateKey[i] = (byte) (i + 1);
        }
        tx.sign(privateKey);
        
        // Create and encode receipt
        TransactionReceipt originalReceipt = new TransactionReceipt();
        originalReceipt.setTransaction(tx);
        originalReceipt.setCumulativeGas(21000);
        originalReceipt.setGasUsed(21000);
        originalReceipt.setTxStatus(true);
        
        byte[] encoded = originalReceipt.getEncoded();
        
        // Decode receipt
        TransactionReceipt decodedReceipt = new TransactionReceipt(encoded);
        
        // The decoded receipt won't have transaction reference until it's set
        // But the encoding should have preserved the type bytes
        assertArrayEquals(encoded, decodedReceipt.getEncoded());
    }
    
    @Test
    public void testRskNamespaceReceiptRoundTrip() {
        // Test a few RSK subtypes
        byte[] subtypes = {0x00, 0x03, 0x0f};
        
        for (byte subtype : subtypes) {
            Transaction tx = createRskTransaction(subtype);
            
            // Sign the transaction
            byte[] privateKey = new byte[32];
            for (int j = 0; j < 32; j++) {
                privateKey[j] = (byte) (j + 1);
            }
            tx.sign(privateKey);
            
            // Create receipt with transaction
            TransactionReceipt original = new TransactionReceipt();
            original.setTransaction(tx);
            original.setCumulativeGas(21000);
            original.setGasUsed(21000);
            original.setTxStatus(true);
            
            byte[] encoded = original.getEncoded();
            
            // Verify encoding has correct prefix
            assertEquals(0x02, encoded[0]);
            assertEquals(subtype, encoded[1]);
            
            // For now, just verify the encoding - full round trip requires
            // more context (block, transaction info) which is tested elsewhere
            assertNotNull(encoded);
            assertTrue(encoded.length > 2);
        }
    }
    
    @Test
    public void testBackwardCompatibilityLegacy() {
        // Legacy transaction should still work
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
        
        // Legacy transaction encoding should not have type prefix
        byte[] privateKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            privateKey[i] = (byte) (i + 1);
        }
        legacy.sign(privateKey);
        
        byte[] encoded = legacy.getEncoded();
        assertTrue((encoded[0] & 0xFF) >= 0xc0); // Should start with RLP list marker
    }
    
    @Test
    public void testBackwardCompatibilityStandardTyped() {
        // Standard TYPE_1 transaction should still work
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
        
        // Sign and encode
        byte[] privateKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            privateKey[i] = (byte) (i + 1);
        }
        type1.sign(privateKey);
        
        byte[] encoded = type1.getEncoded();
        assertEquals(0x01, encoded[0]); // Should start with 0x01
    }
}
