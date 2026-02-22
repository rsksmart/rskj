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
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for typed transaction receipts (RSKIP543)
 * 
 */
class TypedTransactionReceiptTest {

    @Test
    void testLegacyReceiptEncoding() {
        // Create a legacy transaction
        Transaction tx = createTransaction(TransactionType.LEGACY);
        
        // Create receipt
        TransactionReceipt receipt = createReceipt(tx);
        
        // Encode receipt
        byte[] encoded = receipt.getEncoded();
        
        // Legacy receipts should NOT have a type prefix
        // First byte should be >= 0xc0 (RLP list marker)
        assertTrue(encoded[0] >= (byte) 0xc0, 
            "Legacy receipt should start with RLP list marker (>= 0xc0), got: " + 
            String.format("0x%02x", encoded[0] & 0xff));
    }

    @Test
    void testType1ReceiptEncoding() {
        // Create a Type 1 (EIP-2930) transaction
        Transaction tx = createTransaction(TransactionType.TYPE_1);
        
        // Create receipt
        TransactionReceipt receipt = createReceipt(tx);
        
        // Encode receipt
        byte[] encoded = receipt.getEncoded();
        
        // Type 1 receipts should have 0x01 prefix
        assertEquals((byte) 0x01, encoded[0], 
            "Type 1 receipt should start with 0x01 prefix");
        
        // Second byte should be RLP list marker
        assertTrue(encoded[1] >= (byte) 0xc0, 
            "Second byte should be RLP list marker");
    }

    @Test
    void testType2ReceiptEncoding() {
        // Create a Type 2 transaction
        Transaction tx = createTransaction(TransactionType.TYPE_2);
        
        // Create receipt
        TransactionReceipt receipt = createReceipt(tx);
        
        // Encode receipt
        byte[] encoded = receipt.getEncoded();
        
        // Type 2 receipts should have 0x02 prefix
        assertEquals((byte) 0x02, encoded[0], 
            "Type 2 receipt should start with 0x02 prefix");
        
        // Second byte should be RLP list marker
        assertTrue(encoded[1] >= (byte) 0xc0, 
            "Second byte should be RLP list marker");
    }

    @Test
    void testType3ReceiptEncoding() {
        // Create a Type 3 (EIP-4844) transaction
        Transaction tx = createTransaction(TransactionType.TYPE_3);
        
        // Create receipt
        TransactionReceipt receipt = createReceipt(tx);
        
        // Encode receipt
        byte[] encoded = receipt.getEncoded();
        
        // Type 3 receipts should have 0x03 prefix
        assertEquals((byte) 0x03, encoded[0], 
            "Type 3 receipt should start with 0x03 prefix");
    }

    @Test
    void testType4ReceiptEncoding() {
        // Create a Type 4 (EIP-7702) transaction
        Transaction tx = createTransaction(TransactionType.TYPE_4);
        
        // Create receipt
        TransactionReceipt receipt = createReceipt(tx);
        
        // Encode receipt
        byte[] encoded = receipt.getEncoded();
        
        // Type 4 receipts should have 0x04 prefix
        assertEquals((byte) 0x04, encoded[0], 
            "Type 4 receipt should start with 0x04 prefix");
    }

    @Test
    void testLegacyReceiptDecoding() {
        // Create and encode a legacy receipt
        Transaction tx = createTransaction(TransactionType.LEGACY);
        TransactionReceipt originalReceipt = createReceipt(tx);
        byte[] encoded = originalReceipt.getEncoded();
        
        // Decode receipt
        TransactionReceipt decodedReceipt = new TransactionReceipt(encoded);
        decodedReceipt.setTransaction(tx);
        
        // Verify fields match
        assertArrayEquals(originalReceipt.getPostTxState(), decodedReceipt.getPostTxState());
        assertArrayEquals(originalReceipt.getCumulativeGas(), decodedReceipt.getCumulativeGas());
        assertArrayEquals(originalReceipt.getGasUsed(), decodedReceipt.getGasUsed());
        assertArrayEquals(originalReceipt.getStatus(), decodedReceipt.getStatus());
    }

