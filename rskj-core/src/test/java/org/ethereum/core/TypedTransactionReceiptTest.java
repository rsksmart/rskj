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
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        ArrayList<RLPElement> inner = RLP.decode2(Arrays.copyOfRange(encoded, 1, encoded.length));
        RLPList body = (RLPList) inner.get(0);
        assertEquals(4, body.size(), "RSKIP-546 Type 1 receipt body should have 4 RLP elements");
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
        ArrayList<RLPElement> inner = RLP.decode2(Arrays.copyOfRange(encoded, 1, encoded.length));
        RLPList body = (RLPList) inner.get(0);
        assertEquals(4, body.size(), "RSKIP-546 standard Type 2 receipt body should have 4 RLP elements");
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
        assertTrue(encoded[1] >= (byte) 0xc0,
            "Second byte should be RLP list marker");
        ArrayList<RLPElement> inner = RLP.decode2(Arrays.copyOfRange(encoded, 1, encoded.length));
        RLPList body = (RLPList) inner.get(0);
        assertEquals(4, body.size(), "RSKIP-545 Type 4 receipt body should have 4 RLP elements");
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
            if (type == TransactionType.TYPE_1
                    || type == TransactionType.TYPE_2
                    || type == TransactionType.TYPE_4) {
                assertArrayEquals(EMPTY_BYTE_ARRAY, decodedReceipt.getGasUsed(),
                    "Four-field typed receipt omits gasUsed on the wire for " + type.getTypeName());
            } else {
                assertArrayEquals(originalReceipt.getGasUsed(), decodedReceipt.getGasUsed(),
                    "GasUsed mismatch for " + type.getTypeName());
            }
            assertArrayEquals(originalReceipt.getStatus(), decodedReceipt.getStatus(),
                "Status mismatch for " + type.getTypeName());
        }
    }

    @Test
    void receiptEncodeDecode() {
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
    void typedReceiptPreservesPrefixAfterCacheInvalidation_withoutSetTransaction() {
        for (TransactionType type : new TransactionType[]{
            TransactionType.TYPE_1,
            TransactionType.TYPE_2,
            TransactionType.TYPE_3,
            TransactionType.TYPE_4
        }) {
            Transaction tx = createTransaction(type);
            TransactionReceipt originalReceipt = createReceipt(tx);
            byte[] encoded = originalReceipt.getEncoded();

            TransactionReceipt decodedReceipt = new TransactionReceipt(encoded);

            decodedReceipt.setTxStatus(true);

            byte[] reEncoded = decodedReceipt.getEncoded();
            assertEquals(type.getByteCode(), reEncoded[0],
                "Prefix lost after cache invalidation for " + type.getTypeName()
                    + ": expected type byte 0x" + String.format("%02x", type.getByteCode())
                    + " but got 0x" + String.format("%02x", reEncoded[0] & 0xff));
        }
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

    @Test
    void decodedTypedReceipt_afterMutation_stillRoundTrips() {
        byte[] stored = createReceipt(createTransaction(TransactionType.TYPE_4)).getEncoded();

        TransactionReceipt decoded = new TransactionReceipt(stored);
        assertNull(decoded.getTransaction(), "a receipt read back from storage carries no transaction");

        decoded.setGasUsed(21000L); // invalidates the cached encoding

        byte[] reEncoded = decoded.getEncoded();
        assertArrayEquals(stored, reEncoded,
                "a four-field body has no per-tx gasUsed, so setGasUsed must not change the encoding");
        assertDoesNotThrow(() -> new TransactionReceipt(reEncoded),
                "a decoded typed receipt must stay decodable after any mutation");
    }

    @Test
    void setStatus_invalidatesCachedEncodingOfDecodedTypedReceipt() {
        byte[] stored = createReceipt(createTransaction(TransactionType.TYPE_4)).getEncoded();

        TransactionReceipt decoded = new TransactionReceipt(stored);
        decoded.setStatus(TransactionReceipt.FAILED_STATUS);

        assertFalse(decoded.isSuccessful());
        assertFalse(Arrays.equals(stored, decoded.getEncoded()),
                "status is field 0 of the four-field body, so it must reach the encoding");
        assertFalse(new TransactionReceipt(decoded.getEncoded()).isSuccessful(),
                "the re-encoded receipt must agree with the in-memory status");
    }

    private Transaction createTransaction(TransactionType type) {
        byte chainId = (byte) 33;
        RskAddress to = RskAddress.nullAddress();
        byte[] nonce = new byte[]{1};
        byte[] gasLimit = new byte[]{(byte) 0x52, 0x08};
        Coin gasPrice = Coin.valueOf(1000);

        return switch (type) {
            case LEGACY -> Transaction.builder()
                    .nonce(nonce)
                    .gasPrice(gasPrice)
                    .gasLimit(gasLimit)
                    .receiveAddress(to.getBytes())
                    .value(Coin.ZERO)
                    .data(EMPTY_BYTE_ARRAY)
                    .chainId(chainId)
                    .build();
            case TYPE_1 -> Rskip546TestSupport.unsignedType1(
                    chainId, to, gasPrice, Coin.ZERO, BigInteger.valueOf(21_000), nonce, EMPTY_BYTE_ARRAY,
                    Rskip546TestSupport.EMPTY_ACCESS_LIST);
            case TYPE_2 -> Rskip546TestSupport.unsignedType2(
                    chainId, to, gasPrice, gasPrice, Coin.ZERO, BigInteger.valueOf(21_000), nonce, EMPTY_BYTE_ARRAY,
                    Rskip546TestSupport.EMPTY_ACCESS_LIST);
            case TYPE_3 -> new Transaction(
                    nonce,
                    gasPrice,
                    gasLimit,
                    to,
                    Coin.ZERO,
                    EMPTY_BYTE_ARRAY,
                    chainId,
                    false,
                    TransactionTypePrefix.typed(TransactionType.TYPE_3),
                    null,
                    null,
                    null,
                    null
            );
            case TYPE_4 -> Rskip545TestSupport.unsignedType4(
                    new RskAddress("0x0000000000000000000000000000000000000002"),
                    gasPrice,
                    gasPrice,
                    EMPTY_BYTE_ARRAY,
                    Rskip545TestSupport.EMPTY_ACCESS_LIST);
        };
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
