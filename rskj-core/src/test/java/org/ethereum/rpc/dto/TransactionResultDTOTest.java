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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

class TransactionResultDTOTest {

    private static final String HEX_ZERO = "0x0";

    private final TestSystemProperties config = new TestSystemProperties();
    private final byte chainId = config.getNetworkConstants().getChainId();

    @Test
    void remascAddressSerialization() {
        RemascTransaction remascTransaction = new RemascTransaction(new Random().nextLong());

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 42, remascTransaction, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        assertThat(dto.getFrom(), is("0x0000000000000000000000000000000000000000"));
        assertThat(dto.getR(), is(nullValue()));
        assertThat(dto.getS(), is(nullValue()));
        assertThat(dto.getV(), is(nullValue()));
    }

    @Test
    void signedTransactionWithChainIdSerialization() {
        Transaction originalTransaction = CallTransaction.createCallTransaction(
                0, 0, 100000000000000L,
                new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                CallTransaction.Function.fromSignature("get"), chainId);

        originalTransaction.sign(new byte[]{});

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 42, originalTransaction, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertNotNull(dto.getR());
        Assertions.assertNotNull(dto.getS());
        Assertions.assertNotNull(dto.getV());

        String expectedV = String.format("0x%02x", originalTransaction.getSignature().getV() - Transaction.LOWER_REAL_V + Transaction.CHAIN_ID_INC + chainId * 2);

        Assertions.assertEquals(expectedV, dto.getV());
    }

    @Test
    void transactionWithZeroNonce() {
        Transaction originalTransaction = CallTransaction.createCallTransaction(
                0, 0, 100000000000000L,
                new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                CallTransaction.Function.fromSignature("get"), chainId);

        originalTransaction.sign(new byte[]{});

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 42, originalTransaction, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertNotNull(dto.getNonce());
        Assertions.assertEquals("0x0", dto.getNonce());
    }

    @Test
    void transactionWithOneNonceWithoutLeadingZeroes() {
        Transaction originalTransaction = CallTransaction.createCallTransaction(
                1, 0, 100000000000000L,
                new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                CallTransaction.Function.fromSignature("get"), chainId);

        originalTransaction.sign(new byte[]{});

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 42, originalTransaction, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertNotNull(dto.getNonce());
        Assertions.assertEquals("0x1", dto.getNonce());
    }

    @Test
    void transactionRemascHasSignatureNullWhenFlagIsFalse() {
        RemascTransaction remascTransaction = new RemascTransaction(new Random().nextLong());
        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 42, remascTransaction, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        Assertions.assertNull(dto.getV());
        Assertions.assertNull(dto.getR());
        Assertions.assertNull(dto.getS());
    }

    @Test
    void transactionRemascHasSignatureZeroWhenFlagIsTrue() {
        RemascTransaction remascTransaction = new RemascTransaction(new Random().nextLong());
        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 42, remascTransaction, true, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        Assertions.assertEquals(HEX_ZERO, dto.getV());
        Assertions.assertEquals(HEX_ZERO, dto.getR());
        Assertions.assertEquals(HEX_ZERO, dto.getS());
    }
}

