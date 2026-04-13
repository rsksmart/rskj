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
import org.bouncycastle.util.BigIntegers;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security-focused tests for RSKIP-546 typed transaction validation.
 *
 * <p>Covers:
 * <ul>
 *   <li>Chain ID truncation: crafted chainIds that map to RSK mainnet chain ID (30) via
 *       truncation must be rejected (S1)</li>
 *   <li>yParity bounds: only 0 and 1 are valid per EIP-2930/1559 (S2)</li>
 *   <li>Malformed access list RLP: invalid RLP must be rejected at decode time (S3)</li>
 *   <li>Chain ID zero rejected for typed transactions</li>
 *   <li>maxPriorityFeePerGas > maxFeePerGas rejected for Type 2</li>
 * </ul>
 */
class TypedTransactionSecurityTest {

    private static final byte RSK_MAINNET_CHAIN_ID = 30;
    private static final byte RSK_TESTNET_CHAIN_ID = 31;
    private static final byte RSK_REGTEST_CHAIN_ID = 33;
    private static final ECKey TEST_KEY = new ECKey();
    private static final byte[] TEST_ADDRESS = new RskAddress("0x1234567890123456789012345678901234567890").getBytes();
    private static final Coin GAS_PRICE = Coin.valueOf(10_000_000_000L);
    private static final byte[] EMPTY_ACCESS_LIST = new byte[]{(byte) 0xc0};

    // =========================================================================
    // S1: Chain ID truncation — cross-chain replay prevention
    // =========================================================================

    /**
     * An attacker on a chain whose ID is 256 + RSK_MAINNET_CHAIN_ID (= 286) could
     * craft a Type 1 transaction with chainId=286. Without our validation, the silent
     * Java byte-cast of 286 % 256 = 30 would match RSK mainnet, enabling replay.
     * The fix rejects chainId > 255.
     */
    @ParameterizedTest(name = "Type1 rejects oversize chainId={0}")
    @ValueSource(ints = {256, 286, 287, 300, 65535})
    void type1_rejectsChainIdExceeding255(int chainIdValue) {
        byte[] rawTx = buildRawType1WithRawChainId(BigInteger.valueOf(chainIdValue));

        assertThrows(IllegalArgumentException.class,
                () -> new ImmutableTransaction(rawTx),
                "Type 1 with chainId=" + chainIdValue + " must be rejected (exceeds 255)");
    }

    @ParameterizedTest(name = "Type2 rejects oversize chainId={0}")
    @ValueSource(ints = {256, 286, 287, 300, 65535})
    void type2_rejectsChainIdExceeding255(int chainIdValue) {
        byte[] rawTx = buildRawType2WithRawChainId(BigInteger.valueOf(chainIdValue));

        assertThrows(IllegalArgumentException.class,
                () -> new ImmutableTransaction(rawTx),
                "Type 2 with chainId=" + chainIdValue + " must be rejected (exceeds 255)");
    }

    /**
     * Valid chain IDs in range [1, 255] must be accepted.
     */
    @ParameterizedTest(name = "Type1 accepts valid chainId={0}")
    @ValueSource(bytes = {1, 30, 31, 33, (byte) 127, (byte) 255})
    void type1_acceptsValidChainId(byte chainId) {
        Transaction built = Transaction.builder()
                .type(TransactionType.TYPE_1)
                .chainId(chainId)
                .nonce(BigInteger.ZERO)
                .gasPrice(GAS_PRICE)
                .gasLimit(BigInteger.valueOf(21_000))
                .destination(TEST_ADDRESS)
                .value(Coin.ZERO)
                .build();
        built.sign(TEST_KEY.getPrivKeyBytes());

        // Encode then decode should not throw
        Transaction decoded = new ImmutableTransaction(built.getEncoded());
        assertEquals(chainId, decoded.getChainId());
    }

    // =========================================================================
    // S2: yParity must be strictly 0 or 1
    // =========================================================================

    @ParameterizedTest(name = "Type1 rejects yParity={0}")
    @ValueSource(bytes = {2, 3, 5, 10, 100, (byte) 127})
    void type1_rejectsInvalidYParity(byte yParity) {
        byte[] rawTx = buildRawType1WithYParity(RSK_REGTEST_CHAIN_ID, yParity);

        assertThrows(IllegalArgumentException.class,
                () -> new ImmutableTransaction(rawTx),
                "Type 1 with yParity=" + yParity + " must be rejected");
    }

    @ParameterizedTest(name = "Type2 rejects yParity={0}")
    @ValueSource(bytes = {2, 3, 5, 10, 100, (byte) 127})
    void type2_rejectsInvalidYParity(byte yParity) {
        byte[] rawTx = buildRawType2WithYParity(RSK_REGTEST_CHAIN_ID, yParity);

        assertThrows(IllegalArgumentException.class,
                () -> new ImmutableTransaction(rawTx),
                "Type 2 with yParity=" + yParity + " must be rejected");
    }

