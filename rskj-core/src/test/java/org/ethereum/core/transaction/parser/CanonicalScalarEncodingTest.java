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
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Rskip546TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.core.transaction.encoder.util.TransactionEncodingUtils;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.util.CommonParsingUtils;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.ethereum.core.Rskip546TestSupport.DEFAULT_GAS_PRICE;
import static org.ethereum.core.Rskip546TestSupport.DEFAULT_MAX_FEE;
import static org.ethereum.core.Rskip546TestSupport.DEFAULT_MAX_PRIORITY;
import static org.ethereum.core.Rskip546TestSupport.DEFAULT_RECEIVER;
import static org.ethereum.core.Rskip546TestSupport.EMPTY_ACCESS_LIST;
import static org.ethereum.core.Rskip546TestSupport.REGTEST_CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.PRIVATE_KEY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Canonical scalar encodings across both transaction ingress paths: structured ingress receives a
 * value and minimises it, raw RLP ingress receives an encoding the signature commits to and rejects
 * it rather than rewriting. {@link NonceGasLimitCanonicalEncodingTest} covers nonce and gas limit on
 * the structured path; this class covers the typed envelope on the raw path, plus the Type-0
 * exemption.
 */
class CanonicalScalarEncodingTest {

    private static final byte[] NON_CANONICAL_128 = {0x00, (byte) 0x80};
    private static final byte[] CANONICAL_128 = {(byte) 0x80};

    /** Structured ingress minimises the nonce, so a non-canonical one cannot reach a signed transaction. */
    @Nested
    class StructuredIngressMinimisesScalars {

        @Test
        void resolveNonceBytesDropsLeadingZero() {
            assertArrayEquals(CANONICAL_128, TransactionInput.resolveNonceBytes(NON_CANONICAL_128));
        }

        @Test
        void resolveNonceBytesMapsAZeroByteToTheEmptyString() {
            // nonce(Coin.ZERO) yields {0x00} via toByteArray(); the parser makes it the empty string.
            assertArrayEquals(new byte[0], TransactionInput.resolveNonceBytes(new byte[]{0x00}));
            assertArrayEquals(new byte[0], TransactionInput.resolveNonceBytes(new byte[0]));
        }

        @Test
        void builderNonceWithLeadingZeroIsMinimisedIntoTheSignedTransaction() {
            Transaction nonCanonical = legacyBuilder().nonce(NON_CANONICAL_128).build();
            Transaction canonical = legacyBuilder().nonce(CANONICAL_128).build();

            assertArrayEquals(CANONICAL_128, nonCanonical.getNonce());
            assertArrayEquals(CANONICAL_128, canonical.getNonce());

            nonCanonical.sign(PRIVATE_KEY);
            canonical.sign(PRIVATE_KEY);

            // One logical transaction, one encoding, one hash.
            assertEquals(nonCanonical.getNonceAsInteger(), canonical.getNonceAsInteger());
            assertArrayEquals(canonical.getEncoded(), nonCanonical.getEncoded());
            assertEquals(canonical.getHash(), nonCanonical.getHash());
        }

        @Test
        void builderZeroNonceSpellingsAgree() {
            Transaction fromCoin = legacyBuilder().nonce(Coin.ZERO).build();
            Transaction fromBigInteger = legacyBuilder().nonce(BigInteger.ZERO).build();

            assertArrayEquals(new byte[0], fromCoin.getNonce());
            assertEquals(fromBigInteger.getHash(), fromCoin.getHash());
        }

    }

    /** Each typed parser makes its own canonical call with its own field set, so all three are covered. */
    @Nested
    class TypedRawIngressRejectsNonCanonicalScalars {

        @Test
        void type2RawNonceWithLeadingZeroIsRejected() {
            Transaction canonical = type2Builder().nonce(CANONICAL_128).build();
            canonical.sign(PRIVATE_KEY);

            // Typed envelope layout: [chainId, nonce, ...], so the nonce is field 1.
            byte[] mutated = padScalar(canonical.getEncoded(), 1, true);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Nonce must not have leading zero bytes"));
        }

