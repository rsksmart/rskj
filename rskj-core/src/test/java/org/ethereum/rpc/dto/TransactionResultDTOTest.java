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
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Rskip546TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
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
    void type4Transaction_populatesAuthorizationListAndMaxFeeFields() throws Exception {
        RskAddress delegate = new RskAddress("0x0000000000000000000000000000000000000003");
        SetCodeAuthorization auth1 = Rskip545TestSupport.createSignedAuthorization(
                new org.ethereum.crypto.ECKey(), delegate, BigInteger.ZERO, (byte) 33);
        SetCodeAuthorization auth2 = Rskip545TestSupport.createSignedAuthorization(
                new org.ethereum.crypto.ECKey(), delegate, BigInteger.ZERO, (byte) 33);
        SetCodeAuthorization auth3 = Rskip545TestSupport.createSignedAuthorization(
                new org.ethereum.crypto.ECKey(), delegate, BigInteger.ZERO, (byte) 33);
        Transaction tx = Rskip545TestSupport.unsignedType4WithAuthorizations(
                new RskAddress("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"),
                BigInteger.valueOf(100_000),
                List.of(auth1, auth2, auth3));
        tx.sign(new byte[]{});

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 0, tx, false,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertEquals("0x4", dto.getType());
        Assertions.assertNotNull(dto.getAuthorizationList());
        Assertions.assertEquals(3, dto.getAuthorizationList().size());
        Assertions.assertEquals(delegate.toJsonString(), dto.getAuthorizationList().get(0).getAddress());
        Assertions.assertNotNull(dto.getMaxFeePerGas());
        Assertions.assertNotNull(dto.getMaxPriorityFeePerGas());
        Assertions.assertNotNull(dto.getChainId());

        JsonNode json = new ObjectMapper().valueToTree(dto);
        Assertions.assertTrue(json.has("authorizationList"));
        Assertions.assertEquals(3, json.get("authorizationList").size());
    }

    @Test
    void legacyTransaction_omitsAuthorizationListFromDtoAndJson() throws Exception {
        Transaction originalTransaction = CallTransaction.createCallTransaction(
                1, 0, 100000000000000L,
                new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                CallTransaction.Function.fromSignature("get"), chainId);
        originalTransaction.sign(new byte[]{});

        TransactionResultDTO dto = new TransactionResultDTO(null, null, originalTransaction, false,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertNull(dto.getAuthorizationList());
        JsonNode json = new ObjectMapper().valueToTree(dto);
        Assertions.assertFalse(json.has("authorizationList"));
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

    @Test
    void type1Transaction_decodesNonemptyAccessList() {
        byte[] address = new RskAddress("0x095e7baea6a6c7c4c2dfeb977efac326af552d87").getBytes();
        byte[] storageKey = new byte[32];
        storageKey[31] = 0x01;
        byte[] accessList = RLP.encodeList(
                RLP.encodeList(
                        RLP.encodeElement(address),
                        RLP.encodeList(RLP.encodeElement(storageKey))
                )
        );
        Transaction tx = Rskip546TestSupport.unsignedType1(
                (byte) 33,
                new RskAddress("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"),
                Coin.valueOf(1_000_000_000L),
                BigInteger.ONE.toByteArray(),
                new byte[0],
                accessList);
        tx.sign(new byte[]{});

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 0, tx, false,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertEquals(1, dto.getAccessList().size());
        TransactionResultDTO.AccessListEntryDTO entry = dto.getAccessList().get(0);
        Assertions.assertTrue(entry.getAddress().startsWith("0x"));
        Assertions.assertEquals(1, entry.getStorageKeys().size());
        Assertions.assertTrue(entry.getStorageKeys().get(0).startsWith("0x"));
    }

    @Test
    void type1Transaction_corruptAccessList_returnsEmptyList() {
        Transaction signed = Rskip546TestSupport.unsignedType1(
                (byte) 33,
                new RskAddress("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"),
                Coin.valueOf(1_000_000_000L),
                BigInteger.ONE.toByteArray(),
                new byte[0],
                Rskip546TestSupport.EMPTY_ACCESS_LIST);
        signed.sign(new byte[]{});
        Transaction tx = new Transaction(
                signed.getNonce(),
                signed.getGasPrice(),
                signed.getGasLimit(),
                signed.getReceiveAddress(),
                signed.getValue(),
                signed.getData(),
                signed.getChainId(),
                false,
                signed.getTypePrefix(),
                new byte[]{(byte) 0xff, 0x01},
                null,
                null,
                null);
        tx.setSignature(signed.getSignature());

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 0, tx, false,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertNotNull(dto.getAccessList());
        Assertions.assertTrue(dto.getAccessList().isEmpty());
    }

    @Test
    void authorizationListEntryDto_exposesFields() {
        TransactionResultDTO.AuthorizationListEntryDTO entry =
                new TransactionResultDTO.AuthorizationListEntryDTO(
                        "0x21", "0x02", "0x0", "0x0", "0x1", "0x2");

        Assertions.assertEquals("0x21", entry.getChainId());
        Assertions.assertEquals("0x02", entry.getAddress());
        Assertions.assertEquals("0x0", entry.getNonce());
        Assertions.assertEquals("0x0", entry.getYParity());
        Assertions.assertEquals("0x1", entry.getR());
        Assertions.assertEquals("0x2", entry.getS());
    }

    @Test
    void encodeAuthorizationList_nullOrEmpty_returnsEmptyList() throws Exception {
        Method encode = TransactionResultDTO.class.getDeclaredMethod(
                "encodeAuthorizationList", List.class);
        encode.setAccessible(true);

        Assertions.assertEquals(Collections.emptyList(), encode.invoke(null, (Object) null));
        Assertions.assertEquals(Collections.emptyList(), encode.invoke(null, List.of()));
    }

    @Test
    void type1Transaction_nullAccessListBytes_returnsEmptyList() {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getType()).thenReturn(TransactionType.TYPE_1);
        Mockito.when(tx.getTypePrefix())
                .thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_1));
        Mockito.when(tx.getTypeAsHex()).thenReturn("0x1");
        Mockito.when(tx.getHash()).thenReturn(new co.rsk.crypto.Keccak256(new byte[32]));
        Mockito.when(tx.getNonce()).thenReturn(new byte[]{0x01});
        Mockito.when(tx.getGasLimit()).thenReturn(BigInteger.valueOf(21_000).toByteArray());
        Mockito.when(tx.getGasPrice()).thenReturn(Coin.valueOf(10));
        Mockito.when(tx.getReceiveAddress())
                .thenReturn(new RskAddress("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"));
        Mockito.when(tx.getValue()).thenReturn(Coin.ZERO);
        Mockito.when(tx.getData()).thenReturn(new byte[0]);
        Mockito.when(tx.getSender(any()))
                .thenReturn(new RskAddress("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"));
        Mockito.when(tx.getSignature())
                .thenReturn(ECDSASignature.fromComponents(new byte[32], new byte[32], (byte) 27));
        Mockito.when(tx.getEncodedV()).thenReturn((byte) 0);
        Mockito.when(tx.getChainId()).thenReturn((byte) 33);
        Mockito.when(tx.getAccessListBytes()).thenReturn(null);

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 0, tx, false,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertNotNull(dto.getAccessList());
        Assertions.assertTrue(dto.getAccessList().isEmpty());
    }

    @Test
    void type2Transaction_omitsNullMaxFeeFieldsFromDto() {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getType()).thenReturn(TransactionType.TYPE_2);
        Mockito.when(tx.getTypePrefix())
                .thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_2));
        Mockito.when(tx.getTypeAsHex()).thenReturn("0x2");
        Mockito.when(tx.getHash()).thenReturn(new co.rsk.crypto.Keccak256(new byte[32]));
        Mockito.when(tx.getNonce()).thenReturn(new byte[]{0x01});
        Mockito.when(tx.getGasLimit()).thenReturn(BigInteger.valueOf(21_000).toByteArray());
        Mockito.when(tx.getGasPrice()).thenReturn(Coin.valueOf(10));
        Mockito.when(tx.getReceiveAddress())
                .thenReturn(new RskAddress("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"));
        Mockito.when(tx.getValue()).thenReturn(Coin.ZERO);
        Mockito.when(tx.getData()).thenReturn(new byte[0]);
        Mockito.when(tx.getSender(any()))
                .thenReturn(new RskAddress("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"));
        Mockito.when(tx.getSignature())
                .thenReturn(ECDSASignature.fromComponents(new byte[32], new byte[32], (byte) 27));
        Mockito.when(tx.getEncodedV()).thenReturn((byte) 0);
        Mockito.when(tx.getChainId()).thenReturn((byte) 33);
        Mockito.when(tx.getAccessListBytes()).thenReturn(Rskip546TestSupport.EMPTY_ACCESS_LIST);
        Mockito.when(tx.getMaxPriorityFeePerGas()).thenReturn(null);
        Mockito.when(tx.getMaxFeePerGas()).thenReturn(null);

        TransactionResultDTO dto = new TransactionResultDTO(mock(Block.class), 0, tx, false,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        Assertions.assertNull(dto.getMaxFeePerGas());
        Assertions.assertNull(dto.getMaxPriorityFeePerGas());
    }
}
