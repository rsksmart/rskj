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
import org.ethereum.config.Constants;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.util.AuthorizationListCodec;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared fixtures for RSKIP-545 (Type 4 / EIP-7702) unit tests.
 */
public final class Rskip545TestSupport {

    public static final byte REGTEST_CHAIN_ID = 33;
    public static final RskAddress DEFAULT_RECEIVER =
            new RskAddress("0x1234567890123456789012345678901234567890");
    public static final Coin DEFAULT_MAX_PRIORITY = Coin.valueOf(10);
    public static final Coin DEFAULT_MAX_FEE = Coin.valueOf(100);
    public static final byte[] EMPTY_ACCESS_LIST = new byte[]{(byte) 0xc0};
    public static final byte[] EMPTY_AUTH_LIST = RLP.encodeList();
    public static final RskAddress DEFAULT_DELEGATE =
            new RskAddress("0x0000000000000000000000000000000000000003");

    private Rskip545TestSupport() {
    }

    /**
     * Signs an authorization tuple per RSKIP-545: {@code keccak256(0x05 || rlp([chainId, address, nonce]))}.
     */
    public static SetCodeAuthorization createSignedAuthorization(
            ECKey authorityKey,
            RskAddress delegate,
            BigInteger nonce,
            byte chainId
    ) {
        byte[] rlpEncoded = RLP.encodeList(
                RLP.encodeBigInteger(BigInteger.valueOf(chainId & 0xFF)),
                RLP.encodeElement(delegate.getBytes()),
                RLP.encodeElement(nonce.toByteArray())
        );
        byte[] payload = new byte[1 + rlpEncoded.length];
        payload[0] = 0x05;
        System.arraycopy(rlpEncoded, 0, payload, 1, rlpEncoded.length);
        ECDSASignature signature =
                ECDSASignature.fromSignature(authorityKey.sign(HashUtil.keccak256(payload)));
        return new SetCodeAuthorization(
                BigInteger.valueOf(chainId & 0xFF),
                delegate,
                nonce.toByteArray(),
                signature
        );
    }

    public static SetCodeAuthorization minimalAuthorization(byte chainId) {
        return minimalAuthorization(chainId, (byte) 0x03);
    }

    public static SetCodeAuthorization minimalAuthorization(byte chainId, byte addressSuffix) {
        byte[] delegate = new byte[20];
        delegate[19] = addressSuffix;
        ECKey key = new ECKey();
        ECKey.ECDSASignature ecSig = key.sign(new byte[32]);
        ECDSASignature signature = ECDSASignature.fromComponents(
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(ecSig.r),
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(ecSig.s),
                ecSig.v);
        return new SetCodeAuthorization(
                BigInteger.valueOf(chainId & 0xFF),
                new RskAddress(delegate),
                new byte[]{0},
                signature);
    }

    public static SetCodeAuthorization authorizationWithChainId(BigInteger chainId) {
        SetCodeAuthorization base = minimalAuthorization(REGTEST_CHAIN_ID);
        return new SetCodeAuthorization(
                chainId,
                base.getAddress(),
                base.getNonce(),
                base.getSignature());
    }

    public static byte[] defaultAuthListBytes() {
        return AuthorizationListCodec.encodeList(List.of(minimalAuthorization(REGTEST_CHAIN_ID)));
    }

    /**
     * Builds {@code 0x04 || rlp(fields)} from already RLP-encoded field bytes.
     */
    public static byte[] buildRawType4Bytes(byte[]... encodedFields) {
        return ByteUtil.merge(new byte[]{TransactionType.TYPE_4.getByteCode()}, RLP.encodeList(encodedFields));
    }

    /**
     * Builds an unsigned Type 4 transaction through the parser ({@link Transaction#fromRaw}).
     */
    public static Transaction unsignedType4() {
        return unsignedType4(DEFAULT_RECEIVER, DEFAULT_MAX_PRIORITY, DEFAULT_MAX_FEE, new byte[0], EMPTY_ACCESS_LIST);
    }

