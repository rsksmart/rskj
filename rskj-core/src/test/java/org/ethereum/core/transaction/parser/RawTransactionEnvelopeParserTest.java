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
package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import org.ethereum.config.Constants;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Rskip546TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.encoder.Type4TransactionEncoder;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.TransactionFactoryHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.ethereum.core.Rskip545TestSupport.DEFAULT_MAX_FEE;
import static org.ethereum.core.Rskip545TestSupport.DEFAULT_MAX_PRIORITY;
import static org.ethereum.core.Rskip545TestSupport.EMPTY_ACCESS_LIST;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RawTransactionEnvelopeParser}.
 * Covers parsing of {@link CallArguments} into typed {@link ParsedRawTransaction} objects,
 * including legacy, Type 1, Type 2 (EIP-1559 and RSK-namespace), and Type 4 (EIP-7702) transactions.
 */
class RawTransactionEnvelopeParserTest {

    private static final byte REGTEST_CHAIN_ID = Constants.regtest().getChainId();

    // -------------------------------------------------------------------------
    // Legacy (Type 0)
    // -------------------------------------------------------------------------

    @Test
    void parse_legacyArgs_buildsCorrectTransaction() {
        Wallet wallet = new Wallet(new HashMapDB());
        RskAddress sender = wallet.addAccount();
        RskAddress receiver = wallet.addAccount();

        CallArguments args = TransactionFactoryHelper.createArguments(sender, receiver);
        Transaction tx = Transaction.fromCallArguments(args, null, (byte) 33);

        assertEquals(BigInteger.valueOf(100000L), tx.getValue().asBigInteger());
        assertEquals(BigInteger.valueOf(10000000000000L), tx.getGasPrice().asBigInteger());
        assertEquals(BigInteger.valueOf(30400L), new BigInteger(1, tx.getGasLimit()));
        assertEquals(33, tx.getChainId());
        assertArrayEquals(new byte[]{0x01}, tx.getNonce());
        assertArrayEquals(new byte[]{}, tx.getData());
        assertArrayEquals(receiver.getBytes(), tx.getReceiveAddress().getBytes());
    }

