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
package org.ethereum.core.transaction.encoder;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLP;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared deterministic fixtures for transaction encoder tests.
 *
 * <p>Field values are mirrored independently in {@link ReferenceRlpEncoder}. Changing a fixture
 * here requires updating that mirror and deliberately regenerating
 * {@link GoldenTransactionVectors} — do not treat the pinned hex as a peer field source.
 */
public final class EncoderTestSupport {

    public static final byte CHAIN_ID = 33;

    /**
     * ChainId in the 128-255 range (0xC8). Stored as a signed byte it is negative, so this
     * value exercises the unsigned-byte encoding/parsing path that guards against cross-chain
     * replay via silent truncation (see {@code TypedTransactionCodec.parseTypedTxChainId}).
     */
    public static final byte HIGH_CHAIN_ID = (byte) 200;
    public static final byte[] NONCE = {0x01};
    public static final Coin GAS_PRICE = Coin.valueOf(1_000_000_000L);
    public static final Coin MAX_PRIORITY_FEE = Coin.valueOf(1_000_000_000L);
    public static final Coin MAX_FEE = Coin.valueOf(2_000_000_000L);
    public static final byte[] GAS_LIMIT = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(21_000));
    public static final RskAddress RECEIVER = new RskAddress("0x1234567890123456789012345678901234567890");
    public static final Coin VALUE = Coin.valueOf(1_000_000_000_000_000_000L);
    public static final byte[] EMPTY_DATA = new byte[0];
    /**
     * 56-byte data so RLP item length uses the long form ({@code 0xb8…}), which short fixtures never hit.
     */
    public static final byte[] LONG_DATA = repeat((byte) 0xaa, 56);
    public static final byte[] EMPTY_ACCESS_LIST = {(byte) 0xc0};
    /**
     * One access-list entry: address {@code 0x00…00} with a single storage key {@code 0x00…00}.
     * Pins nesting and item order for type-1 encoding.
     */
    public static final byte[] NON_EMPTY_ACCESS_LIST = RLP.encodeList(
            RLP.encodeList(
                    RLP.encodeElement(new byte[20]),
                    RLP.encodeList(RLP.encodeElement(new byte[32]))
            )
    );
    public static final RskAddress AUTH_DELEGATE =
            new RskAddress("0x0000000000000000000000000000000000000003");

    /** Deterministic private key so recovered senders and signatures are stable within a run. */
    public static final byte[] PRIVATE_KEY = deterministicPrivateKey();

    /** Fixed signature components used by the golden vectors: r=0x11*32, s=0x22*32. */
    public static final byte[] FIXED_R = repeat((byte) 0x11, 32);
    public static final byte[] FIXED_S = repeat((byte) 0x22, 32);
    /** Recovery id 28 -> typed yParity 1. */
    public static final byte FIXED_V = 28;
    /** Recovery id 27 -> typed yParity 0 (must encode as empty RLP byte {@code 0x80}, not {@code 0x00}). */
    public static final byte FIXED_V_Y_PARITY_0 = 27;

    private EncoderTestSupport() {
    }

    public static Transaction unsignedLegacy(byte chainId) {
        return build(TransactionTypePrefix.legacy(), chainId, null, null, null, null,
                RECEIVER, EMPTY_DATA);
    }

    /** Contract-creation legacy tx: null {@code to} encodes as empty RLP element. */
    public static Transaction unsignedLegacyContractCreation() {
        return build(TransactionTypePrefix.legacy(), CHAIN_ID, null, null, null, null,
                null, EMPTY_DATA);
    }

    /** Legacy tx whose data field is long enough to exercise RLP long-form item length. */
    public static Transaction unsignedLegacyWithLongData() {
        return build(TransactionTypePrefix.legacy(), CHAIN_ID, null, null, null, null,
                RECEIVER, LONG_DATA);
    }

    public static Transaction unsignedType1() {
        return build(TransactionTypePrefix.typed(TransactionType.TYPE_1), CHAIN_ID,
                EMPTY_ACCESS_LIST, null, null, null, RECEIVER, EMPTY_DATA);
    }

    public static Transaction unsignedType1(byte[] accessListBytes) {
        return build(TransactionTypePrefix.typed(TransactionType.TYPE_1), CHAIN_ID,
                accessListBytes, null, null, null, RECEIVER, EMPTY_DATA);
    }

    public static Transaction unsignedType1(byte chainId) {
        return build(TransactionTypePrefix.typed(TransactionType.TYPE_1), chainId,
                EMPTY_ACCESS_LIST, null, null, null, RECEIVER, EMPTY_DATA);
    }

    /** Type-1 with one address and one storage key in the access list. */
    public static Transaction unsignedType1WithAccessList() {
        return unsignedType1(NON_EMPTY_ACCESS_LIST);
    }

    public static Transaction unsignedType2() {
        return unsignedType2(CHAIN_ID);
    }

    public static Transaction unsignedType2(byte chainId) {
        return build(TransactionTypePrefix.typed(TransactionType.TYPE_2), chainId,
                EMPTY_ACCESS_LIST, MAX_PRIORITY_FEE, MAX_FEE, null, RECEIVER, EMPTY_DATA);
    }

    public static Transaction unsignedType4() {
        return unsignedType4(CHAIN_ID);
    }

    public static Transaction unsignedType4(byte chainId) {
        return build(TransactionTypePrefix.typed(TransactionType.TYPE_4), chainId,
                EMPTY_ACCESS_LIST, MAX_PRIORITY_FEE, MAX_FEE, List.of(deterministicAuthorization(chainId)),
                RECEIVER, EMPTY_DATA);
    }

    /**
     * RSKIP-545 authorization tuple with fixed components so the encoding is fully deterministic:
     * {@code [chainId, 0x...03, nonce=1, yParity=0, r=1, s=1]}.
     */
    public static SetCodeAuthorization deterministicAuthorization() {
        return deterministicAuthorization(CHAIN_ID);
    }

    public static SetCodeAuthorization deterministicAuthorization(byte chainId) {
        return new SetCodeAuthorization(
                BigInteger.valueOf(chainId & 0xFF),
                AUTH_DELEGATE,
                new byte[]{0x01},
                ECDSASignature.fromComponents(new byte[]{0x01}, new byte[]{0x01}, (byte) 27));
    }

    /** Installs the fixed (non-recoverable) signature used by the golden signed vectors (yParity 1). */
    public static Transaction withFixedSignature(Transaction tx) {
        return withFixedSignature(tx, FIXED_V);
    }

    public static Transaction withFixedSignature(Transaction tx, byte v) {
        tx.setSignature(ECDSASignature.fromComponents(FIXED_R, FIXED_S, v));
        return tx;
    }

    /**
     * Field-by-field comparison. {@link Transaction#equals(Object)} only compares hashes,
     * so it cannot distinguish which field diverged nor detect compensating encode/parse bugs.
     */
    public static void assertCoreFieldsMatch(Transaction original, Transaction decoded) {
        assertEquals(original.getTypePrefix(), decoded.getTypePrefix(), "type prefix");
        assertEquals(original.getChainId(), decoded.getChainId(), "chainId");
        assertArrayEquals(original.getNonce(), decoded.getNonce(), "nonce");
        assertEquals(original.getGasPrice(), decoded.getGasPrice(), "gasPrice");
        assertArrayEquals(original.getGasLimit(), decoded.getGasLimit(), "gasLimit");
        assertEquals(original.getReceiveAddress(), decoded.getReceiveAddress(), "receiveAddress");
        assertEquals(original.getValue(), decoded.getValue(), "value");
        assertArrayEquals(original.getData(), decoded.getData(), "data");
        assertEquals(original.getMaxPriorityFeePerGas(), decoded.getMaxPriorityFeePerGas(), "maxPriorityFeePerGas");
        assertEquals(original.getMaxFeePerGas(), decoded.getMaxFeePerGas(), "maxFeePerGas");
        assertArrayEquals(original.getAccessListBytes(), decoded.getAccessListBytes(), "accessListBytes");
        assertEquals(original.getAuthorizationList(), decoded.getAuthorizationList(), "authorizationList");
    }

    private static Transaction build(
            TransactionTypePrefix typePrefix,
            byte chainId,
            byte[] accessListBytes,
            Coin maxPriorityFeePerGas,
            Coin maxFeePerGas,
            List<SetCodeAuthorization> authorizationList,
            RskAddress receiveAddress,
            byte[] data
    ) {
        return new Transaction(
                NONCE,
                GAS_PRICE,
                GAS_LIMIT,
                receiveAddress,
                VALUE,
                data,
                chainId,
                false,
                typePrefix,
                accessListBytes,
                maxPriorityFeePerGas,
                maxFeePerGas,
                authorizationList);
    }

    private static byte[] deterministicPrivateKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i + 1);
        }
        return key;
    }

    private static byte[] repeat(byte value, int length) {
        byte[] result = new byte[length];
        Arrays.fill(result, value);
        return result;
    }
}
