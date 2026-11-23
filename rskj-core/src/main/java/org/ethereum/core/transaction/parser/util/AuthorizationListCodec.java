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
import org.bouncycastle.util.encoders.DecoderException;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
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

    /** Raw-ingress check and decode of the authorization list envelope field: list framing, non-empty, then each tuple. */
    public static List<SetCodeAuthorization> requireRawAuthorizationList(RLPElement authorizationListField) {
        CommonParsingUtils.requireListFramed(authorizationListField, "Authorization list");
        return decodeListUnchecked(requireAuthorizationListBytes(authorizationListField.getRLPRawData()));
    }

    public static List<SetCodeAuthorization> parseFromCallArguments(List<CallArguments.AuthorizationListEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw invalidParamError("Set-code transaction authorization_list must not be empty");
        }
        List<SetCodeAuthorization> result = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            try {
                result.add(parseCallArgumentsEntry(entries.get(i), i));
            } catch (IllegalArgumentException e) {
                throw invalidParamError(e.getMessage() + " at index " + i, e);
            }
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

    /** Pure encoder: {@link SetCodeAuthorization} enforces its own decode bounds on construction. */
    public static byte[] encodeTuple(SetCodeAuthorization auth) {
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
        CommonParsingUtils.requireListFramed(element, "Authorization list tuple");
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
        byte[] nonce = decodeAndValidateNonce(inner.get(2));
        byte yParity = decodeAndValidateYParity(inner.get(3).getRLPData());
        byte[] r = decodeAndValidateSignatureComponent(inner.get(4), "r");
        byte[] s = decodeAndValidateSignatureComponent(inner.get(5), "s");
        byte v = (byte) (Transaction.LOWER_REAL_V + yParity);
        ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);

        SetCodeAuthorization auth = new SetCodeAuthorization(chainId, address, nonce, signature);
        requireCanonicalTupleRlp(tupleBytes, auth);
        return auth;
    }

    /**
     * Requires re-encoding to reproduce the bytes received, covering the whole RLP frame: list
     * header, item prefixes and scalar minimality. Catches the prefixes per-field checks cannot see.
     */
    private static void requireCanonicalTupleRlp(byte[] tupleBytes, SetCodeAuthorization auth) {
        if (!Arrays.equals(tupleBytes, encodeTuple(auth))) {
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

        byte[] chainIdBytes = decodeQuantity(entry.getChainId(), "chainId", index);
        CommonParsingUtils.requireDataWordBytes(chainIdBytes, "Authorization chain_id is not valid");
        BigInteger chainId = chainIdBytes == null || chainIdBytes.length == 0
                ? BigInteger.ZERO
                : new BigInteger(1, chainIdBytes);
        String addressError = "Authorization list entry address must be a 20-byte hex value at index " + index;
        byte[] addressBytes = CommonParsingUtils.decodeHex(entry.getAddress(), addressError);
        if (addressBytes.length != RskAddress.LENGTH_IN_BYTES) {
            throw invalidParamError(addressError);
        }
        byte[] nonce = decodeQuantity(entry.getNonce(), "nonce", index);
        if (nonce == null) {
            nonce = new byte[0];
        }
        CommonParsingUtils.requireDataWordBytes(nonce, "Authorization nonce is not valid");
        byte yParity = parseNormalizedYParity(decodeQuantity(entry.getYParity(), "yParity", index));
        byte[] r = CommonParsingUtils.decodeHex(entry.getR(), "Authorization list entry r must be hex at index " + index);
        byte[] s = CommonParsingUtils.decodeHex(entry.getS(), "Authorization list entry s must be hex at index " + index);
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
        requireProcessableAuthorization(auth, index);
        return auth;
    }

    /** Decodes an RPC hex or decimal quantity, reporting either malformed form as an invalid parameter. */
    private static byte[] decodeQuantity(String value, String fieldName, int index) {
        byte[] quantity;
        try {
            quantity = HexUtils.strHexOrStrNumberToByteArray(value);
        } catch (DecoderException | RskJsonRpcRequestException e) {
            throw invalidParamError(
                    "Authorization list entry " + fieldName + " must be a hex or decimal quantity at index " + index);
        }
        if (value.startsWith("-")) {
            throw invalidParamError("Authorization list entry " + fieldName + " must not be negative at index " + index);
        }
        return quantity;
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
        return chainIdData == null || chainIdData.length == 0 ? BigInteger.ZERO : new BigInteger(1, chainIdData);
    }

    private static RskAddress decodeAddress(byte[] addressData) {
        if (addressData == null || addressData.length != RskAddress.LENGTH_IN_BYTES) {
            throw new IllegalArgumentException("Authorization address must be exactly 20 bytes");
        }
        return new RskAddress(addressData);
    }

    private static byte[] decodeAndValidateNonce(RLPElement field) {
        byte[] rawNonce = field.getRLPData();
        byte[] nonce = rawNonce == null ? new byte[0] : ByteUtil.cloneBytes(rawNonce);
        CommonParsingUtils.requireDataWordBytes(nonce, "Authorization nonce is not valid");
        CommonParsingUtils.requireCanonicalScalar(nonce, "Authorization nonce");
        return nonce;
    }

    /** Decode bounds for r/s; the curve range {@code [1, secp256k1n)} is a processing step. */
    private static byte[] decodeAndValidateSignatureComponent(RLPElement field, String fieldName) {
        byte[] component = CommonParsingUtils.nullToEmpty(field.getRLPData());
        CommonParsingUtils.requireCanonicalSignatureComponent(component, "Authorization signature " + fieldName);
        return component;
    }

    /**
     * Wire path: canonical and single-byte, but any value, since restricting it to {@code {0, 1}}
     * is a processing step. That is why it cannot use the shared {@code parseCanonicalYParity}.
     */
    private static byte decodeAndValidateYParity(byte[] yParityData) {
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
}