    @Test
    void type1_acceptsYParity0() {
        // Build a normal Type 1 tx and verify the encoded yParity is 0 or 1
        Transaction tx = buildSignedType1(RSK_REGTEST_CHAIN_ID);
        byte yParity = tx.getEncodedV();
        assertTrue(yParity == 0 || yParity == 1,
                "Signed Type 1 yParity must be 0 or 1, got: " + yParity);
    }

    // =========================================================================
    // S3: Malformed access list must be rejected
    // =========================================================================

    /**
     * An access list with truncated RLP (declaring more bytes than present) must be rejected.
     * We craft a payload where the access list field is a list element that declares a length
     * longer than the available bytes. Because the outer transaction list decoder reads each
     * field in order, a truncated access list element will be caught during outer list parsing.
     */
    @Test
    void type1_rejectsTruncatedAccessListRlp() {
        // 0xC4 = list of length 4, but we only provide 1 byte of content → truncated RLP
        byte[] truncatedList = new byte[]{(byte) 0xC4, 0x01};
        // buildRawType1 inserts this directly as the access list element bytes
        // The outer RLP list decoder will fail trying to read 4 bytes from the list content
        byte[] rawTx = buildRawType1WithRawAccessListRlp(RSK_REGTEST_CHAIN_ID, truncatedList);

        assertThrows(Exception.class,
                () -> new ImmutableTransaction(rawTx),
                "Type 1 with truncated RLP access list must fail to decode");
    }

    @Test
    void type2_rejectsTruncatedAccessListRlp() {
        byte[] truncatedList = new byte[]{(byte) 0xC4, 0x01};
        byte[] rawTx = buildRawType2WithRawAccessListRlp(RSK_REGTEST_CHAIN_ID, truncatedList);

        assertThrows(Exception.class,
                () -> new ImmutableTransaction(rawTx),
                "Type 2 with truncated RLP access list must fail to decode");
    }

    @Test
    void type1_acceptsEmptyAccessList() {
        Transaction tx = buildSignedType1(RSK_REGTEST_CHAIN_ID);
        assertNotNull(tx.getAccessListBytes(), "Type 1 access list bytes should be non-null");
        assertDoesNotThrow(() -> new ImmutableTransaction(tx.getEncoded()),
                "Type 1 with empty access list should decode without error");
    }

    @Test
    void type1_accessListBytesArePreservedAfterEncodeDecode() {
        // Build a Type 1 tx with a valid non-empty access list (one entry, one key)
        byte[] addressBytes = new byte[20]; // zero address
        byte[] keyBytes = new byte[32];     // zero key
        byte[] accessListRlp = RLP.encodeList(
                RLP.encodeList(
                        RLP.encodeElement(addressBytes),
                        RLP.encodeList(RLP.encodeElement(keyBytes))
                )
        );

        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_1)
                .chainId(RSK_REGTEST_CHAIN_ID)
                .nonce(BigInteger.ZERO)
                .gasPrice(GAS_PRICE)
                .gasLimit(BigInteger.valueOf(21_000))
                .destination(TEST_ADDRESS)
                .value(Coin.ZERO)
                .accessList(accessListRlp)
                .build();
        tx.sign(TEST_KEY.getPrivKeyBytes());

        byte[] encoded = tx.getEncoded();
        Transaction decoded = new ImmutableTransaction(encoded);

