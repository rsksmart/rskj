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
 */
class TypedTransactionReceiptTest {

    @Test
    void legacyReceiptEncoding() {
        Transaction tx = createTransaction(TransactionType.LEGACY);
        TransactionReceipt receipt = createReceipt(tx);
        byte[] encoded = receipt.getEncoded();

        assertTrue(encoded[0] >= (byte) 0xc0,
            "Legacy receipt should start with RLP list marker (>= 0xc0), got: " +
            String.format("0x%02x", encoded[0] & 0xff));
    }

    @Test
    void type1ReceiptEncoding() {
        Transaction tx = createTransaction(TransactionType.TYPE_1);
        TransactionReceipt receipt = createReceipt(tx);
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x01, encoded[0],
            "Type 1 receipt should start with 0x01 prefix");
        assertTrue(encoded[1] >= (byte) 0xc0,
            "Second byte should be RLP list marker");
    }

    @Test
    void type2ReceiptEncoding() {
        Transaction tx = createTransaction(TransactionType.TYPE_2);
        TransactionReceipt receipt = createReceipt(tx);
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x02, encoded[0],
            "Type 2 receipt should start with 0x02 prefix");
        assertTrue(encoded[1] >= (byte) 0xc0,
            "Second byte should be RLP list marker");
    }

    @Test
    void type3ReceiptEncoding() {
        Transaction tx = createTransaction(TransactionType.TYPE_3);
        TransactionReceipt receipt = createReceipt(tx);
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x03, encoded[0],
            "Type 3 receipt should start with 0x03 prefix");
    }

    @Test
    void type4ReceiptEncoding() {
        Transaction tx = createTransaction(TransactionType.TYPE_4);
        TransactionReceipt receipt = createReceipt(tx);
        byte[] encoded = receipt.getEncoded();

        assertEquals((byte) 0x04, encoded[0],
            "Type 4 receipt should start with 0x04 prefix");
    }

    @Test
    void legacyReceiptDecoding() {
        Transaction tx = createTransaction(TransactionType.LEGACY);
        TransactionReceipt originalReceipt = createReceipt(tx);
        byte[] encoded = originalReceipt.getEncoded();

        TransactionReceipt decodedReceipt = new TransactionReceipt(encoded);
        decodedReceipt.setTransaction(tx);

        assertArrayEquals(originalReceipt.getPostTxState(), decodedReceipt.getPostTxState());
        assertArrayEquals(originalReceipt.getCumulativeGas(), decodedReceipt.getCumulativeGas());
        assertArrayEquals(originalReceipt.getGasUsed(), decodedReceipt.getGasUsed());
        assertArrayEquals(originalReceipt.getStatus(), decodedReceipt.getStatus());
    }

    @Test
    void typedReceiptDecoding() {
        for (TransactionType type : new TransactionType[]{
            TransactionType.TYPE_1,
            TransactionType.TYPE_2,
            TransactionType.TYPE_3,
            TransactionType.TYPE_4
        }) {
            Transaction tx = createTransaction(type);
            TransactionReceipt originalReceipt = createReceipt(tx);
            byte[] encoded = originalReceipt.getEncoded();

            assertEquals(type.getByteCode(), encoded[0],
                "First byte should be transaction type: " + type.getTypeName());

            TransactionReceipt decodedReceipt = new TransactionReceipt(encoded);
            decodedReceipt.setTransaction(tx);

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
    void receiptRoundTrip() {
        for (TransactionType type : TransactionType.values()) {
            Transaction tx = createTransaction(type);
            TransactionReceipt originalReceipt = createReceipt(tx);

            List<LogInfo> logs = new ArrayList<>();
            logs.add(createLogInfo());
            originalReceipt.setLogInfoList(logs);

            byte[] encoded = originalReceipt.getEncoded();
            TransactionReceipt decodedReceipt = new TransactionReceipt(encoded);
            decodedReceipt.setTransaction(tx);
            byte[] reEncoded = decodedReceipt.getEncoded();

            assertArrayEquals(encoded, reEncoded,
                "Round-trip encoding failed for " + type.getTypeName());
        }
    }

    @Test
    void receiptWithoutTransactionDefaultsToLegacy() {
        TransactionReceipt receipt = new TransactionReceipt(
            new byte[]{1},
            new byte[]{100},
            new byte[]{50},
            new Bloom(),
            new ArrayList<>(),
            TransactionReceipt.SUCCESS_STATUS
        );

        byte[] encoded = receipt.getEncoded();

        assertTrue(encoded[0] >= (byte) 0xc0,
            "Receipt without transaction should default to legacy format");
    }

    @Test
    void receiptStatusSuccess() {
        Transaction tx = createTransaction(TransactionType.TYPE_1);
        TransactionReceipt receipt = createReceipt(tx);
        receipt.setStatus(TransactionReceipt.SUCCESS_STATUS);

        assertTrue(receipt.isSuccessful(), "Receipt should be successful");
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt.getStatus());
    }

    @Test
    void receiptStatusFailed() {
        Transaction tx = createTransaction(TransactionType.TYPE_2);
        TransactionReceipt receipt = createReceipt(tx);
        receipt.setStatus(TransactionReceipt.FAILED_STATUS);

        assertFalse(receipt.isSuccessful(), "Receipt should be failed");
        assertArrayEquals(TransactionReceipt.FAILED_STATUS, receipt.getStatus());
    }

    private Transaction createTransaction(TransactionType type) {
        return Transaction.builder()
            .nonce(new byte[]{1})
            .gasPrice(Coin.valueOf(1000))
            .gasLimit(new byte[]{(byte) 0x52, 0x08})
            .destination(RskAddress.nullAddress().getBytes())
            .value(Coin.ZERO)
            .data(EMPTY_BYTE_ARRAY)
            .chainId((byte) 33)
            .type(type)
            .build();
    }

    private TransactionReceipt createReceipt(Transaction tx) {
        TransactionReceipt receipt = new TransactionReceipt(
            new byte[]{1},
            new byte[]{(byte) 0x52, 0x08},
            new byte[]{(byte) 0x52, 0x08},
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