    @Test
    void testTypedReceiptDecoding() {
        // Test decoding for each typed transaction
        for (TransactionType type : new TransactionType[]{
            TransactionType.TYPE_1, 
            TransactionType.TYPE_2, 
            TransactionType.TYPE_3, 
            TransactionType.TYPE_4
        }) {
            // Create and encode a typed receipt
            Transaction tx = createTransaction(type);
            TransactionReceipt originalReceipt = createReceipt(tx);
            byte[] encoded = originalReceipt.getEncoded();
            
            // Verify first byte is the type
            assertEquals(type.getByteCode(), encoded[0], 
                "First byte should be transaction type: " + type.getTypeName());
            
            // Decode receipt
            TransactionReceipt decodedReceipt = new TransactionReceipt(encoded);
            decodedReceipt.setTransaction(tx);
            
            // Verify fields match
            assertArrayEquals(originalReceipt.getPostTxState(), decodedReceipt.getPostTxState(),
                "PostTxState mismatch for " + type.getTypeName());
            assertArrayEquals(originalReceipt.getCumulativeGas(), decodedReceipt.getCumulativeGas(),
                "CumulativeGas mismatch for " + type.getTypeName());
            assertArrayEquals(originalReceipt.getGasUsed(), decodedReceipt.getGasUsed(),
                "GasUsed mismatch for " + type.getTypeName());
            assertArrayEquals(originalReceipt.getStatus(), decodedReceipt.getStatus(),
                "Status mismatch for " + type.getTypeName());
        }
    }

    @Test
    void testReceiptRoundTrip() {
        // Test that encoding and decoding preserves all data
        for (TransactionType type : TransactionType.values()) {
            Transaction tx = createTransaction(type);
            TransactionReceipt originalReceipt = createReceipt(tx);
            
            // Add some logs
            List<LogInfo> logs = new ArrayList<>();
            logs.add(createLogInfo());
            originalReceipt.setLogInfoList(logs);
            
            // Encode
            byte[] encoded = originalReceipt.getEncoded();
            
            // Decode
            TransactionReceipt decodedReceipt = new TransactionReceipt(encoded);
            decodedReceipt.setTransaction(tx);
            
            // Re-encode
            byte[] reEncoded = decodedReceipt.getEncoded();
            
            // Should be identical
            assertArrayEquals(encoded, reEncoded, 
                "Round-trip encoding failed for " + type.getTypeName());
        }
    }

    @Test
    void testReceiptWithoutTransaction() {
        // Test that receipt without transaction reference defaults to legacy format
        TransactionReceipt receipt = new TransactionReceipt(
            new byte[]{1}, // postTxState
            new byte[]{100}, // cumulativeGas
            new byte[]{50}, // gasUsed
            new Bloom(), // bloom
            new ArrayList<>(), // logs
            TransactionReceipt.SUCCESS_STATUS
        );
        
        byte[] encoded = receipt.getEncoded();
        
        // Should use legacy format (no type prefix)
        assertTrue(encoded[0] >= (byte) 0xc0, 
            "Receipt without transaction should default to legacy format");
    }

    @Test
    void testReceiptStatusSuccess() {
        Transaction tx = createTransaction(TransactionType.TYPE_1);
        TransactionReceipt receipt = createReceipt(tx);
        receipt.setStatus(TransactionReceipt.SUCCESS_STATUS);
        
        assertTrue(receipt.isSuccessful(), "Receipt should be successful");
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt.getStatus());
    }

    @Test
    void testReceiptStatusFailed() {
        Transaction tx = createTransaction(TransactionType.TYPE_2);
        TransactionReceipt receipt = createReceipt(tx);
        receipt.setStatus(TransactionReceipt.FAILED_STATUS);
        
        assertFalse(receipt.isSuccessful(), "Receipt should be failed");
        assertArrayEquals(TransactionReceipt.FAILED_STATUS, receipt.getStatus());
    }

    // Helper methods

    private Transaction createTransaction(TransactionType type) {
        // Create a minimal transaction with the specified type
        return Transaction.builder()
            .nonce(new byte[]{1})
            .gasPrice(Coin.valueOf(1000))
            .gasLimit(new byte[]{(byte) 0x52, 0x08}) // 21000
            .destination(RskAddress.nullAddress().getBytes())
            .value(Coin.ZERO)
            .data(EMPTY_BYTE_ARRAY)
            .chainId((byte) 33) // RegTest chain ID
            .type(type)
            .build();
    }

    private TransactionReceipt createReceipt(Transaction tx) {
        TransactionReceipt receipt = new TransactionReceipt(
            new byte[]{1}, // postTxState (success)
            new byte[]{(byte) 0x52, 0x08}, // cumulativeGas (21000)
            new byte[]{(byte) 0x52, 0x08}, // gasUsed (21000)
            new Bloom(),
            new ArrayList<>(),
            TransactionReceipt.SUCCESS_STATUS
        );
        receipt.setTransaction(tx);
        return receipt;
    }

    private LogInfo createLogInfo() {
        byte[] address = new byte[20];
        address[0] = 0x01;
        
        List<org.ethereum.vm.DataWord> topics = new ArrayList<>();
        topics.add(org.ethereum.vm.DataWord.valueOf(HashUtil.keccak256("TestEvent(uint256)".getBytes())));
        
        byte[] data = new byte[]{1, 2, 3, 4};
        
        return new LogInfo(address, topics, data);
    }
}
