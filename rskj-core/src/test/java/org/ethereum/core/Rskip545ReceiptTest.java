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
import co.rsk.crypto.Keccak256;
import co.rsk.util.HexUtils;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.dto.TransactionReceiptDTO;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RSKIP-545 Type 4 transaction receipts (EIP-7702 set-code).
 *
 * <p>Parallel to RSKIP-546 receipt tests in {@link Rskip546DslTest} and {@link TypedTransactionReceiptTest}.
 */
class Rskip545ReceiptTest {

    @Test
    void type4Receipt_startsWith0x04Prefix() {
        TransactionReceipt receipt = receiptForType4Transaction();

        assertEquals((byte) 0x04, receipt.getEncoded()[0],
                "Type 4 receipt must start with 0x04 per RSKIP-545");
    }

    @Test
    void type4Receipt_bodyHasFourFields() {
        byte[] encoded = receiptForType4Transaction().getEncoded();
        byte[] body = new byte[encoded.length - 1];
        System.arraycopy(encoded, 1, body, 0, body.length);

        RLPList fields = RLP.decodeList(body);
        assertEquals(4, fields.size(),
                "RSKIP-545 Type 4 receipt body: status, cumulativeGas, bloom, logs");
    }

    @Test
    void type4Receipt_encodeDecodeRoundTrip() {
        TransactionReceipt original = receiptForType4Transaction();
        TransactionReceipt decoded = new TransactionReceipt(original.getEncoded());
        decoded.setTransaction(original.getTransaction());

        assertArrayEquals(original.getStatus(), decoded.getStatus());
        assertArrayEquals(original.getCumulativeGas(), decoded.getCumulativeGas());
        assertArrayEquals(EMPTY_BYTE_ARRAY, decoded.getGasUsed(),
                "Four-field typed receipt omits per-tx gasUsed on the wire");
        assertArrayEquals(original.getBloomFilter().getData(), decoded.getBloomFilter().getData());
        assertArrayEquals(original.getEncoded(), decoded.getEncoded());
    }

    @Test
    void type4Receipt_withLogs_survivesRoundTrip() {
        TransactionReceipt original = receiptForType4Transaction();
        original.setLogInfoList(List.of(new LogInfo(new byte[20], new ArrayList<>(), new byte[]{0x01})));

        TransactionReceipt decoded = new TransactionReceipt(original.getEncoded());
        decoded.setTransaction(original.getTransaction());

        assertArrayEquals(original.getEncoded(), decoded.getEncoded());
        assertEquals(1, decoded.getLogInfoList().size());
    }

    @Test
    void type4ReceiptDTO_typeAndEffectiveGasPrice() {
        Transaction tx = type4Transaction();
        tx.sign(new ECKey().getPrivKeyBytes());
        TransactionReceipt receipt = receiptFor(tx);
        Block block = mock(Block.class);
        Keccak256 hash = Keccak256.ZERO_HASH;
        when(block.getHash()).thenReturn(hash);

        TransactionInfo txInfo = new TransactionInfo(receipt, hash.getBytes(), 0);
        TransactionReceiptDTO dto = new TransactionReceiptDTO(
                block, txInfo, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        assertEquals("0x4", dto.getType());
        assertEquals(HexUtils.toQuantityJsonHex(tx.getGasPrice().getBytes()), dto.getEffectiveGasPrice());
    }

    private static TransactionReceipt receiptForType4Transaction() {
        return receiptFor(type4Transaction());
    }

    private static TransactionReceipt receiptFor(Transaction tx) {
        TransactionReceipt receipt = new TransactionReceipt(
                new byte[]{1},
                new byte[]{(byte) 0x52, (byte) 0x08},
                new byte[]{(byte) 0x52, (byte) 0x08},
                new Bloom(),
                new ArrayList<>(),
                TransactionReceipt.SUCCESS_STATUS
        );
        receipt.setTransaction(tx);
        return receipt;
    }

    private static Transaction type4Transaction() {
        return Rskip545TestSupport.unsignedType4(
                new RskAddress("0x0000000000000000000000000000000000000002"),
                Coin.valueOf(1_000_000_000L),
                Coin.valueOf(2_000_000_000L),
                new byte[0],
                Rskip545TestSupport.EMPTY_ACCESS_LIST);
    }
}
