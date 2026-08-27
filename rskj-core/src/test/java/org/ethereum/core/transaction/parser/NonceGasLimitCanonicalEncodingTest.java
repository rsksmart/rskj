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

import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.util.AuthorizationListCodec;
import org.ethereum.core.transaction.parser.util.CommonParsingUtils;
import org.ethereum.rpc.CallArguments;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;

import static org.ethereum.core.Rskip546TestSupport.DEFAULT_GAS_PRICE;
import static org.ethereum.core.Rskip546TestSupport.DEFAULT_MAX_FEE;
import static org.ethereum.core.Rskip546TestSupport.DEFAULT_MAX_PRIORITY;
import static org.ethereum.core.Rskip546TestSupport.DEFAULT_RECEIVER;
import static org.ethereum.core.Rskip546TestSupport.EMPTY_ACCESS_LIST;
import static org.ethereum.core.Rskip546TestSupport.REGTEST_CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.PRIVATE_KEY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NonceGasLimitCanonicalEncodingTest {

    private static final BigInteger DEFAULT_NONCE = BigInteger.ONE;
    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(21_000);

    private static final List<BigInteger> BOUNDARIES = List.of(
            BigInteger.ZERO,
            BigInteger.valueOf(127),
            BigInteger.valueOf(128),
            BigInteger.valueOf(255),
            BigInteger.valueOf(256),
            BigInteger.valueOf(32767),
            BigInteger.valueOf(32768)
    );


    private static final List<TransactionType> SUPPORTED_TYPES = List.of(TransactionType.LEGACY, TransactionType.TYPE_1, TransactionType.TYPE_2, TransactionType.TYPE_4);

    @ParameterizedTest(name = "{0} nonce={1}")
    @MethodSource("typesAndBoundaries")
    void nonceIsCanonical(TransactionType type, BigInteger nonce) {
        assertCanonical(type, nonce, DEFAULT_GAS_LIMIT);
    }

    @ParameterizedTest(name = "{0} gasLimit={1}")
    @MethodSource("typesAndBoundaries")
    void gasLimitIsCanonical(TransactionType type, BigInteger gasLimit) {
        assertCanonical(type, DEFAULT_NONCE, gasLimit);
    }

    private static void assertCanonical(TransactionType type, BigInteger nonce, BigInteger gasLimit) {
        Transaction builderTx = build(type, nonce, gasLimit);
        Transaction rpcTx = Transaction.fromCallArguments(callArguments(type, nonce, gasLimit), null, REGTEST_CHAIN_ID);

        byte[] expectedNonce = CommonParsingUtils.unsignedBytes(nonce);
        byte[] expectedGasLimit = CommonParsingUtils.unsignedBytes(gasLimit);

        assertArrayEquals(expectedNonce, builderTx.getNonce());
        assertArrayEquals(expectedNonce, rpcTx.getNonce());
        assertArrayEquals(expectedGasLimit, builderTx.getGasLimit());
        assertArrayEquals(expectedGasLimit, rpcTx.getGasLimit());
        assertArrayEquals(builderTx.getEncodedRaw(), rpcTx.getEncodedRaw());

        builderTx.sign(PRIVATE_KEY);
        rpcTx.sign(PRIVATE_KEY);

        Transaction rawTx = Transaction.fromRaw(builderTx.getEncoded());

        assertArrayEquals(builderTx.getEncoded(), rpcTx.getEncoded());
        assertArrayEquals(builderTx.getEncoded(), rawTx.getEncoded());
        assertEquals(builderTx.getRawHash(), rpcTx.getRawHash());
        assertEquals(builderTx.getRawHash(), rawTx.getRawHash());
        assertEquals(builderTx.getHash(), rpcTx.getHash());
        assertEquals(builderTx.getHash(), rawTx.getHash());
    }

    private static Transaction build(TransactionType type, BigInteger nonce, BigInteger gasLimit) {
        var builder = Transaction.builder()
                .nonce(nonce)
                .gasLimit(gasLimit)
                .receiveAddress(DEFAULT_RECEIVER.getBytes())
                .value(BigInteger.ZERO)
                .chainId(REGTEST_CHAIN_ID);

        return switch (type) {
            case LEGACY -> builder
                    .gasPrice(DEFAULT_GAS_PRICE)
                    .build();

            case TYPE_1 -> builder
                    .type(TransactionType.TYPE_1)
                    .gasPrice(DEFAULT_GAS_PRICE)
                    .accessList(EMPTY_ACCESS_LIST)
                    .build();

            case TYPE_2 -> builder
                    .type(TransactionType.TYPE_2)
                    .maxPriorityFeePerGas(DEFAULT_MAX_PRIORITY)
                    .maxFeePerGas(DEFAULT_MAX_FEE)
                    .accessList(EMPTY_ACCESS_LIST)
                    .build();

            case TYPE_4 -> builder
                    .type(TransactionType.TYPE_4)
                    .maxPriorityFeePerGas(DEFAULT_MAX_PRIORITY)
                    .maxFeePerGas(DEFAULT_MAX_FEE)
                    .accessList(EMPTY_ACCESS_LIST)
                    .authorizationList(fixedAuthorizationList())
                    .build();

            case TYPE_3 -> throw new IllegalArgumentException("Unsupported transaction type: " + type);
        };
    }

    private static CallArguments callArguments(TransactionType type, BigInteger nonce, BigInteger gasLimit) {
        CallArguments args = new CallArguments();
        args.setFrom("0x0000000000000000000000000000000000000001");
        args.setTo(DEFAULT_RECEIVER.toHexString());
        args.setNonce(hex(nonce));
        args.setGas(hex(gasLimit));
        args.setValue("0x0");

        switch (type) {
            case LEGACY -> args.setGasPrice(hex(DEFAULT_GAS_PRICE.asBigInteger()));
            case TYPE_1 -> {
                setTypedFields(args, "0x1");
                args.setGasPrice(hex(DEFAULT_GAS_PRICE.asBigInteger()));
            }
            case TYPE_2 -> {
                setTypedFields(args, "0x2");
                setDynamicFeeFields(args);
            }
            case TYPE_4 -> {
                setTypedFields(args, "0x4");
                setDynamicFeeFields(args);
                args.setAuthorizationList(List.of(Rskip545TestSupport.defaultType4AuthorizationEntry()));
            }
            case TYPE_3 -> throw new IllegalArgumentException("Unsupported transaction type: " + type);
        }
        return args;
    }

    private static void setTypedFields(CallArguments args, String type) {
        args.setType(type);
        args.setChainId(hex(BigInteger.valueOf(REGTEST_CHAIN_ID)));
    }

    private static void setDynamicFeeFields(CallArguments args) {
        args.setMaxPriorityFeePerGas(hex(DEFAULT_MAX_PRIORITY.asBigInteger()));
        args.setMaxFeePerGas(hex(DEFAULT_MAX_FEE.asBigInteger()));
    }

    private static Stream<Arguments> typesAndBoundaries() {
        return SUPPORTED_TYPES.stream()
                .flatMap(type -> BOUNDARIES.stream().map(value -> Arguments.of(type, value)));
    }

    private static String hex(BigInteger value) {
        return "0x" + value.toString(16);
    }

    private static List<SetCodeAuthorization> fixedAuthorizationList() {
        return AuthorizationListCodec.parseFromCallArguments(List.of(Rskip545TestSupport.defaultType4AuthorizationEntry()));
    }
}
