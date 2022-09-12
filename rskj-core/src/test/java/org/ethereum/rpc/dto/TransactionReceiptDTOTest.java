/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package org.ethereum.rpc.dto;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.Bloom;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.TransactionInfo;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionReceiptDTOTest {

    @Test
    void testOkStatusField() {
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender()).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(new byte[] { 0x01 });

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);

        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO(block, txInfo);

        String actualStatus = transactionReceiptDTO.getStatus();

        assertNotNull(actualStatus);
        assertEquals("0x1", actualStatus);
    }

    @Test
    void testErrorStatusField() {
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender()).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(new byte[] { 0x00 });

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);

        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO(block, txInfo);

        String actualStatus = transactionReceiptDTO.getStatus();

        assertNotNull(actualStatus);
        assertEquals("0x0", actualStatus);
    }

    @Test
    void testErrorStatusFieldUsingEmptyByteArray() {
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender()).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(ByteUtil.EMPTY_BYTE_ARRAY);

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);

        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO(block, txInfo);

        String actualStatus = transactionReceiptDTO.getStatus();

        assertNotNull(actualStatus);
        assertEquals("0x0", actualStatus);
    }

    @Test
    void testErrorStatusFieldUsingNullByteArray() {
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender()).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(null);

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);

        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO(block, txInfo);

        String actualStatus = transactionReceiptDTO.getStatus();

        assertNotNull(actualStatus);
        assertEquals("0x0", actualStatus);
    }
}