    @Test
    void parse_withExplicitType0x00_throws() {
        CallArguments args = legacyArgs();
        args.setType("0");

        assertThrows(RskJsonRpcRequestException.class,
                () -> RawTransactionEnvelopeParser.parse(args, null, REGTEST_CHAIN_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0x0", "0x00"})
    void parse_withExplicitHexType0x00_throws(String type) {
        CallArguments args = legacyArgs();
        args.setType(type);

        assertThrows(RskJsonRpcRequestException.class,
                () -> RawTransactionEnvelopeParser.parse(args, null, REGTEST_CHAIN_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0x0", "0x00", "0"})
    void parse_chainIdZero_usesDefaultChainId(String chainIdHex) {
        CallArguments args = legacyArgs();
        args.setChainId(chainIdHex);

        Transaction tx = Transaction.fromCallArguments(args, null, REGTEST_CHAIN_ID);

        assertEquals(REGTEST_CHAIN_ID, tx.getChainId());
    }

    // -------------------------------------------------------------------------
    // Type 1 (EIP-2930)
    // -------------------------------------------------------------------------

    @Test
    void parse_type1_buildsType1Transaction() {
        CallArguments args = legacyArgs();
        args.setType("0x1");
        args.setChainId("0x21");

        Transaction tx = Transaction.fromCallArguments(args, null, REGTEST_CHAIN_ID);

        assertEquals(TransactionType.TYPE_1, tx.getTypePrefix().type());
        assertFalse(tx.getTypePrefix().isRskNamespace());
    }

    // -------------------------------------------------------------------------
    // Type 2 — RSK namespace (0x02 || subtype || legacy body)
    // -------------------------------------------------------------------------

    @Test
    void parse_type2WithRskSubtype_buildsRskNamespaceType2() {
        CallArguments args = legacyArgs();
        args.setType("0x2");
        args.setRskSubtype("0x3");
        args.setNonce("0x1");

        Transaction tx = Transaction.fromCallArguments(args, null, REGTEST_CHAIN_ID);

        assertEquals(TransactionType.TYPE_2, tx.getTypePrefix().type());
        assertTrue(tx.getTypePrefix().isRskNamespace(),
                "Type 2 with an RSK subtype must be parsed as the RSK-namespace variant");
        assertEquals((byte) 0x03, tx.getTypePrefix().subtype());
    }

    @Test
    void parse_type2RskNamespace_acceptsAbsentMaxFees() {
        CallArguments args = baseType2Args();
        args.setGasPrice("0x1");
        args.setRskSubtype("0x3");

        Transaction tx = Transaction.fromCallArguments(args, null, REGTEST_CHAIN_ID);

        assertEquals(TransactionType.TYPE_2, tx.getTypePrefix().type());
        assertTrue(tx.getTypePrefix().isRskNamespace());
    }

    // -------------------------------------------------------------------------
    // Type 2 — standard EIP-1559
    // -------------------------------------------------------------------------

    @Test
    void parse_type2Standard_propagatesMaxFees() {
        CallArguments args = baseType2Args();
        args.setMaxPriorityFeePerGas("0xa");
        args.setMaxFeePerGas("0x64");

        Transaction tx = Transaction.fromCallArguments(args, null, REGTEST_CHAIN_ID);

        assertEquals(TransactionType.TYPE_2, tx.getTypePrefix().type());
        assertFalse(tx.getTypePrefix().isRskNamespace());
        assertEquals(Coin.valueOf(10), tx.getMaxPriorityFeePerGas());
        assertEquals(Coin.valueOf(100), tx.getMaxFeePerGas());
        assertEquals(Coin.valueOf(10), tx.getGasPrice());
    }

    @Test
    void parse_type2Standard_missingBothMaxFees_throws() {
        CallArguments args = baseType2Args();
        args.setGasPrice("0x7d0");

        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class,
                () -> RawTransactionEnvelopeParser.parse(args, null, REGTEST_CHAIN_ID));
        assertTrue(ex.getMessage().contains("Type 2"),
                "Error must mention Type 2 context, got: " + ex.getMessage());
    }

    @Test
    void parse_type2Standard_missingMaxPriorityFee_throws() {
        CallArguments args = baseType2Args();
        args.setMaxFeePerGas("0x64");

        assertThrows(RskJsonRpcRequestException.class,
                () -> RawTransactionEnvelopeParser.parse(args, null, REGTEST_CHAIN_ID));
    }

    @Test
    void parse_type2Standard_missingMaxFee_throws() {
        CallArguments args = baseType2Args();
        args.setMaxPriorityFeePerGas("0x5");

        assertThrows(RskJsonRpcRequestException.class,
                () -> RawTransactionEnvelopeParser.parse(args, null, REGTEST_CHAIN_ID));
    }

    // -------------------------------------------------------------------------
    // parse(byte[]) — RLP path
    // -------------------------------------------------------------------------

    @Test
    void parse_nullRawData_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> RawTransactionEnvelopeParser.parse((byte[]) null));
    }

    @Test
    void parse_emptyRawData_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> RawTransactionEnvelopeParser.parse(new byte[0]));
    }

    @Test
    void parse_signedLegacyEncoded_buildsCorrectTransaction() {
        Transaction original = buildSignedLegacyTx();
        byte[] encoded = original.getEncoded();

        Transaction rebuilt = Transaction.fromRaw(encoded);

        assertEquals(TransactionType.LEGACY, rebuilt.getType());
        assertArrayEquals(original.getNonce(), rebuilt.getNonce());
        assertEquals(original.getGasPrice(), rebuilt.getGasPrice());
        assertArrayEquals(original.getGasLimit(), rebuilt.getGasLimit());
        assertEquals(original.getReceiveAddress(), rebuilt.getReceiveAddress());
        assertEquals(original.getValue(), rebuilt.getValue());
    }

    @Test
    void parse_signedType1Encoded_buildsType1Transaction() {
        Transaction original = buildSignedType1Tx();
        byte[] encoded = original.getEncoded();

        Transaction rebuilt = Transaction.fromRaw(encoded);

        assertEquals(TransactionType.TYPE_1, rebuilt.getType());
        assertFalse(rebuilt.getTypePrefix().isRskNamespace());
        assertArrayEquals(original.getNonce(), rebuilt.getNonce());
        assertEquals(original.getGasPrice(), rebuilt.getGasPrice());
    }

    @Test
    void parse_signedType2StandardEncoded_buildsType2Transaction() {
        Transaction original = buildSignedType2Tx();
        byte[] encoded = original.getEncoded();

        Transaction rebuilt = Transaction.fromRaw(encoded);

        assertEquals(TransactionType.TYPE_2, rebuilt.getType());
        assertFalse(rebuilt.getTypePrefix().isRskNamespace());
        assertEquals(original.getMaxFeePerGas(), rebuilt.getMaxFeePerGas());
        assertEquals(original.getMaxPriorityFeePerGas(), rebuilt.getMaxPriorityFeePerGas());
    }

    @Test
    void parse_signedRskNamespaceType2Encoded_buildsRskNamespaceTransaction() {
        Transaction original = Transaction.builder()
                .nonce(new byte[]{0x01})
                .gasPrice(Coin.valueOf(1000))
                .gasLimit(BigInteger.valueOf(21000))
                .receiveAddress(new RskAddress("0x0000000000000000000000000000000000000002").getBytes())
                .value(BigInteger.ZERO)
                .chainId(REGTEST_CHAIN_ID)
                .type(TransactionType.TYPE_2, (byte) 0x03)
                .build();
        original.sign(PRIVATE_KEY);
        byte[] encoded = original.getEncoded();

        Transaction rebuilt = Transaction.fromRaw(encoded);

        assertEquals(TransactionType.TYPE_2, rebuilt.getType());
        assertTrue(rebuilt.getTypePrefix().isRskNamespace());
        assertEquals((byte) 0x03, rebuilt.getTypePrefix().subtype());
    }

    // -------------------------------------------------------------------------
    // parse(CallArguments) — nonce supplier
    // -------------------------------------------------------------------------

    @Test
    void parse_nonceAbsentAndSupplierProvided_supplierIsCalledAndNonceIsSet() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        CallArguments args = legacyArgs();
        args.setNonce(null);

        RawTransactionEnvelopeParser.parse(args, () -> {
            supplierCalled.set(true);
            return "0x5";
        }, REGTEST_CHAIN_ID);

        assertTrue(supplierCalled.get(), "Nonce supplier must be called when nonce is absent");
        assertEquals("0x5", args.getNonce());
    }