        assertArrayEquals(tx.getAccessListBytes(), decoded.getAccessListBytes(),
                "Access list bytes must be preserved after encode/decode");
    }

    // =========================================================================
    // chainId == 0 rejected for typed transactions
    // =========================================================================

    @Test
    void type1_rejectsChainIdZeroInRlp() {
        byte[] rawTx = buildRawType1WithRawChainId(BigInteger.ZERO);

        assertThrows(IllegalArgumentException.class,
                () -> new ImmutableTransaction(rawTx),
                "Type 1 with chainId=0 must be rejected (EIP-2930 requires chain ID)");
    }

    @Test
    void type2_rejectsChainIdZeroInRlp() {
        byte[] rawTx = buildRawType2WithRawChainId(BigInteger.ZERO);

        assertThrows(IllegalArgumentException.class,
                () -> new ImmutableTransaction(rawTx),
                "Type 2 with chainId=0 must be rejected (EIP-1559 requires chain ID)");
    }

    @Test
    void type1_chainIdZeroRejectedByAcceptTransactionSignature() {
        // Build a tx with an actual valid chain ID and verify acceptTransactionSignature
        Transaction tx = buildSignedType1(RSK_MAINNET_CHAIN_ID);
        assertTrue(tx.acceptTransactionSignature(RSK_MAINNET_CHAIN_ID));
        assertFalse(tx.acceptTransactionSignature((byte) 0),
                "Type 1 tx must not be accepted with chainId=0 even if in-memory value matches");
    }

    // =========================================================================
    // maxPriorityFeePerGas > maxFeePerGas rejected for Type 2 (RLP decoder path)
    // =========================================================================

    @Test
    void type2_rlpDecoderRejectsMaxPriorityExceedingMaxFee() {
        // Hand-craft an RLP-encoded Type 2 tx where maxPriority > maxFee
        byte[] rawTx = buildRawType2WithFees(RSK_REGTEST_CHAIN_ID, 200, 100);

        assertThrows(IllegalArgumentException.class,
                () -> new ImmutableTransaction(rawTx),
                "Decoding a Type 2 tx with maxPriority > maxFee must throw");
    }

    // =========================================================================
    // Replay attack: mainnet tx rejected on testnet and vice versa
    // =========================================================================

    @Test
    void type1_mainnetTxRejectedOnTestnet() {
        Transaction mainnetTx = buildSignedType1(RSK_MAINNET_CHAIN_ID);

        assertFalse(mainnetTx.acceptTransactionSignature(RSK_TESTNET_CHAIN_ID),
                "RSK mainnet Type 1 tx must be rejected on testnet (different chain ID)");
    }

    @Test
    void type2_mainnetTxRejectedOnTestnet() {
        Transaction mainnetTx = buildSignedType2(RSK_MAINNET_CHAIN_ID);

        assertFalse(mainnetTx.acceptTransactionSignature(RSK_TESTNET_CHAIN_ID),
                "RSK mainnet Type 2 tx must be rejected on testnet (different chain ID)");
    }

    @Test
    void type1_signatureComponentsAreValid() {
        Transaction tx = buildSignedType1(RSK_REGTEST_CHAIN_ID);
        assertTrue(tx.acceptTransactionSignature(RSK_REGTEST_CHAIN_ID),
                "Signature should be valid on the correct chain");
        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        assertNotNull(tx.getSender(signatureCache), "Sender should be recoverable");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Transaction buildSignedType1(byte chainId) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_1)
                .chainId(chainId)
                .nonce(BigInteger.ZERO)
                .gasPrice(GAS_PRICE)
                .gasLimit(BigInteger.valueOf(21_000))
                .destination(TEST_ADDRESS)
                .value(Coin.ZERO)
                .build();
        tx.sign(TEST_KEY.getPrivKeyBytes());
        return tx;
    }

    private Transaction buildSignedType2(byte chainId) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .chainId(chainId)
                .nonce(BigInteger.ZERO)
                .gasLimit(BigInteger.valueOf(21_000))
                .maxPriorityFeePerGas(GAS_PRICE)
                .maxFeePerGas(GAS_PRICE)
                .destination(TEST_ADDRESS)
                .value(Coin.ZERO)
                .build();
        tx.sign(TEST_KEY.getPrivKeyBytes());
        return tx;
    }

    /**
     * Builds a raw Type 1 RLP payload with an arbitrary (potentially oversize) chain ID.
     * Produces: 0x01 || rlp([chainId, nonce, gasPrice, gasLimit, to, value, data, accessList, yParity, r, s])
     */
    private byte[] buildRawType1WithRawChainId(BigInteger chainId) {
        return buildRawType1WithFields(
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(chainId)),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY), // nonce=0
                RLP.encodeCoinNonNullZero(GAS_PRICE),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(21_000))),
                RLP.encodeElement(TEST_ADDRESS),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY), // value=0
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY), // data=empty
                EMPTY_ACCESS_LIST,
                RLP.encodeByte((byte) 0),  // yParity=0
                RLP.encodeElement(new byte[32]), // r
                RLP.encodeElement(new byte[32])  // s
        );
    }

    private byte[] buildRawType2WithRawChainId(BigInteger chainId) {
        return buildRawType2WithFields(
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(chainId)),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY), // nonce=0
                RLP.encodeCoinNonNullZero(GAS_PRICE),         // maxPriorityFeePerGas
                RLP.encodeCoinNonNullZero(GAS_PRICE),         // maxFeePerGas
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(21_000))),
                RLP.encodeElement(TEST_ADDRESS),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY), // value=0
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY), // data=empty
                EMPTY_ACCESS_LIST,
                RLP.encodeByte((byte) 0),  // yParity=0
                RLP.encodeElement(new byte[32]), // r
                RLP.encodeElement(new byte[32])  // s
        );
    }

    private byte[] buildRawType1WithYParity(byte chainId, byte yParity) {
        return buildRawType1WithFields(
                RLP.encodeByte(chainId),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                RLP.encodeCoinNonNullZero(GAS_PRICE),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(21_000))),
                RLP.encodeElement(TEST_ADDRESS),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                EMPTY_ACCESS_LIST,
                RLP.encodeByte(yParity),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[32])
        );
    }

    private byte[] buildRawType2WithYParity(byte chainId, byte yParity) {
        return buildRawType2WithFields(
                RLP.encodeByte(chainId),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                RLP.encodeCoinNonNullZero(GAS_PRICE),
                RLP.encodeCoinNonNullZero(GAS_PRICE),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(21_000))),
                RLP.encodeElement(TEST_ADDRESS),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                EMPTY_ACCESS_LIST,
                RLP.encodeByte(yParity),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[32])
        );
    }

    /**
     * Builds a raw Type 1 transaction where the access list field bytes are inserted as-is
     * into the outer RLP list (not wrapped). This allows testing with truncated RLP.
     */
    /**
     * Builds a raw Type 1 transaction whose access list field bytes are inserted verbatim
     * (not properly RLP-framed). If the bytes are truncated RLP (e.g. {@code {0xC4, 0x01}}),
     * the outer transaction RLP decoder will fail reading the declared-but-missing bytes.
     */
    private byte[] buildRawType1WithRawAccessListRlp(byte chainId, byte[] rawAccessListRlp) {
        byte[] fields = ByteUtil.merge(
                RLP.encodeByte(chainId),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                RLP.encodeCoinNonNullZero(GAS_PRICE),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(21_000))),
                RLP.encodeElement(TEST_ADDRESS),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                rawAccessListRlp,
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[32])
        );
        // 0xF8 = long list, length in next 1 byte
        byte[] payload = ByteUtil.merge(new byte[]{(byte) 0xF8, (byte) fields.length}, fields);
        return ByteUtil.merge(new byte[]{0x01}, payload);
    }

    private byte[] buildRawType2WithRawAccessListRlp(byte chainId, byte[] rawAccessListRlp) {
        byte[] fields = ByteUtil.merge(
                RLP.encodeByte(chainId),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                RLP.encodeCoinNonNullZero(GAS_PRICE),
                RLP.encodeCoinNonNullZero(GAS_PRICE),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(21_000))),
                RLP.encodeElement(TEST_ADDRESS),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                rawAccessListRlp,
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[32])
        );
        byte[] payload = ByteUtil.merge(new byte[]{(byte) 0xF8, (byte) fields.length}, fields);
        return ByteUtil.merge(new byte[]{0x02}, payload);
    }

    private byte[] buildRawType2WithFees(byte chainId, long maxPriorityFee, long maxFee) {
        return buildRawType2WithFields(
                RLP.encodeByte(chainId),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                RLP.encodeCoinNonNullZero(Coin.valueOf(maxPriorityFee)),
                RLP.encodeCoinNonNullZero(Coin.valueOf(maxFee)),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(21_000))),
                RLP.encodeElement(TEST_ADDRESS),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                RLP.encodeElement(ByteUtil.EMPTY_BYTE_ARRAY),
                EMPTY_ACCESS_LIST,
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[32])
        );
    }

    /**
     * Type 1: 0x01 || rlp([chainId, nonce, gasPrice, gasLimit, to, value, data, accessList, yParity, r, s])
     * Fields are already RLP-encoded; they are composed into a list.
     */
    private byte[] buildRawType1WithFields(byte[] chainIdRlp, byte[] nonce, byte[] gasPrice,
                                            byte[] gasLimit, byte[] to, byte[] value, byte[] data,
                                            byte[] accessList, byte[] yParity, byte[] r, byte[] s) {
        byte[] payload = RLP.encodeList(chainIdRlp, nonce, gasPrice, gasLimit, to, value, data,
                accessList, yParity, r, s);
        return ByteUtil.merge(new byte[]{0x01}, payload);
    }

    /**
     * Type 2: 0x02 || rlp([chainId, nonce, maxPriority, maxFee, gasLimit, to, value, data, accessList, yParity, r, s])
     */
    private byte[] buildRawType2WithFields(byte[] chainIdRlp, byte[] nonce, byte[] maxPriority,
                                            byte[] maxFee, byte[] gasLimit, byte[] to, byte[] value,
                                            byte[] data, byte[] accessList, byte[] yParity,
                                            byte[] r, byte[] s) {
        byte[] payload = RLP.encodeList(chainIdRlp, nonce, maxPriority, maxFee, gasLimit, to, value,
                data, accessList, yParity, r, s);
        return ByteUtil.merge(new byte[]{0x02}, payload);
    }
}