    public static Transaction unsignedType4(
            RskAddress to,
            Coin maxPriorityFeePerGas,
            Coin maxFeePerGas,
            byte[] data,
            byte[] accessListBytes
    ) {
        byte[] authList = defaultAuthListBytes();
        byte[][] fields = new byte[][]{
                RLP.encodeByte(REGTEST_CHAIN_ID),
                RLP.encodeElement(new byte[]{0x01}),
                RLP.encodeCoinNonNullZero(maxPriorityFeePerGas),
                RLP.encodeCoinNonNullZero(maxFeePerGas),
                RLP.encodeElement(BigInteger.valueOf(21_000).toByteArray()),
                RLP.encodeRskAddress(to),
                RLP.encodeBigInteger(BigInteger.ZERO),
                RLP.encodeElement(data == null ? new byte[0] : data),
                accessListBytes == null ? EMPTY_ACCESS_LIST : accessListBytes,
                authList,
                RLP.encodeElement(null),
                RLP.encodeElement(null),
                RLP.encodeElement(null)
        };
        return Transaction.fromRaw(buildRawType4Bytes(fields));
    }

    /**
     * Builds an unsigned Type 4 transaction from JSON-RPC-shaped arguments ({@link Transaction#fromCallArguments}).
     */
    public static Transaction unsignedType4FromCallArguments(byte[] data, Supplier<String> nonceSupplier) {
        return Transaction.fromCallArguments(defaultType4CallArguments(data), nonceSupplier, REGTEST_CHAIN_ID);
    }

    public static CallArguments defaultType4CallArguments(byte[] data) {
        CallArguments args = new CallArguments();
        args.setTo(DEFAULT_RECEIVER.toJsonString());
        args.setGas("0x5208");
        args.setMaxPriorityFeePerGas("0xa");
        args.setMaxFeePerGas("0x64");
        args.setValue("0x0");
        args.setNonce("0x1");
        args.setChainId("0x21");
        args.setType("0x4");
        args.setData(data == null || data.length == 0 ? "0x"
                : "0x" + org.bouncycastle.util.encoders.Hex.toHexString(data));
        args.setAuthorizationList(List.of(defaultType4AuthorizationEntry()));
        return args;
    }

    public static CallArguments.AuthorizationListEntry defaultType4AuthorizationEntry() {
        CallArguments.AuthorizationListEntry entry = new CallArguments.AuthorizationListEntry();
        entry.setChainId("0x21");
        entry.setAddress("0x0000000000000000000000000000000000000003");
        entry.setNonce("0x0");
        entry.setYParity("0x0");
        entry.setR("0x01");
        entry.setS("0x01");
        return entry;
    }

    public static RLPList buildType4RlpList(byte[] authListBytes) {
        return buildType4RlpList(DEFAULT_RECEIVER, authListBytes);
    }

    public static RLPList buildType4RlpList(RskAddress to, byte[] authListBytes) {
        return RLP.decodeList(RLP.encodeList(defaultSignedType4Fields(to, authListBytes)));
    }

    public static byte[][] defaultSignedType4Fields(RskAddress to, byte[] authListBytes) {
        return new byte[][]{
                RLP.encodeByte(REGTEST_CHAIN_ID),
                RLP.encodeElement(new byte[0]),
                RLP.encodeCoinNonNullZero(DEFAULT_MAX_PRIORITY),
                RLP.encodeCoinNonNullZero(DEFAULT_MAX_FEE),
                RLP.encodeElement(BigInteger.valueOf(21_000).toByteArray()),
                RLP.encodeRskAddress(to),
                RLP.encodeElement(new byte[0]),
                RLP.encodeElement(new byte[0]),
                EMPTY_ACCESS_LIST,
                authListBytes,
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(new byte[32]),
                RLP.encodeElement(new byte[32])
        };
    }

