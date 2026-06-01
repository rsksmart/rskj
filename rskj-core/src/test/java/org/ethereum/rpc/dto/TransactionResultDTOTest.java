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
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.remasc.RemascTransaction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Rskip546TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.TransactionType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

class TransactionResultDTOTest {

    private static final String HEX_ZERO = "0x0";

    private final TestSystemProperties config = new TestSystemProperties();
    private final byte chainId = config.getNetworkConstants().getChainId();

    @Test
    void remascAddressSerialization() {
        RemascTransaction remascTransaction = new RemascTransaction(TestUtils.generateLong("remascTransaction"));

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
        RemascTransaction remascTransaction = new RemascTransaction(TestUtils.generateLong("remascTransaction"));
        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 42, remascTransaction, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        Assertions.assertNull(dto.getV());
        Assertions.assertNull(dto.getR());
        Assertions.assertNull(dto.getS());
    }

    @Test
    void transactionRemascHasSignatureZeroWhenFlagIsTrue() {
        RemascTransaction remascTransaction = new RemascTransaction(TestUtils.generateLong("remascTransaction"));
        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 42, remascTransaction, true, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        Assertions.assertEquals(HEX_ZERO, dto.getV());
        Assertions.assertEquals(HEX_ZERO, dto.getR());
        Assertions.assertEquals(HEX_ZERO, dto.getS());
    }

    @Test
    void transactionResultHasType() {
        Transaction originalTransaction = CallTransaction.createCallTransaction(
                1, 0, 100000000000000L,
                new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                CallTransaction.Function.fromSignature("get"), chainId);

        originalTransaction.sign(new byte[]{});

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 42, originalTransaction, false, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertEquals("0x0", dto.getType());
    }

    @Test
    void type1Transaction_populatesChainIdAccessListAndYParity_omitsMaxFeeFields() throws Exception {
        Transaction tx = Rskip546TestSupport.unsignedType1(
                (byte) 33,
                new RskAddress("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"),
                Coin.valueOf(1_000_000_000L),
                BigInteger.ONE.toByteArray(),
                new byte[0],
                Rskip546TestSupport.EMPTY_ACCESS_LIST);
        tx.sign(new byte[]{});

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 0, tx, false,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertEquals("0x1", dto.getType());
        Assertions.assertNotNull(dto.getChainId(), "chainId must be present for Type 1");
        Assertions.assertNotNull(dto.getAccessList(), "accessList must be present (empty list) for Type 1");
        Assertions.assertNotNull(dto.getYParity(), "yParity must be present for Type 1");
        Assertions.assertNull(dto.getMaxFeePerGas(), "maxFeePerGas must be absent for Type 1");
        Assertions.assertNull(dto.getMaxPriorityFeePerGas(), "maxPriorityFeePerGas must be absent for Type 1");

        JsonNode json = new ObjectMapper().valueToTree(dto);
        Assertions.assertTrue(json.has("chainId"), "chainId must appear in JSON for Type 1");
        Assertions.assertTrue(json.has("accessList"), "accessList must appear in JSON for Type 1");
        Assertions.assertFalse(json.has("maxFeePerGas"), "maxFeePerGas must be omitted from JSON for Type 1");
        Assertions.assertFalse(json.has("maxPriorityFeePerGas"),
                "maxPriorityFeePerGas must be omitted from JSON for Type 1");
    }

    @Test
    void type2StandardTransaction_populatesAllTypedFieldsIncludingMaxFees() throws Exception {
        Coin maxPriority = Coin.valueOf(10L);
        Coin maxFee = Coin.valueOf(100L);
        Transaction tx = Rskip546TestSupport.unsignedType2(
                (byte) 33,
                new RskAddress("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"),
                maxPriority,
                maxFee,
                BigInteger.ONE.toByteArray(),
                new byte[0],
                Rskip546TestSupport.EMPTY_ACCESS_LIST);
        tx.sign(new byte[]{});

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 0, tx, false,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertEquals("0x2", dto.getType());
        Assertions.assertNotNull(dto.getChainId(), "chainId must be present for Type 2 tx");
        Assertions.assertNotNull(dto.getAccessList(), "accessList must be present for Type 2 tx");
        Assertions.assertNotNull(dto.getYParity(), "yParity must be present for Type 2 tx");
        Assertions.assertNotNull(dto.getMaxFeePerGas(), "maxFeePerGas must be present for Type 2 tx");
        Assertions.assertNotNull(dto.getMaxPriorityFeePerGas(),
                "maxPriorityFeePerGas must be present for Type 2 tx");

        // gasPrice in the DTO reflects the effective gas price = min(maxPriority, maxFee) = 10
        Assertions.assertEquals("0xa", dto.getGasPrice(),
                "gasPrice for Type 2 tx must equal min(maxPriorityFeePerGas, maxFeePerGas)");

        JsonNode json = new ObjectMapper().valueToTree(dto);
        Assertions.assertTrue(json.has("maxFeePerGas"), "maxFeePerGas must appear in JSON for Type 2");
        Assertions.assertTrue(json.has("maxPriorityFeePerGas"),
                "maxPriorityFeePerGas must appear in JSON for Type 2 tx");
    }

    @Test
    void pendingLegacyTransaction_serializesBlockFieldsAsJsonNull() throws Exception {
        Transaction originalTransaction = CallTransaction.createCallTransaction(
                1, 0, 100000000000000L,
                new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                CallTransaction.Function.fromSignature("get"), chainId);
        originalTransaction.sign(new byte[]{});

        // Pending tx: no block, no index.
        TransactionResultDTO dto = new TransactionResultDTO(null, null, originalTransaction, false,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        JsonNode json = new ObjectMapper().valueToTree(dto);

        Assertions.assertTrue(json.has("blockHash"), "blockHash must be present in JSON for pending tx");
        Assertions.assertTrue(json.get("blockHash").isNull(), "blockHash must serialize as JSON null");
        Assertions.assertTrue(json.has("blockNumber"), "blockNumber must be present in JSON for pending tx");
        Assertions.assertTrue(json.get("blockNumber").isNull(), "blockNumber must serialize as JSON null");
        Assertions.assertTrue(json.has("transactionIndex"), "transactionIndex must be present in JSON for pending tx");
        Assertions.assertTrue(json.get("transactionIndex").isNull(), "transactionIndex must serialize as JSON null");

        // Typed-only fields must be OMITTED (not null) for legacy transactions.
        Assertions.assertFalse(json.has("chainId"), "chainId must be omitted from JSON for legacy tx");
        Assertions.assertFalse(json.has("yParity"), "yParity must be omitted from JSON for legacy tx");
        Assertions.assertFalse(json.has("accessList"), "accessList must be omitted from JSON for legacy tx");
        Assertions.assertFalse(json.has("maxFeePerGas"), "maxFeePerGas must be omitted from JSON for legacy tx");
        Assertions.assertFalse(json.has("maxPriorityFeePerGas"),
                "maxPriorityFeePerGas must be omitted from JSON for legacy tx");
    }
}