        @Test
        void type2RawGasLimitWithLeadingZeroIsRejected() {
            Transaction canonical = type2Builder().nonce(CANONICAL_128).build();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = padScalar(canonical.getEncoded(), 4, true);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Gas Limit must not have leading zero bytes"));
        }

        @Test
        void type1RawGasPriceWithLeadingZeroIsRejected() {
            // Type-1 carries a gasPrice, a different field set from Type-2/4.
            // Layout: [chainId, nonce, gasPrice, ...].
            Transaction canonical = Rskip546TestSupport.unsignedType1();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = padScalar(canonical.getEncoded(), 2, true);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Gas Price must not have leading zero bytes"));
        }

        @Test
        void type1RawNonceWithLeadingZeroIsRejected() {
            Transaction canonical = Rskip546TestSupport.unsignedType1();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = padScalar(canonical.getEncoded(), 1, true);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Nonce must not have leading zero bytes"));
        }

        @Test
        void type4RawValueWithLeadingZeroIsRejected() {
            // Type-4 layout: [chainId, nonce, maxPriorityFee, maxFee, gasLimit, to, value, ...].
            Transaction canonical = Rskip545TestSupport.unsignedType4();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = padScalar(canonical.getEncoded(), 6, true);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Value must not have leading zero bytes"));
        }

        @Test
        void type4RawGasLimitWithLeadingZeroIsRejected() {
            Transaction canonical = Rskip545TestSupport.unsignedType4();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = padScalar(canonical.getEncoded(), 4, true);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Gas Limit must not have leading zero bytes"));
        }
    }

    /**
     * The ingress rule applied to the rest of the typed envelope — signature components, chainId
     * and the fee scalars — which the authorization tuple already enforced on every field.
     */
    @Nested
    class TypedRawIngressRejectsNonCanonicalEnvelopeScalars {

        @Test
        void type2RawSignatureRWithLeadingZeroIsRejected() {
            // r already fills a data word, so the lead byte is zeroed rather than prepended, which
            // would trip the length check first.
            byte[] mutated = zeroLeadByteOfSignedType2Field(10);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Signature R must not have leading zero bytes"));
        }

        @Test
        void type2RawSignatureSWithLeadingZeroIsRejected() {
            byte[] mutated = zeroLeadByteOfSignedType2Field(11);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Signature S must not have leading zero bytes"));
        }

        @Test
        void type2RawChainIdWithLeadingZeroIsRejected() {
            byte[] mutated = padSignedType2Field(0);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("chainId must not have leading zero bytes"));
        }

        @Test
        void type2RawMaxFeeWithLeadingZeroIsRejected() {
            byte[] mutated = padSignedType2Field(3);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Max fee per gas must not have leading zero bytes"));
        }