    public static byte[] authListWithModifiedTupleField(int fieldIndex, byte[] encodedField) {
        SetCodeAuthorization base = minimalAuthorization(REGTEST_CHAIN_ID);
        byte[] tuple = AuthorizationListCodec.encodeTuple(base);
        RLPList inner = RLP.decodeList(tuple);
        byte[][] fields = new byte[6][];
        for (int i = 0; i < 6; i++) {
            fields[i] = (i == fieldIndex) ? encodedField : reencodeAuthTupleField(inner.get(i), i);
        }
        return RLP.encodeList(RLP.encodeList(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]));
    }

    /**
     * Builds a valid authorization then replaces {@code s} with the high-{@code s} malleated counterpart
     * ({@code n - s}), which must be rejected per EIP-2 during tuple processing.
     */
    public static SetCodeAuthorization createHighSAuthorization(
            ECKey authorityKey,
            RskAddress delegate,
            BigInteger nonce,
            byte chainId
    ) {
        SetCodeAuthorization valid = createSignedAuthorization(authorityKey, delegate, nonce, chainId);
        BigInteger highS = Constants.getSECP256K1N().subtract(valid.getSignature().getS());
        ECDSASignature highSig = ECDSASignature.fromComponents(
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(valid.getSignature().getR()),
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(highS),
                valid.getSignature().getV()
        );
        return new SetCodeAuthorization(valid.getChainId(), valid.getAddress(), valid.getNonce(), highSig);
    }

    /**
     * Signs an authorization for a chain ID that is not accepted by RSKIP-545 (not 0/30/31/33).
     */
    public static SetCodeAuthorization createWrongChainIdAuthorization(
            ECKey authorityKey,
            RskAddress delegate,
            BigInteger nonce
    ) {
        return createSignedAuthorization(authorityKey, delegate, nonce, (byte) 99);
    }

    public static Transaction unsignedType4WithAuthorizations(
            RskAddress to,
            BigInteger gasLimit,
            List<SetCodeAuthorization> authorizations
    ) {
        byte[][] fields = new byte[][]{
                RLP.encodeByte(REGTEST_CHAIN_ID),
                RLP.encodeElement(BigInteger.ZERO.toByteArray()),
                RLP.encodeCoinNonNullZero(DEFAULT_MAX_PRIORITY),
                RLP.encodeCoinNonNullZero(DEFAULT_MAX_FEE),
                RLP.encodeElement(gasLimit.toByteArray()),
                RLP.encodeRskAddress(to),
                RLP.encodeBigInteger(BigInteger.ZERO),
                RLP.encodeElement(new byte[0]),
                EMPTY_ACCESS_LIST,
                AuthorizationListCodec.encodeList(authorizations),
                RLP.encodeElement(null),
                RLP.encodeElement(null),
                RLP.encodeElement(null)
        };
        return Transaction.fromRaw(buildRawType4Bytes(fields));
    }

    private static byte[] reencodeAuthTupleField(org.ethereum.util.RLPElement element, int fieldIndex) {
        byte[] data = element.getRLPData();
        return switch (fieldIndex) {
            case 0 -> data == null || data.length == 0
                    ? RLP.encodeByte((byte) 0)
                    : RLP.encodeBigInteger(new BigInteger(1, data));
            case 1 -> data == null
                    ? RLP.encodeElement(null)
                    : RLP.encodeRskAddress(new RskAddress(data));
            case 2 -> RLP.encodeElement(data == null ? new byte[0] : data);
            case 3 -> data == null || data.length == 0
                    ? RLP.encodeElement(new byte[0])
                    : (data.length == 1 ? RLP.encodeByte(data[0]) : RLP.encodeElement(data));
            case 4, 5 -> data == null ? RLP.encodeElement(null) : RLP.encodeElement(data);
            default -> throw new IllegalArgumentException("Unexpected field index: " + fieldIndex);
        };
    }
}
