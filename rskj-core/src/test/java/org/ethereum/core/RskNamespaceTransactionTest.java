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

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RSKIP-543 reserves the RSK namespace envelope, but no subtype transaction payload is defined.
 */
class RskNamespaceTransactionTest {

    private static final byte REGTEST_CHAIN_ID = 33;

    @Test
    void rawNamespaceEnvelope_isRejectedBeforePayloadDecoding() {
        byte[] malformedPayload = {TransactionType.RSK_NAMESPACE_PREFIX, 0x03, 0x00};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> RawTransactionEnvelopeParser.parse(malformedPayload));

        assertEquals(TransactionTypePrefix.RSK_NAMESPACE_UNSUPPORTED_MESSAGE, ex.getMessage());
    }

    @Test
    void namespaceReceipt_isRejectedBeforePayloadDecoding() {
        byte[] malformedPayload = {TransactionType.RSK_NAMESPACE_PREFIX, 0x03, 0x00};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TransactionReceipt(malformedPayload));

        assertEquals(TransactionTypePrefix.RSK_NAMESPACE_UNSUPPORTED_MESSAGE, ex.getMessage());
    }

    @Test
    void callArgumentsNamespace_isRejectedBeforeNonceResolution() {
        CallArguments args = new CallArguments();
        args.setType("0x2");
        args.setRskSubtype("0x3");
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

    @Test
    void builderNamespace_isRejected() {
        byte[] nonce = BigInteger.ZERO.toByteArray();
        Coin gasPrice = Coin.valueOf(10);
        byte[] gasLimit = BigInteger.valueOf(21_000).toByteArray();
        RskAddress receiveAddress = new RskAddress("0x1234567890123456789012345678901234567890");
        TransactionTypePrefix typePrefix = TransactionTypePrefix.rskNamespace((byte) 0x03);

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

    @Test
    void directTransactionConstructionWithNamespace_isRejected() {
        byte[] nonce = new byte[]{0x00};
        Coin gasPrice = Coin.valueOf(10);
        byte[] gasLimit = BigInteger.valueOf(21_000).toByteArray();
        RskAddress receiveAddress = new RskAddress("0x1234567890123456789012345678901234567890");
        byte[] data = new byte[0];
        TransactionTypePrefix typePrefix = TransactionTypePrefix.rskNamespace((byte) 0x03);

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

    @Test
    void namespacePrefix_remainsRecognizable() {
        TransactionTypePrefix prefix =
                TransactionTypePrefix.fromRawData(new byte[]{TransactionType.RSK_NAMESPACE_PREFIX, 0x03});

        assertEquals("0x0203", prefix.toFullString());
        assertEquals((byte) 0x03, prefix.subtype());
    }

    @Test
    void namespaceSubtypeOutsideEip2718Range_isRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransactionTypePrefix.rskNamespace((byte) 0x80));
    }
}
