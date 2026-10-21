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
import org.ethereum.core.transaction.parser.RawTransactionEnvelopeParser;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RSKIP-543 reserves the RSK namespace envelope ({@code 0x02 || subtype}), but no subtype
 * transaction payload is defined. Unassigned subtypes must be rejected at raw and
 * structured ingress so they are not aliased as legacy transactions.
 */
class RskNamespaceTransactionTest {

    private static final byte REGTEST_CHAIN_ID = 33;

    @ParameterizedTest(name = "raw subtype 0x{0}")
    @ValueSource(bytes = {0x00, 0x05, 0x7f})
    void rawNamespaceEnvelope_isRejectedBeforePayloadDecoding(byte subtype) {
        byte[] malformedPayload = {TransactionType.RSK_NAMESPACE_PREFIX, subtype, 0x00};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> RawTransactionEnvelopeParser.parse(malformedPayload));

        assertEquals(TransactionTypePrefix.RSK_NAMESPACE_UNSUPPORTED_MESSAGE, ex.getMessage());
    }

    @ParameterizedTest(name = "receipt subtype 0x{0}")
    @ValueSource(bytes = {0x00, 0x05, 0x7f})
    void namespaceReceipt_isRejectedBeforePayloadDecoding(byte subtype) {
        byte[] malformedPayload = {TransactionType.RSK_NAMESPACE_PREFIX, subtype, 0x00};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TransactionReceipt(malformedPayload));

        assertEquals(TransactionTypePrefix.RSK_NAMESPACE_UNSUPPORTED_MESSAGE, ex.getMessage());
    }

    @ParameterizedTest(name = "callArguments subtype {0}")
    @ValueSource(strings = {"0x0", "0x5", "0x7f"})
    void callArgumentsNamespace_isRejectedBeforeNonceResolution(String rskSubtype) {
        CallArguments args = new CallArguments();
        args.setType("0x2");
        args.setRskSubtype(rskSubtype);
        AtomicBoolean nonceRequested = new AtomicBoolean();

        RskJsonRpcRequestException ex = assertThrows(
                RskJsonRpcRequestException.class,
                () -> Transaction.fromCallArguments(args, () -> {
                    nonceRequested.set(true);
                    return "0x0";
                }, REGTEST_CHAIN_ID));

        assertEquals(TransactionTypePrefix.RSK_NAMESPACE_UNSUPPORTED_MESSAGE, ex.getMessage());
        assertFalse(nonceRequested.get());
    }

    @ParameterizedTest(name = "builder subtype 0x{0}")
    @ValueSource(bytes = {0x00, 0x05, 0x7f})
    void builderNamespace_isRejected(byte subtype) {
        byte[] nonce = BigInteger.ZERO.toByteArray();
        Coin gasPrice = Coin.valueOf(10);
        byte[] gasLimit = BigInteger.valueOf(21_000).toByteArray();
        RskAddress receiveAddress = new RskAddress("0x1234567890123456789012345678901234567890");
        TransactionTypePrefix typePrefix = TransactionTypePrefix.rskNamespace(subtype);

        TransactionBuilder builder = Transaction.builder()
                .typePrefix(typePrefix)
                .chainId(REGTEST_CHAIN_ID)
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gasLimit)
                .receiveAddress(receiveAddress)
                .value(Coin.ZERO);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                builder::build);

        assertEquals(TransactionTypePrefix.RSK_NAMESPACE_UNSUPPORTED_MESSAGE, ex.getMessage());
    }

    @ParameterizedTest(name = "constructor subtype 0x{0}")
    @ValueSource(bytes = {0x00, 0x05, 0x7f})
    void directTransactionConstructionWithNamespace_isRejected(byte subtype) {
        byte[] nonce = new byte[]{0x00};
        Coin gasPrice = Coin.valueOf(10);
        byte[] gasLimit = BigInteger.valueOf(21_000).toByteArray();
        RskAddress receiveAddress = new RskAddress("0x1234567890123456789012345678901234567890");
        byte[] data = new byte[0];
        TransactionTypePrefix typePrefix = TransactionTypePrefix.rskNamespace(subtype);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new Transaction(
                        nonce,
                        gasPrice,
                        gasLimit,
                        receiveAddress,
                        Coin.ZERO,
                        data,
                        REGTEST_CHAIN_ID,
                        false,
                        typePrefix,
                        null,
                        null,
                        null,
                        null));

        assertEquals(TransactionTypePrefix.RSK_NAMESPACE_UNSUPPORTED_MESSAGE, ex.getMessage());
    }

    @ParameterizedTest(name = "fromRawData subtype 0x{0}")
    @ValueSource(bytes = {0x00, 0x05, 0x7f})
    void namespacePrefix_remainsRecognizable(byte subtype) {
        TransactionTypePrefix prefix =
                TransactionTypePrefix.fromRawData(new byte[]{TransactionType.RSK_NAMESPACE_PREFIX, subtype});

        assertTrue(prefix.isRskNamespace());
        assertEquals(subtype, prefix.subtype());
        assertEquals(String.format("0x02%02x", subtype & 0xFF), prefix.toFullString());
    }

    @Test
    void namespaceSubtypeOutsideEip2718Range_isRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransactionTypePrefix.rskNamespace((byte) 0x80));
    }
}