    @Test
    void parse_noncePresent_supplierIsNotCalled() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        CallArguments args = legacyArgs();
        args.setNonce("0x3");

        RawTransactionEnvelopeParser.parse(args, () -> {
            supplierCalled.set(true);
            return "0x99";
        }, REGTEST_CHAIN_ID);

        assertFalse(supplierCalled.get(), "Nonce supplier must not be called when nonce is already set");
        assertEquals("0x3", args.getNonce());
    }

    @Test
    void parse_nullCallArguments_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> RawTransactionEnvelopeParser.parse(null, null, REGTEST_CHAIN_ID));
    }

    // -------------------------------------------------------------------------
    // Type 4 (EIP-7702 / RSKIP-545)
    // -------------------------------------------------------------------------

    @Test
    void parse_type4_buildsType4Transaction() {
        CallArguments args = baseType4Args();

        Transaction tx = Transaction.fromCallArguments(args, null, REGTEST_CHAIN_ID);

        assertEquals(TransactionType.TYPE_4, tx.getTypePrefix().type());
        assertEquals(1, tx.getAuthorizationList().size());
        assertEquals(Coin.valueOf(10), tx.getMaxPriorityFeePerGas());
        assertEquals(Coin.valueOf(100), tx.getMaxFeePerGas());
    }

    @Test
    void parse_signedType4Encoded_buildsType4Transaction() {
        Transaction original = buildSignedType4Tx();
        byte[] encoded = original.getEncoded();

        Transaction rebuilt = Transaction.fromRaw(encoded);

        assertEquals(TransactionType.TYPE_4, rebuilt.getType());
        assertEquals(original.getMaxFeePerGas(), rebuilt.getMaxFeePerGas());
        assertEquals(1, rebuilt.getAuthorizationList().size());
    }

    @Test
    void parse_type3Unsupported_throws() {
        byte[] raw = ByteUtil.merge(new byte[]{TransactionType.TYPE_3.getByteCode()}, RLP.encodeList(
                RLP.encodeByte(REGTEST_CHAIN_ID),
                RLP.encodeElement(new byte[0]),
                RLP.encodeCoinNonNullZero(DEFAULT_MAX_PRIORITY),
                RLP.encodeCoinNonNullZero(DEFAULT_MAX_FEE),
                RLP.encodeElement(java.math.BigInteger.valueOf(21_000).toByteArray()),
                RLP.encodeElement(Rskip545TestSupport.DEFAULT_RECEIVER.getBytes()),
                RLP.encodeElement(new byte[0]),
                RLP.encodeElement(new byte[0]),
                EMPTY_ACCESS_LIST,
                RLP.encodeList(),
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[32])
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RawTransactionEnvelopeParser.parse(raw));

        assertTrue(ex.getMessage().contains("Unsupported transaction type"));
    }

    @Test
    void parse_type4PlaceholderSignatureFields_parses() {
        Transaction tx = buildSignedType4Tx();
        byte[] raw = new Type4TransactionEncoder().encodeSigned(tx);

        assertDoesNotThrow(() -> RawTransactionEnvelopeParser.parse(raw));
        assertEquals(13, RLP.decodeList(java.util.Arrays.copyOfRange(raw, 1, raw.length)).size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static final byte[] PRIVATE_KEY = new byte[32];
    static {
        for (int i = 0; i < 32; i++) PRIVATE_KEY[i] = (byte) (i + 1);
    }

    private static Transaction buildSignedLegacyTx() {
        Transaction tx = Transaction.builder()
                .nonce(new byte[]{0x01})
                .gasPrice(Coin.valueOf(1000))
                .gasLimit(BigInteger.valueOf(21000))
                .receiveAddress(new RskAddress("0x0000000000000000000000000000000000000002").getBytes())
                .value(BigInteger.ZERO)
                .chainId(REGTEST_CHAIN_ID)
                .build();
        tx.sign(PRIVATE_KEY);
        return tx;
    }

    private static Transaction buildSignedType1Tx() {
        Transaction tx = Rskip546TestSupport.unsignedType1(
                REGTEST_CHAIN_ID,
                new RskAddress("0x0000000000000000000000000000000000000002"),
                Coin.valueOf(1000),
                new byte[0],
                Rskip546TestSupport.EMPTY_ACCESS_LIST);
        tx.sign(PRIVATE_KEY);
        return tx;
    }

    private static Transaction buildSignedType2Tx() {
        Coin fee = Coin.valueOf(500);
        Transaction tx = Rskip546TestSupport.unsignedType2(
                REGTEST_CHAIN_ID,
                new RskAddress("0x0000000000000000000000000000000000000002"),
                fee,
                fee,
                new byte[0],
                Rskip546TestSupport.EMPTY_ACCESS_LIST);
        tx.sign(PRIVATE_KEY);
        return tx;
    }

    private static CallArguments legacyArgs() {
        CallArguments args = new CallArguments();
        args.setFrom("0x0000000000000000000000000000000000000001");
        args.setTo("0x0000000000000000000000000000000000000002");
        args.setGas("0x5208");
        args.setGasPrice("0x1");
        args.setValue("0x0");
        args.setNonce("0x1");
        return args;
    }

    private static CallArguments baseType2Args() {
        CallArguments args = new CallArguments();
        args.setFrom("0x0000000000000000000000000000000000000001");
        args.setTo("0x0000000000000000000000000000000000000002");
        args.setGas("0x5208");
        args.setValue("0x0");
        args.setNonce("0x1");
        args.setType("0x2");
        args.setChainId("0x21");
        return args;
    }

    private static Transaction buildSignedType4Tx() {
        Transaction tx = Rskip545TestSupport.unsignedType4();
        tx.sign(PRIVATE_KEY);
        return tx;
    }

    private static CallArguments baseType4Args() {
        CallArguments args = new CallArguments();
        args.setFrom("0x0000000000000000000000000000000000000001");
        args.setTo("0x0000000000000000000000000000000000000002");
        args.setGas("0x5208");
        args.setValue("0x0");
        args.setNonce("0x1");
        args.setType("0x4");
        args.setChainId("0x21");
        args.setMaxPriorityFeePerGas("0xa");
        args.setMaxFeePerGas("0x64");

        CallArguments.AuthorizationListEntry entry = new CallArguments.AuthorizationListEntry();
        entry.setChainId("0x21");
        entry.setAddress("0x0000000000000000000000000000000000000003");
        entry.setNonce("0x0");
        entry.setYParity("0x0");
        entry.setR("0x01");
        entry.setS("0x01");
        args.setAuthorizationList(List.of(entry));
        return args;
    }
}
