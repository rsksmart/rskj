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
import org.ethereum.config.Constants;
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
    private static final BigInteger MAX_NONCE = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final BigInteger MAX_SIGNATURE_COMPONENT = BigInteger.ONE.shiftLeft(256);
    private static final BigInteger SECP256K1N_HALF = Constants.getSECP256K1N().divide(BigInteger.valueOf(2));

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
        byte yParity = (byte) (auth.getSignature().getV() - Transaction.LOWER_REAL_V);
        return RLP.encodeList(
                RLP.encodeBigInteger(auth.getChainId()),
                RLP.encodeRskAddress(auth.getAddress()),
                RLP.encodeElement(auth.getNonce()),
                RLP.encodeByte(yParity),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(auth.getSignature().getR())),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(auth.getSignature().getS()))
        );
    }

    public static List<SetCodeAuthorization> decodeList(byte[] authorizationListBytes) {
        requireAuthorizationListBytes(authorizationListBytes);
        return decodeListUnchecked(authorizationListBytes);
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

        BigInteger chainId = decodeChainId(inner.get(0).getRLPData());
        RskAddress address = decodeAddress(inner.get(1).getRLPData());
        byte[] nonce = ByteUtil.cloneBytes(inner.get(2).getRLPData());
        validateNonceValue(decodeNonce(nonce));
        byte yParity = parseYParity(inner.get(3).getRLPData());
        byte[] r = inner.get(4).getRLPData();
        byte[] s = inner.get(5).getRLPData();
        if (r == null || s == null) {
            throw new IllegalArgumentException("Authorization list tuple signature is incomplete");
        }
        byte v = (byte) (Transaction.LOWER_REAL_V + yParity);
        ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);

        SetCodeAuthorization auth = new SetCodeAuthorization(chainId, address, nonce, signature);
        validateAuthorization(auth);
        return auth;
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

        BigInteger chainId = HexUtils.strHexOrStrNumberToBigInteger(entry.getChainId());
        byte[] addressBytes = HexUtils.stringHexToByteArray(entry.getAddress());
        if (addressBytes == null || addressBytes.length != RskAddress.LENGTH_IN_BYTES) {
            throw invalidParamError(
                    "Authorization list entry address must be a 20-byte hex value at index " + index);
        }
        byte[] nonce = HexUtils.strHexOrStrNumberToByteArray(entry.getNonce());
        if (nonce == null) {
            nonce = new byte[0];
        }
        validateNonceValue(decodeNonce(nonce));
        byte yParity = parseYParity(HexUtils.strHexOrStrNumberToByteArray(entry.getYParity()));
        byte[] r = HexUtils.stringHexToByteArray(entry.getR());
        byte[] s = HexUtils.stringHexToByteArray(entry.getS());
        if (r == null || s == null) {
            throw invalidParamError("Authorization list entry signature r/s must be hex at index " + index);
        }
        byte v = (byte) (Transaction.LOWER_REAL_V + yParity);
        ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);

        SetCodeAuthorization auth = new SetCodeAuthorization(
                chainId,
                new RskAddress(addressBytes),
                nonce,
                signature
        );
        validateAuthorization(auth);
        return auth;
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

    private static BigInteger decodeNonce(byte[] nonce) {
        if (nonce == null) {
            return BigInteger.ZERO;
        }
        return new BigInteger(1, nonce);
    }

    private static void validateNonceValue(BigInteger nonceValue) {
        if (nonceValue.signum() < 0 || nonceValue.compareTo(MAX_NONCE) >= 0) {
            throw new IllegalArgumentException("Authorization nonce must be non-negative and less than 2^64 - 1");
        }
    }

    private static byte parseYParity(byte[] yParityData) {
        if (yParityData == null || yParityData.length == 0) {
            return 0;
        }
        if (yParityData.length > 1) {
            throw new IllegalArgumentException("Authorization y_parity must fit in a single byte");
        }
        byte yParity = yParityData[0];
        if (yParity != 0 && yParity != 1) {
            throw new IllegalArgumentException("Authorization y_parity must be 0 or 1, got: " + (yParity & 0xFF));
        }
        return yParity;
    }

    private static void validateAuthorization(SetCodeAuthorization auth) {
        if (auth.getChainId().signum() < 0 || auth.getChainId().compareTo(MAX_CHAIN_ID) >= 0) {
            throw new IllegalArgumentException("Authorization chain_id must be non-negative and less than 2^256");
        }
        validateNonceValue(decodeNonce(auth.getNonce()));

        ECDSASignature signature = auth.getSignature();
        BigInteger r = signature.getR();
        BigInteger s = signature.getS();
        if (!signature.validateComponentsWithoutV()) {
            throw new IllegalArgumentException("Authorization signature components are invalid");
        }
        if (r.compareTo(MAX_SIGNATURE_COMPONENT) >= 0 || s.compareTo(MAX_SIGNATURE_COMPONENT) >= 0) {
            throw new IllegalArgumentException("Authorization signature r and s must be less than 2^256");
        }
        if (s.compareTo(SECP256K1N_HALF) >= 0) {
            throw new IllegalArgumentException("Authorization signature s must be at most secp256k1n/2");
        }
    }
}
