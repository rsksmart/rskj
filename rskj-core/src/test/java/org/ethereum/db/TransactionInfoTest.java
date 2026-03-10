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
package org.ethereum.db;

import org.ethereum.core.Bloom;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TransactionInfoTest {

    private static final byte[] BLOCK_HASH = new byte[32];
    static {
        BLOCK_HASH[0] = (byte) 0xab;
        BLOCK_HASH[31] = (byte) 0xcd;
    }

    private static final byte[] STATUS_SUCCESS = new byte[]{0x01};
    private static final byte[] STATUS_FAILED = new byte[0];

    /**
     * Simulates the encoding format used BEFORE the encodeElement wrapping was added.
     * Old format: receipt bytes placed directly in the list (as a sub-list element).
     */
    private static byte[] encodeLegacyFormat(TransactionReceipt receipt, byte[] blockHash, int index) {
        byte[] receiptRLP = receipt.getEncoded();
        byte[] blockHashRLP = RLP.encodeElement(blockHash);
        byte[] indexRLP = RLP.encodeInt(index);
        return RLP.encodeList(receiptRLP, blockHashRLP, indexRLP);
    }

    static Stream<TransactionReceipt> receiptVariants() {
        return Stream.of(
                createMinimalReceipt(),
                createReceiptWithLogs(),
                createFailedReceipt(),
                createLargeGasReceipt()
        );
    }

    @ParameterizedTest
    @MethodSource("receiptVariants")
    void decodeOldFormat(TransactionReceipt original) {
        int index = 3;

        byte[] oldFormatBytes = encodeLegacyFormat(original, BLOCK_HASH, index);
        TransactionInfo decoded = new TransactionInfo(oldFormatBytes);

        assertReceiptFieldsMatch(original, decoded.getReceipt());
        assertArrayEquals(BLOCK_HASH, decoded.getBlockHash());
        assertEquals(index, decoded.getIndex());
    }

    @ParameterizedTest
    @MethodSource("receiptVariants")
    void newFormatEncodeDecode(TransactionReceipt original) {
        int index = 5;

        TransactionInfo info = new TransactionInfo(original, BLOCK_HASH, index);
        byte[] encoded = info.getEncoded();
        TransactionInfo decoded = new TransactionInfo(encoded);

        assertReceiptFieldsMatch(original, decoded.getReceipt());
        assertArrayEquals(BLOCK_HASH, decoded.getBlockHash());
        assertEquals(index, decoded.getIndex());
    }

    @ParameterizedTest
    @MethodSource("receiptVariants")
    void oldAndNewFormatProduceSameReceiptFields(TransactionReceipt original) {
        int index = 7;

        byte[] oldBytes = encodeLegacyFormat(original, BLOCK_HASH, index);
        TransactionInfo fromOld = new TransactionInfo(oldBytes);

        byte[] newBytes = new TransactionInfo(original, BLOCK_HASH, index).getEncoded();
        TransactionInfo fromNew = new TransactionInfo(newBytes);

        assertReceiptFieldsMatch(fromOld.getReceipt(), fromNew.getReceipt());
        assertArrayEquals(fromOld.getBlockHash(), fromNew.getBlockHash());
        assertEquals(fromOld.getIndex(), fromNew.getIndex());
    }

    @Test
    void decodeOldFormatWithZeroIndex() {
        TransactionReceipt original = createMinimalReceipt();

        byte[] oldFormatBytes = encodeLegacyFormat(original, BLOCK_HASH, 0);
        TransactionInfo decoded = new TransactionInfo(oldFormatBytes);

        assertEquals(0, decoded.getIndex());
        assertArrayEquals(BLOCK_HASH, decoded.getBlockHash());
    }

    @Test
    void oldFormatEncodingDiffersFromNewFormat() {
        TransactionReceipt receipt = createMinimalReceipt();
        int index = 1;

        byte[] oldBytes = encodeLegacyFormat(receipt, BLOCK_HASH, index);
        byte[] newBytes = new TransactionInfo(receipt, BLOCK_HASH, index).getEncoded();

        assertFalse(Arrays.equals(oldBytes, newBytes),
                "Old and new encodings should differ (sub-list vs byte-string wrapping)");
    }

    private static void assertReceiptFieldsMatch(TransactionReceipt expected, TransactionReceipt actual) {
        assertArrayEquals(expected.getPostTxState(), actual.getPostTxState());
        assertArrayEquals(expected.getCumulativeGas(), actual.getCumulativeGas());
        assertArrayEquals(expected.getGasUsed(), actual.getGasUsed());
        assertArrayEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getLogInfoList().size(), actual.getLogInfoList().size());
    }

    /** Minimal receipt: no logs, small gas, success. Short-form RLP (< 55 bytes). */
    private static TransactionReceipt createMinimalReceipt() {
        return new TransactionReceipt(
                new byte[]{0x01},
                new byte[]{0x00, 0x52, 0x08},
                new byte[]{0x52, 0x08},
                new Bloom(),
                Collections.emptyList(),
                STATUS_SUCCESS);
    }

    /** Receipt with logs and bloom data. Triggers long-form RLP (> 55 bytes). */
    private static TransactionReceipt createReceiptWithLogs() {
        byte[] contractAddr = new byte[20];
        contractAddr[0] = (byte) 0xaa;
        DataWord topic = DataWord.valueOf(42);
        byte[] logData = new byte[]{0x01, 0x02, 0x03, 0x04};

        LogInfo log = new LogInfo(contractAddr, List.of(topic), logData);
        Bloom bloom = new Bloom();
        bloom.or(log.getBloom());

        return new TransactionReceipt(
                new byte[]{0x01},
                new byte[]{0x01, 0x00, 0x00},
                new byte[]{0x00, (byte) 0x80, 0x00},
                bloom,
                List.of(log),
                STATUS_SUCCESS);
    }

    /** Failed receipt: empty status, empty postTxState. */
    private static TransactionReceipt createFailedReceipt() {
        return new TransactionReceipt(
                new byte[0],
                new byte[]{0x00, 0x52, 0x08},
                new byte[]{0x52, 0x08},
                new Bloom(),
                Collections.emptyList(),
                STATUS_FAILED);
    }

    /** Receipt with large gas values to exercise multi-byte RLP length encoding. */
    private static TransactionReceipt createLargeGasReceipt() {
        byte[] largeCumulativeGas = new byte[]{0x3B, (byte) 0x9A, (byte) 0xCA, 0x00};
        byte[] largeGasUsed = new byte[]{0x1D, (byte) 0xCD, 0x65, 0x00};

        return new TransactionReceipt(
                new byte[]{0x01},
                largeCumulativeGas,
                largeGasUsed,
                new Bloom(),
                Collections.emptyList(),
                STATUS_SUCCESS);
    }
}
