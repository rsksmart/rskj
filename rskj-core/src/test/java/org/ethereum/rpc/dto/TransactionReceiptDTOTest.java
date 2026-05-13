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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.util.HexUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.Bloom;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.db.TransactionInfo;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionReceiptDTOTest {

    @Test
    void testOkStatusField() {
        //given
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();
        Coin gasPrice = Coin.valueOf(100);
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);
        when(transaction.getGasPrice()).thenReturn(gasPrice);
        when(transaction.getType()).thenReturn(TransactionType.LEGACY);
        when(transaction.getTypeAsHex()).thenReturn("0x0");

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(new byte[] { 0x01 });

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);

        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO(block, txInfo, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        //when
        String actualStatus = transactionReceiptDTO.getStatus();

        //then
        assertNotNull(actualStatus);
        assertEquals("0x1", actualStatus);
    }

    @Test
    void testErrorStatusField() {
        //given
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();
        Coin gasPrice = Coin.valueOf(100);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);
        when(transaction.getGasPrice()).thenReturn(gasPrice);
        when(transaction.getType()).thenReturn(TransactionType.LEGACY);
        when(transaction.getTypeAsHex()).thenReturn("0x0");

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(new byte[] { 0x00 });

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);

        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO(block, txInfo, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        //when
        String actualStatus = transactionReceiptDTO.getStatus();

        //then
        assertNotNull(actualStatus);
        assertEquals("0x0", actualStatus);
    }

    @Test
    void testErrorStatusFieldUsingEmptyByteArray() {
        //given
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();
        Coin gasPrice = Coin.valueOf(100);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);
        when(transaction.getGasPrice()).thenReturn(gasPrice);
        when(transaction.getType()).thenReturn(TransactionType.LEGACY);
        when(transaction.getTypeAsHex()).thenReturn("0x0");

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(ByteUtil.EMPTY_BYTE_ARRAY);

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);

        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO(block, txInfo, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        //when
        String actualStatus = transactionReceiptDTO.getStatus();

        //then
        assertNotNull(actualStatus);
        assertEquals("0x0", actualStatus);
    }

    @Test
    void testErrorStatusFieldUsingNullByteArray() {
        //given
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();
        Coin gasPrice = Coin.valueOf(100);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);
        when(transaction.getGasPrice()).thenReturn(gasPrice);
        when(transaction.getType()).thenReturn(TransactionType.LEGACY);
        when(transaction.getTypeAsHex()).thenReturn("0x0");

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(null);
        when(txReceipt.getStatus()).thenReturn(null);


        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);

        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO(block, txInfo, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        //when
        String actualStatus = transactionReceiptDTO.getStatus();

        //then
        assertNotNull(actualStatus);
        assertEquals("0x0", actualStatus);
    }

    @Test
    void testType1Transaction_typeIs0x1_andEffectiveGasPriceIsSet() {
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();
        Coin gasPrice = Coin.valueOf(1_000_000_000L);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);
        when(transaction.getGasPrice()).thenReturn(gasPrice);
        when(transaction.getType()).thenReturn(TransactionType.TYPE_1);
        when(transaction.getTypeAsHex()).thenReturn("0x1");

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(new byte[]{0x01});

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);
        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        assertEquals("0x1", dto.getType());
        assertEquals(HexUtils.toQuantityJsonHex(gasPrice.getBytes()), dto.getEffectiveGasPrice());
    }

    @Test
    void testType2Transaction_effectiveGasPriceIsMinOfMaxFees() {
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();
        // For Type 2, tx.getGasPrice() == min(maxPriority, maxFee)
        Coin effectiveGasPrice = Coin.valueOf(10L);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);
        when(transaction.getGasPrice()).thenReturn(effectiveGasPrice);
        when(transaction.getType()).thenReturn(TransactionType.TYPE_2);
        when(transaction.getTypeAsHex()).thenReturn("0x2");

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(new byte[]{0x01});

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);
        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        assertEquals("0x2", dto.getType());
        assertEquals(HexUtils.toQuantityJsonHex(effectiveGasPrice.getBytes()), dto.getEffectiveGasPrice());
    }

    @Test
    void testOverrideGasUsed_whenProvided_overridesReceiptGasUsed() {
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();
        Coin gasPrice = Coin.valueOf(100L);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);
        when(transaction.getGasPrice()).thenReturn(gasPrice);
        when(transaction.getTypeAsHex()).thenReturn("0x1");

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(new byte[]{0x01});

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);
        long overrideGas = 21_000L;
        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()), 0, overrideGas);

        assertEquals("0x5208", dto.getGasUsed(),
                "gasUsed must reflect the overrideGasUsed value (21000 = 0x5208)");
    }

    @Test
    void testOverrideGasUsed_whenNull_usesReceiptGasUsed() {
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Bloom bloom = new Bloom();
        Coin gasPrice = Coin.valueOf(100L);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);
        when(transaction.getGasPrice()).thenReturn(gasPrice);
        when(transaction.getTypeAsHex()).thenReturn("0x0");

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(new byte[]{0x01});
        when(txReceipt.getGasUsed()).thenReturn(new byte[]{0x52, 0x08}); // 21000

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);
        TransactionReceiptDTO dto = new TransactionReceiptDTO(block, txInfo,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()), 0, null);

        assertEquals("0x5208", dto.getGasUsed(),
                "gasUsed must fall back to receipt.getGasUsed() when overrideGasUsed is null");
    }

    @Test
    void testTypeField() {
        //given
        RskAddress rskAddress = RskAddress.nullAddress();
        Keccak256 hash = Keccak256.ZERO_HASH;
        Coin gasPrice = Coin.valueOf(100);
        Bloom bloom = new Bloom();

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(hash);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(rskAddress);
        when(transaction.getReceiveAddress()).thenReturn(rskAddress);
        when(transaction.getGasPrice()).thenReturn(gasPrice);
        when(transaction.getType()).thenReturn(TransactionType.LEGACY);
        when(transaction.getTypeAsHex()).thenReturn("0x0");

        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        when(txReceipt.getTransaction()).thenReturn(transaction);
        when(txReceipt.getLogInfoList()).thenReturn(Collections.emptyList());
        when(txReceipt.getBloomFilter()).thenReturn(bloom);
        when(txReceipt.getStatus()).thenReturn(ByteUtil.EMPTY_BYTE_ARRAY);

        TransactionInfo txInfo = new TransactionInfo(txReceipt, hash.getBytes(), 0);

        TransactionReceiptDTO transactionReceiptDTO = new TransactionReceiptDTO(block, txInfo, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        //when
        String actualType = transactionReceiptDTO.getType();

        //then
        assertNotNull(actualType);
        assertEquals("0x0", actualType);
    }
}
