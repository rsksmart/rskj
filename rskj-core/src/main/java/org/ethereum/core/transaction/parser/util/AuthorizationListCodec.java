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
package org.ethereum.core.transaction.parser.util;

import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

/**
 * RLP codec for RSKIP-545 / EIP-7702 authorization tuples
 * {@code [chain_id, address, nonce, y_parity, r, s]}.
 */
public final class AuthorizationListCodec {

    private static final int TUPLE_FIELD_COUNT = 6;
    private static final BigInteger MAX_CHAIN_ID = BigInteger.ONE.shiftLeft(256);
    private static final BigInteger MAX_NONCE = BigInteger.ONE.shiftLeft(64);
    private static final BigInteger MAX_SIGNATURE_COMPONENT = BigInteger.ONE.shiftLeft(256);

    private AuthorizationListCodec() {}

    /**
     * Validates that the authorization list field is non-empty, well-formed RLP, and returns the raw bytes.
     */
    public static byte[] requireAuthorizationListBytes(byte[] authorizationListBytes) {
        if (authorizationListBytes == null || authorizationListBytes.length == 0) {
            throw new IllegalArgumentException("Set-code transaction authorization_list must not be empty");
        }
        try {
            RLP.decode2(authorizationListBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Authorization list contains invalid RLP encoding", e);
        }
        RLPList outer = RLP.decodeList(authorizationListBytes);
        if (outer.size() == 0) {
            throw new IllegalArgumentException("Set-code transaction authorization_list must not be empty");
        }
        return authorizationListBytes;
    }

    public static List<SetCodeAuthorization> parseFromCallArguments(List<CallArguments.AuthorizationListEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw invalidParamError("Set-code transaction authorization_list must not be empty");
        }
        List<SetCodeAuthorization> result = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            result.add(parseCallArgumentsEntry(entries.get(i), i));
        }
        return Collections.unmodifiableList(result);
    }

    public static byte[] encodeList(List<SetCodeAuthorization> authorizations) {
        if (authorizations == null || authorizations.isEmpty()) {
            throw new IllegalArgumentException("Set-code transaction authorization_list must not be empty");
        }
        byte[][] encodedTuples = new byte[authorizations.size()][];
        for (int i = 0; i < authorizations.size(); i++) {
            encodedTuples[i] = encodeTuple(authorizations.get(i));
        }
        return RLP.encodeList(encodedTuples);
    }

    public static byte[] encodeTuple(SetCodeAuthorization auth) {
        validateAuthorization(auth);
        return encodeTupleUnchecked(auth);
    }

    /** Pure encoder. The round-trip check in decodeTuple has already validated the authorization. */
    private static byte[] encodeTupleUnchecked(SetCodeAuthorization auth) {
        byte yParity = (byte) (auth.getSignature().getV() - Transaction.LOWER_REAL_V);
        return RLP.encodeList(
                RLP.encodeBigInteger(auth.getChainId()),
                RLP.encodeRskAddress(auth.getAddress()),
                RLP.encodeElement(auth.getNonceBytes()),
                RLP.encodeByte(yParity),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(auth.getSignature().getR())),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(auth.getSignature().getS()))
        );
    }

    public static List<SetCodeAuthorization> decodeListUnchecked(byte[] authorizationListBytes) {
        RLPList outer = RLP.decodeList(authorizationListBytes);
        List<SetCodeAuthorization> result = new ArrayList<>(outer.size());
        for (int i = 0; i < outer.size(); i++) {
            result.add(decodeTuple(outer.get(i)));
        }
        return Collections.unmodifiableList(result);
    }

    public static SetCodeAuthorization decodeTuple(RLPElement element) {
        byte[] tupleBytes = element.getRLPRawData();
        if (tupleBytes == null || tupleBytes.length == 0) {
            throw new IllegalArgumentException("Authorization list tuple must not be empty");
        }
        RLPList inner = RLP.decodeList(tupleBytes);
        if (inner.size() != TUPLE_FIELD_COUNT) {
            throw new IllegalArgumentException("Authorization list tuple must have " + TUPLE_FIELD_COUNT + " fields");
        }

        byte[] chainIdData = inner.get(0).getRLPData();
        CommonParsingUtils.requireDataWordBytes(chainIdData, "Authorization chain_id is not valid");
        CommonParsingUtils.requireCanonicalScalar(chainIdData, "Authorization chain_id");
        BigInteger chainId = decodeChainId(chainIdData);
        RskAddress address = decodeAddress(inner.get(1).getRLPData());
        byte[] nonce = inner.get(2).getRLPData();
        if (nonce == null) {
            nonce = new byte[0];
        } else {
            nonce = ByteUtil.cloneBytes(nonce);
        }
        CommonParsingUtils.requireDataWordBytes(nonce, "Authorization nonce is not valid");
        CommonParsingUtils.requireCanonicalScalar(nonce, "Authorization nonce");
        requireNonceInRange(decodeUnsignedBigInteger(nonce));
        byte yParity = parseTupleYParity(inner.get(3).getRLPData());
        byte[] r = CommonParsingUtils.nullToEmpty(inner.get(4).getRLPData());
        byte[] s = CommonParsingUtils.nullToEmpty(inner.get(5).getRLPData());
        CommonParsingUtils.requireDataWordBytes(r, "Authorization signature r is not valid");
        CommonParsingUtils.requireDataWordBytes(s, "Authorization signature s is not valid");
        CommonParsingUtils.requireCanonicalScalar(r, "Authorization signature r");
        CommonParsingUtils.requireCanonicalScalar(s, "Authorization signature s");
        byte v = (byte) (Transaction.LOWER_REAL_V + yParity);
        ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);

        SetCodeAuthorization auth = new SetCodeAuthorization(chainId, address, nonce, signature);
        validateAuthorization(auth);
        requireCanonicalTupleRlp(tupleBytes, auth);
        return auth;
    }

    /**
     * Requires re-encoding to reproduce the bytes received, covering the whole RLP frame: list
     * header, item prefixes and scalar minimality. Catches the prefixes per-field checks cannot see.
     */
    private static void requireCanonicalTupleRlp(byte[] tupleBytes, SetCodeAuthorization auth) {
        if (!Arrays.equals(tupleBytes, encodeTupleUnchecked(auth))) {
            throw new IllegalArgumentException("Authorization list tuple is not canonically encoded");
        }
    }

    private static SetCodeAuthorization parseCallArgumentsEntry(CallArguments.AuthorizationListEntry entry, int index) {
        if (entry.getChainId() == null) {
            throw invalidParamError("Authorization list entry missing chainId at index " + index);
        }
        if (entry.getAddress() == null) {
            throw invalidParamError("Authorization list entry missing address at index " + index);
        }
        if (entry.getNonce() == null) {
            throw invalidParamError("Authorization list entry missing nonce at index " + index);
        }
        if (entry.getYParity() == null) {
            throw invalidParamError("Authorization list entry missing yParity at index " + index);
        }
        if (entry.getR() == null) {
            throw invalidParamError("Authorization list entry missing r at index " + index);
        }
        if (entry.getS() == null) {
            throw invalidParamError("Authorization list entry missing s at index " + index);
        }

        byte[] chainIdBytes = HexUtils.strHexOrStrNumberToByteArray(entry.getChainId());
        CommonParsingUtils.requireDataWordBytes(chainIdBytes, "Authorization chain_id is not valid");
        BigInteger chainId = chainIdBytes == null || chainIdBytes.length == 0
                ? BigInteger.ZERO
                : new BigInteger(1, chainIdBytes);
        byte[] addressBytes = HexUtils.stringHexToByteArray(entry.getAddress());
        if (addressBytes == null || addressBytes.length != RskAddress.LENGTH_IN_BYTES) {
            throw invalidParamError(
                    "Authorization list entry address must be a 20-byte hex value at index " + index);
        }
        byte[] nonce = HexUtils.strHexOrStrNumberToByteArray(entry.getNonce());
        if (nonce == null) {
            nonce = new byte[0];
        }
        CommonParsingUtils.requireDataWordBytes(nonce, "Authorization nonce is not valid");
        requireNonceInRange(decodeUnsignedBigInteger(nonce));
        byte yParity = parseNormalizedYParity(HexUtils.strHexOrStrNumberToByteArray(entry.getYParity()));
        byte[] r = HexUtils.stringHexToByteArray(entry.getR());
        byte[] s = HexUtils.stringHexToByteArray(entry.getS());
        if (r == null || s == null) {
            throw invalidParamError("Authorization list entry signature r/s must be hex at index " + index);
        }
        CommonParsingUtils.requireNormalizedSignatureComponent(r, "Authorization signature r is not valid");
        CommonParsingUtils.requireNormalizedSignatureComponent(s, "Authorization signature s is not valid");
        byte v = (byte) (Transaction.LOWER_REAL_V + yParity);
        ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);

        SetCodeAuthorization auth = new SetCodeAuthorization(
                chainId,
                new RskAddress(addressBytes),
                nonce,
                signature
        );
        validateAuthorization(auth);
        requireProcessableAuthorization(auth, index);
        return auth;
    }

    /**
     * The raw decoding path deliberately admits tuples that processing will skip, so that one bad
     * tuple cannot invalidate a whole signed transaction.
     */
    private static void requireProcessableAuthorization(SetCodeAuthorization auth, int index) {
        try {
            auth.verifyNonceRange();
            auth.verifyYParity();
            auth.verifySignatureComponents();
        } catch (IllegalStateException e) {
            throw invalidParamError(
                    "Authorization list entry at index " + index + " is not processable: " + e.getMessage());
        }
    }

    private static BigInteger decodeChainId(byte[] chainIdData) {
        BigInteger chainId = chainIdData == null || chainIdData.length == 0
                ? BigInteger.ZERO
                : new BigInteger(1, chainIdData);
        if (chainId.signum() < 0 || chainId.compareTo(MAX_CHAIN_ID) >= 0) {
            throw new IllegalArgumentException("Authorization chain_id must be non-negative and less than 2^256");
        }
        return chainId;
    }

    private static RskAddress decodeAddress(byte[] addressData) {
        if (addressData == null || addressData.length != RskAddress.LENGTH_IN_BYTES) {
            throw new IllegalArgumentException("Authorization address must be exactly 20 bytes");
        }
        return new RskAddress(addressData);
    }

    private static BigInteger decodeUnsignedBigInteger(byte[] value) {
        if (value == null) {
            return BigInteger.ZERO;
        }
        return new BigInteger(1, value);
    }

    /** Decode bound {@code nonce < 2^64}; the tighter {@code < 2^64 - 1} is a processing step. */
    private static void requireNonceInRange(BigInteger nonceValue) {
        if (nonceValue.signum() < 0 || nonceValue.compareTo(MAX_NONCE) >= 0) {
            throw new IllegalArgumentException("Authorization nonce must be non-negative and less than 2^64");
        }
    }

    /**
     * Wire path: canonical and single-byte, but any value. Restricting it to {@code {0, 1}} is a
     * processing step, so it cannot use the shared {@code parseCanonicalYParity}.
     */
    private static byte parseTupleYParity(byte[] yParityData) {
        if (yParityData == null || yParityData.length == 0) {
            return 0;
        }
        CommonParsingUtils.requireCanonicalScalar(yParityData, "Authorization y_parity");
        if (yParityData.length > 1) {
            throw new IllegalArgumentException("Authorization y_parity must fit in a single byte");
        }
        return yParityData[0];
    }

    /** RPC path only: accepts the 0x00 that a JSON quantity of "0x0" decodes to. */
    private static byte parseNormalizedYParity(byte[] yParityData) {
        if (yParityData == null || yParityData.length == 0) {
            return 0;
        }
        if (yParityData.length > 1) {
            throw new IllegalArgumentException("Authorization y_parity must fit in a single byte");
        }
        return yParityData[0];
    }

    private static void validateAuthorization(SetCodeAuthorization auth) {
        if (auth.getChainId().signum() < 0 || auth.getChainId().compareTo(MAX_CHAIN_ID) >= 0) {
            throw new IllegalArgumentException("Authorization chain_id must be non-negative and less than 2^256");
        }
        requireNonceInRange(decodeUnsignedBigInteger(auth.getNonceBytes()));

        ECDSASignature signature = auth.getSignature();
        // The curve range moved to processing, so this is the only remaining lower bound;
        // asUnsignedByteArray drops the sign instead of failing, so -1 would encode as 0xff.
        if (signature.getR().signum() < 0 || signature.getS().signum() < 0) {
            throw new IllegalArgumentException("Authorization signature r and s must be non-negative");
        }
        if (signature.getR().compareTo(MAX_SIGNATURE_COMPONENT) >= 0
                || signature.getS().compareTo(MAX_SIGNATURE_COMPONENT) >= 0) {
            throw new IllegalArgumentException("Authorization signature r and s must be less than 2^256");
        }
    }
}