        @Test
        void unsignedEnvelopeWithNonZeroYParityIsRejected() {
            // An unsigned envelope reports no parity; the encoders emit zero for one.
            byte[] body = RLP.encodeList(
                    RLP.encodeByte(REGTEST_CHAIN_ID),
                    RLP.encodeElement(new byte[]{0x05}),
                    RLP.encodeElement(new byte[]{0x01}),
                    RLP.encodeElement(new byte[]{0x02}),
                    RLP.encodeElement(new byte[]{0x52, 0x08}),
                    RLP.encodeElement(DEFAULT_RECEIVER.getBytes()),
                    RLP.encodeElement(null),
                    RLP.encodeElement(null),
                    RLP.encodeList(),
                    RLP.encodeByte((byte) 1),
                    RLP.encodeElement(null),
                    RLP.encodeElement(null));
            byte[] raw = new byte[body.length + 1];
            raw[0] = 0x02;
            System.arraycopy(body, 0, raw, 1, body.length);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(raw));
            assertTrue(e.getMessage().contains(
                    "yParity must be 0 when the signature is absent"), e.getMessage());
        }

        @Test
        void zeroFeesEncodeAsTheEmptyStringAndSurviveRawIngress() {
            // encodeCoinNonNullZero spells zero as 0x00, which a node could not re-parse itself.
            Transaction tx = Transaction.builder()
                    .type(TransactionType.TYPE_2)
                    .nonce(BigInteger.ONE)
                    .maxPriorityFeePerGas(Coin.ZERO)
                    .maxFeePerGas(Coin.ZERO)
                    .gasLimit(BigInteger.valueOf(21_000))
                    .receiveAddress(DEFAULT_RECEIVER.getBytes())
                    .value(BigInteger.ZERO)
                    .accessList(EMPTY_ACCESS_LIST)
                    .chainId(REGTEST_CHAIN_ID)
                    .build();
            tx.sign(PRIVATE_KEY);

            RLPList fields = (RLPList) RLP.decode2(
                    Arrays.copyOfRange(tx.getEncoded(), 1, tx.getEncoded().length)).get(0);
            assertNull(fields.get(2).getRLPData(), "zero maxPriorityFeePerGas must encode as 0x80");
            assertNull(fields.get(3).getRLPData(), "zero maxFeePerGas must encode as 0x80");

            assertEquals(tx.getHash(), Transaction.fromRaw(tx.getEncoded()).getHash());
        }

        @Test
        void rpcChainIdWithLeadingZeroIsNormalised() {
            // Structured ingress normalises: 0x0021 is a legal hex quantity for 33.
            CallArguments args = type2Args("0x1", "0x5208");
            args.setChainId("0x0021");

            TransactionInput input = TransactionInput.fromCallArguments(args, null);

            assertEquals(Byte.valueOf((byte) 33), input.chainId());
        }

        @Test
        void rpcExplicitZeroChainIdIsStillAValue() {
            CallArguments args = type2Args("0x1", "0x5208");
            args.setChainId("0x0");

            assertEquals(Byte.valueOf((byte) 0), TransactionInput.fromCallArguments(args, null).chainId());
        }

        @Test
        void rpcZeroChainIdIsRejectedForTypedTransactions() {
            // The typed encoders spell zero as an empty chainId field, which the parser refuses, so
            // accepting it would build a transaction that cannot be reparsed from its own encoding.
            CallArguments args = type2Args("0x1", "0x5208");
            args.setChainId("0x0");

            assertThrows(RskJsonRpcRequestException.class,
                    () -> Transaction.fromCallArguments(args, () -> "0x1", REGTEST_CHAIN_ID));
        }

        @ParameterizedTest
        @ValueSource(strings = {"-56", "-1", "256", "1000"})
        void rpcChainIdOutsideByteRangeIsRejected(String chainId) {
            // "-56" renders as two's-complement 0xc8, which is byte-identical to "200"; the sign is
            // only visible before the value is narrowed, so that is where the range is enforced.
            CallArguments args = type2Args("0x1", "0x5208");
            args.setChainId(chainId);

            assertThrows(RskJsonRpcRequestException.class,
                    () -> Transaction.fromCallArguments(args, () -> "0x1", REGTEST_CHAIN_ID));
        }

        @Test
        void rpcChainIdAboveSignedByteRangeIsStillAccepted() {
            // 200 does not fit a signed byte, but is a legal chainId and must survive.
            CallArguments args = type2Args("0x1", "0x5208");
            args.setChainId("200");

            TransactionInput input = TransactionInput.fromCallArguments(args, null);

            assertEquals(200, Byte.toUnsignedInt(input.chainId()));
        }

        @Test
        void rpcChainIdWithNoDigitsIsRejectedAsAParameterError() {
            // "0x" carries no value; normalising it to zero would defer the failure to the encoder.
            CallArguments args = type2Args("0x1", "0x5208");
            args.setChainId("0x");

            assertThrows(RskJsonRpcRequestException.class,
                    () -> TransactionInput.fromCallArguments(args, null));
        }

        private byte[] padSignedType2Field(int index) {
            return mutateSignedType2Field(index, false);
        }

        private byte[] zeroLeadByteOfSignedType2Field(int index) {
            return mutateSignedType2Field(index, true);
        }

        private byte[] mutateSignedType2Field(int index, boolean inPlace) {
            Transaction canonical = type2Builder().nonce(CANONICAL_128).build();
            canonical.sign(PRIVATE_KEY);
            byte[] encoded = canonical.getEncoded();
            return inPlace ? zeroLeadByte(encoded, index, true) : padScalar(encoded, index, true);
        }
    }

    /**
     * Item framing, which the scalar checks cannot see: {@code 0x81 0x05} and {@code 0x05} decode to
     * the same byte, so only re-encoding the decoded structure catches the long form.
     */
    @Nested
    class TypedRawIngressRejectsNonCanonicalFraming {

        @Test
        void type2RawNonceInLongFormIsRejected() {
            Transaction canonical = type2Builder().nonce(BigInteger.valueOf(5)).build();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = longFormScalar(canonical.getEncoded(), 1);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("not canonically encoded"), e.getMessage());
        }

        @Test
        void type2RawGasLimitInLongFormIsRejected() {
            Transaction canonical = type2Builder().nonce(BigInteger.valueOf(5)).build();
            canonical.sign(PRIVATE_KEY);

            // gasLimit 21000 needs two bytes, so the long form here is a wider length prefix.
            byte[] mutated = longFormScalar(canonical.getEncoded(), 4);

            assertThrows(IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
        }

        @Test
        void type1RawNonceInLongFormIsRejected() {
            Transaction canonical = Rskip546TestSupport.unsignedType1();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = longFormScalar(canonical.getEncoded(), 1);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("not canonically encoded"), e.getMessage());
        }

        @Test
        void type4RawNonceInLongFormIsRejected() {
            Transaction canonical = Rskip545TestSupport.unsignedType4();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = longFormScalar(canonical.getEncoded(), 1);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("not canonically encoded"), e.getMessage());
        }

        @Test
        void canonicallyFramedType1AndType4StillParse() {
            Transaction type1 = Rskip546TestSupport.unsignedType1();
            type1.sign(PRIVATE_KEY);
            assertEquals(type1.getHash(), Transaction.fromRaw(type1.getEncoded()).getHash());

            Transaction type4 = Rskip545TestSupport.unsignedType4();
            type4.sign(PRIVATE_KEY);
            assertEquals(type4.getHash(), Transaction.fromRaw(type4.getEncoded()).getHash());
        }

        @Test
        void builderRejectsNonCanonicallyFramedAccessList() {
            // 0xf800 is the long form of the empty list 0xc0. The encoders emit caller-supplied
            // access-list bytes verbatim, so accepting it would build a transaction that the raw
            // parser refuses to read back.
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> type2Builder().nonce(BigInteger.ONE)
                            .accessList(new byte[]{(byte) 0xf8, 0x00}).build());

            assertTrue(e.getMessage().contains("not canonically encoded"), e.getMessage());
        }

        @Test
        void builderAcceptsTheCanonicalEmptyAccessList() {
            Transaction tx = type2Builder().nonce(BigInteger.ONE).accessList(EMPTY_ACCESS_LIST).build();
            tx.sign(PRIVATE_KEY);

            assertEquals(tx.getHash(), Transaction.fromRaw(tx.getEncoded()).getHash());
        }

        @Test
        void canonicallyFramedTypedTransactionStillParses() {
            Transaction canonical = type2Builder().nonce(BigInteger.valueOf(5)).build();
            canonical.sign(PRIVATE_KEY);

            assertEquals(canonical.getHash(), Transaction.fromRaw(canonical.getEncoded()).getHash());
        }

        /** Re-frames one scalar in long form, leaving the decoded payload identical. */
        private byte[] longFormScalar(byte[] encoded, int index) {
            byte[] body = Arrays.copyOfRange(encoded, 1, encoded.length);
            RLPList list = (RLPList) RLP.decode2(body).get(0);

            byte[][] items = new byte[list.size()][];
            for (int i = 0; i < list.size(); i++) {
                RLPElement element = list.get(i);
                if (element instanceof RLPList) {
                    items[i] = element.getRLPData();
                    continue;
                }
                byte[] data = CommonParsingUtils.nullToEmpty(element.getRLPData());
                if (i == index) {
                    byte[] framed = new byte[data.length + 1];
                    framed[0] = (byte) (0x80 + data.length);
                    System.arraycopy(data, 0, framed, 1, data.length);
                    items[i] = data.length == 1 && (data[0] & 0xFF) < 0x80
                            ? framed
                            : widenPrefix(data);
                } else {
                    items[i] = RLP.encodeElement(data);
                }
            }

            byte[] rlp = RLP.encodeList(items);
            byte[] out = new byte[rlp.length + 1];
            out[0] = encoded[0];
            System.arraycopy(rlp, 0, out, 1, rlp.length);
            return out;
        }

        /** Long-string form (0xB7 + 1) for a payload that fits the short form. */
        private byte[] widenPrefix(byte[] data) {
            byte[] framed = new byte[data.length + 2];
            framed[0] = (byte) 0xB8;
            framed[1] = (byte) data.length;
            System.arraycopy(data, 0, framed, 2, data.length);
            return framed;
        }
    }

    /**
     * The typed encoders assert the minimal-nonce invariant rather than canonicalising, because on
     * the raw path the signature commits to the bytes as received. Legacy keeps the lenient helper.
     */
    @Nested
    class TypedEncodersAssertMinimalNonce {

        @Test
        void encodeTypedNonceRejectsALeadingZero() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> TransactionEncodingUtils.encodeTypedNonce(NON_CANONICAL_128));
            assertTrue(e.getMessage().contains("minimally encoded"), e.getMessage());
        }

        @Test
        void encodeTypedNonceAcceptsMinimalAndEmptyNonces() {
            assertArrayEquals(RLP.encodeElement(CANONICAL_128),
                    TransactionEncodingUtils.encodeTypedNonce(CANONICAL_128));
            assertArrayEquals(RLP.encodeElement(null),
                    TransactionEncodingUtils.encodeTypedNonce(new byte[0]));
            assertArrayEquals(RLP.encodeElement(null),
                    TransactionEncodingUtils.encodeTypedNonce(null));
        }

        @Test
        void encodeTypedGasLimitRejectsALeadingZero() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> TransactionEncodingUtils.encodeTypedGasLimit(new byte[]{0x00, 0x52, 0x08}));
            assertTrue(e.getMessage().contains("minimally encoded"), e.getMessage());
        }

        @Test
        void encodeTypedGasLimitAcceptsMinimalAndEmpty() {
            assertArrayEquals(RLP.encodeElement(new byte[]{0x52, 0x08}),
                    TransactionEncodingUtils.encodeTypedGasLimit(new byte[]{0x52, 0x08}));
            assertArrayEquals(RLP.encodeElement(null),
                    TransactionEncodingUtils.encodeTypedGasLimit(new byte[0]));
        }

        @Test
        void legacyEncodeNonceIsUnchanged() {
            // Type-0 shares encodeNonce and must keep accepting what it always accepted.
            assertArrayEquals(RLP.encodeElement(NON_CANONICAL_128),
                    TransactionEncodingUtils.encodeNonce(NON_CANONICAL_128));
            assertArrayEquals(RLP.encodeElement(null),
                    TransactionEncodingUtils.encodeNonce(new byte[]{0x00}));
        }
    }

    /**
     * An unset gas limit resolves to zero on the builder path: every array reaches
     * {@code resolveGasLimit} through {@code ByteUtil.cloneBytes}, so it is an empty array rather
     * than null, and {@code DEFAULT_GAS_LIMIT} applies only on the {@code CallArguments} path.
     */
    @Nested
    class OmittedGasLimitResolvesToZero {

        @Test
        void transactionInputSurfacesEmptyBytesForAnUnsetGasLimit() {
            TransactionInput input = TransactionInput.fromBuilderState(
                    TransactionTypePrefix.legacy(),
                    null, DEFAULT_GAS_PRICE, null, null,
                    null, DEFAULT_RECEIVER, Coin.ZERO, null, REGTEST_CHAIN_ID, null, null);

            assertArrayEquals(new byte[0], input.gasLimit());
            assertEquals(BigInteger.ZERO, TransactionInput.resolveGasLimit(input.gasLimit()));
        }

    }

    /**
     * The access list and the authorization list carry nested lists, so their slots must arrive
     * framed as RLP lists. A byte string is a different framing of the field even when its content
     * decodes as a list, and raw ingress refuses it rather than reinterpreting it.
     */
    @Nested
    class TypedRawIngressRequiresListFramedSlots {

        @Test
        void type2AccessListFramedAsAStringIsRejected() {
            Transaction canonical = type2Builder().nonce(BigInteger.valueOf(5)).build();
            canonical.sign(PRIVATE_KEY);

            // The empty access list 0xc0, wrapped as the one-byte string 0x81 0xc0.
            byte[] mutated = replaceField(canonical.getEncoded(), 8, new byte[]{(byte) 0x81, (byte) 0xc0});

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Access list must be encoded as an RLP list"), e.getMessage());
        }

        @Test
        void type2AccessListFramedAsTheEmptyStringIsRejected() {
            Transaction canonical = type2Builder().nonce(BigInteger.valueOf(5)).build();
            canonical.sign(PRIVATE_KEY);

            // 0x80 is the empty string; the empty list is 0xc0.
            byte[] mutated = replaceField(canonical.getEncoded(), 8, new byte[]{(byte) 0x80});

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Access list must be encoded as an RLP list"), e.getMessage());
        }

        @Test
        void type2PopulatedAccessListFramedAsAStringIsRejected() {
            byte[] accessList = RLP.encodeList(RLP.encodeList(
                    RLP.encodeElement(DEFAULT_RECEIVER.getBytes()),
                    RLP.encodeList(RLP.encodeElement(new byte[32]))));
            Transaction canonical = type2Builder().nonce(BigInteger.valueOf(5))
                    .accessList(accessList).build();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = replaceField(canonical.getEncoded(), 8, RLP.encodeElement(accessList));

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Access list must be encoded as an RLP list"), e.getMessage());
        }

        @Test
        void type1AccessListFramedAsAStringIsRejected() {
            // Type-1 layout: [chainId, nonce, gasPrice, gasLimit, to, value, data, accessList, ...].
            Transaction canonical = Rskip546TestSupport.unsignedType1();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = replaceField(canonical.getEncoded(), 7, new byte[]{(byte) 0x81, (byte) 0xc0});

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Access list must be encoded as an RLP list"), e.getMessage());
        }

        @Test
        void type4AccessListFramedAsAStringIsRejected() {
            // Type-4 carries both lists; this is the access list, at index 8.
            Transaction canonical = signedType4WithOneAuthorization();

            byte[] mutated = replaceField(canonical.getEncoded(), 8, new byte[]{(byte) 0x81, (byte) 0xc0});

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Access list must be encoded as an RLP list"), e.getMessage());
        }

        @Test
        void type4AuthorizationListFramedAsAStringIsRejected() {
            Transaction canonical = signedType4WithOneAuthorization();
            byte[] encoded = canonical.getEncoded();
            RLPList fields = (RLPList) RLP.decode2(
                    Arrays.copyOfRange(encoded, 1, encoded.length)).get(0);

            byte[] mutated = replaceField(encoded, 9, RLP.encodeElement(fields.get(9).getRLPData()));

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Authorization list must be encoded as an RLP list"), e.getMessage());
        }

        @Test
        void type4AuthorizationTupleFramedAsAStringIsRejected() {
            // The outer list is well formed; the tuple inside it is the string.
            Transaction canonical = signedType4WithOneAuthorization();
            byte[] encoded = canonical.getEncoded();
            RLPList fields = (RLPList) RLP.decode2(
                    Arrays.copyOfRange(encoded, 1, encoded.length)).get(0);
            RLPList authList = RLP.decodeList(fields.get(9).getRLPData());

            byte[] wrapped = RLP.encodeList(RLP.encodeElement(authList.get(0).getRLPRawData()));
            byte[] mutated = replaceField(encoded, 9, wrapped);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Authorization list tuple must be encoded as an RLP list"),
                    e.getMessage());
        }

        @Test
        void type2AccessListEntryFramedAsAStringIsRejected() {
            byte[] entry = RLP.encodeList(
                    RLP.encodeElement(DEFAULT_RECEIVER.getBytes()),
                    RLP.encodeList(RLP.encodeElement(new byte[32])));
            Transaction canonical = type2Builder().nonce(BigInteger.valueOf(5))
                    .accessList(RLP.encodeList(entry)).build();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = replaceField(canonical.getEncoded(), 8,
                    RLP.encodeList(RLP.encodeElement(entry)));

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Access list entry at index 0 must be encoded as an RLP list"),
                    e.getMessage());
        }

        @Test
        void type2AccessListStorageKeysFramedAsAStringIsRejected() {
            byte[] storageKeys = RLP.encodeList(RLP.encodeElement(new byte[32]));
            byte[] canonicalEntry = RLP.encodeList(
                    RLP.encodeElement(DEFAULT_RECEIVER.getBytes()), storageKeys);
            Transaction canonical = type2Builder().nonce(BigInteger.valueOf(5))
                    .accessList(RLP.encodeList(canonicalEntry)).build();
            canonical.sign(PRIVATE_KEY);

            byte[] wrappedKeys = RLP.encodeList(RLP.encodeList(
                    RLP.encodeElement(DEFAULT_RECEIVER.getBytes()), RLP.encodeElement(storageKeys)));
            byte[] mutated = replaceField(canonical.getEncoded(), 8, wrappedKeys);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains("Access list storage keys at index 0 must be encoded as an RLP list"),
                    e.getMessage());
        }

        @Test
        void type2ByteStringFieldFramedAsAListIsRejected() {
            Transaction canonical = type2Builder().nonce(BigInteger.valueOf(5)).build();
            canonical.sign(PRIVATE_KEY);

            // The data field is a byte string; 0xc0 is the empty list.
            byte[] mutated = replaceField(canonical.getEncoded(), 7, new byte[]{(byte) 0xc0});

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains(
                    "Transaction field at index 7 must be encoded as an RLP byte string"), e.getMessage());
        }

        @Test
        void type2ScalarFieldFramedAsAListIsRejected() {
            Transaction canonical = type2Builder().nonce(BigInteger.valueOf(5)).build();
            canonical.sign(PRIVATE_KEY);

            byte[] mutated = replaceField(canonical.getEncoded(), 1, new byte[]{(byte) 0xc0});

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains(
                    "Transaction field at index 1 must be encoded as an RLP byte string"), e.getMessage());
        }

        @Test
        void type4ByteStringFieldFramedAsAListIsRejected() {
            Transaction canonical = signedType4WithOneAuthorization();

            byte[] mutated = replaceField(canonical.getEncoded(), 7, new byte[]{(byte) 0xc0});

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> Transaction.fromRaw(mutated));
            assertTrue(e.getMessage().contains(
                    "Transaction field at index 7 must be encoded as an RLP byte string"), e.getMessage());
        }

        @Test
        void listFramedSlotsStillParseAndKeepTheirHash() {
            Transaction type2 = type2Builder().nonce(BigInteger.valueOf(5)).build();
            type2.sign(PRIVATE_KEY);
            assertEquals(type2.getHash(), Transaction.fromRaw(type2.getEncoded()).getHash());

            Transaction type4 = signedType4WithOneAuthorization();
            assertEquals(type4.getHash(), Transaction.fromRaw(type4.getEncoded()).getHash());
        }

        private Transaction signedType4WithOneAuthorization() {
            ECKey authority = ECKey.fromPrivate(HashUtil.keccak256("authority".getBytes()));
            Transaction tx = Rskip545TestSupport.unsignedType4WithAuthorizations(
                    DEFAULT_RECEIVER,
                    BigInteger.valueOf(100_000),
                    List.of(Rskip545TestSupport.createSignedAuthorization(
                            authority, DEFAULT_RECEIVER, BigInteger.ONE, REGTEST_CHAIN_ID)));
            tx.sign(PRIVATE_KEY);
            return tx;
        }
    }

    private static org.ethereum.core.TransactionBuilder legacyBuilder() {
        return Transaction.builder()
                .gasPrice(DEFAULT_GAS_PRICE)
                .gasLimit(BigInteger.valueOf(21_000))
                .receiveAddress(DEFAULT_RECEIVER.getBytes())
                .value(BigInteger.ZERO)
                .chainId(REGTEST_CHAIN_ID);
    }

    private static org.ethereum.core.TransactionBuilder type2Builder() {
        return Transaction.builder()
                .type(TransactionType.TYPE_2)
                .maxPriorityFeePerGas(DEFAULT_MAX_PRIORITY)
                .maxFeePerGas(DEFAULT_MAX_FEE)
                .gasLimit(BigInteger.valueOf(21_000))
                .receiveAddress(DEFAULT_RECEIVER.getBytes())
                .value(BigInteger.ZERO)
                .accessList(EMPTY_ACCESS_LIST)
                .chainId(REGTEST_CHAIN_ID);
    }

    private static CallArguments type2Args(String nonce, String gas) {
        CallArguments args = new CallArguments();
        args.setType("0x2");
        args.setTo(DEFAULT_RECEIVER.toHexString());
        args.setChainId("0x21");
        args.setMaxPriorityFeePerGas("0x1");
        args.setMaxFeePerGas("0x2");
        args.setValue("0x0");
        args.setGas(gas);
        args.setNonce(nonce);
        return args;
    }

    /** Replaces one top-level field of a typed envelope with an already-framed replacement. */
    private static byte[] replaceField(byte[] encoded, int index, byte[] replacement) {
        RLPList list = (RLPList) RLP.decode2(Arrays.copyOfRange(encoded, 1, encoded.length)).get(0);

        byte[][] items = new byte[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            RLPElement element = list.get(i);
            if (i == index) {
                items[i] = replacement;
            } else if (element instanceof RLPList) {
                items[i] = element.getRLPData();
            } else {
                items[i] = RLP.encodeElement(element.getRLPData());
            }
        }

        byte[] rlp = RLP.encodeList(items);
        byte[] out = new byte[rlp.length + 1];
        out[0] = encoded[0];
        System.arraycopy(rlp, 0, out, 1, rlp.length);
        return out;
    }

    /** Replaces the leading byte of one scalar field with 0x00, keeping its width. */
    private static byte[] zeroLeadByte(byte[] encoded, int index, boolean typed) {
        return rewriteScalar(encoded, index, typed, data -> {
            byte[] mutated = Arrays.copyOf(data, data.length);
            mutated[0] = 0;
            return mutated;
        });
    }

    /** Prepends a zero byte to one scalar field, widening it. */
    private static byte[] padScalar(byte[] encoded, int index, boolean typed) {
        return rewriteScalar(encoded, index, typed, data -> {
            byte[] padded = new byte[data.length + 1];
            System.arraycopy(data, 0, padded, 1, data.length);
            return padded;
        });
    }

    private static byte[] rewriteScalar(byte[] encoded, int index, boolean typed, UnaryOperator<byte[]> mutation) {
        byte[] body = typed ? Arrays.copyOfRange(encoded, 1, encoded.length) : encoded;
        RLPList list = (RLPList) RLP.decode2(body).get(0);

        byte[][] items = new byte[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            RLPElement element = list.get(i);
            if (element instanceof RLPList) {
                items[i] = element.getRLPData();
                continue;
            }
            byte[] data = CommonParsingUtils.nullToEmpty(element.getRLPData());
            items[i] = RLP.encodeElement(i == index ? mutation.apply(data) : data);
        }

        byte[] rlp = RLP.encodeList(items);
        if (!typed) {
            return rlp;
        }

        byte[] out = new byte[rlp.length + 1];
        out[0] = encoded[0];
        System.arraycopy(rlp, 0, out, 1, rlp.length);
        return out;
    }
}
