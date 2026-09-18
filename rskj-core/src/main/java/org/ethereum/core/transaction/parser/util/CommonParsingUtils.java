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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Shared field checks for the transaction parsers. The {@code require*Bytes} / {@code require*Coin}
 * family bounds the value a field carries; the {@code requireCanonical*} family checks the bytes the
 * encoding carried, and is for typed transactions only — Type-0 is live consensus code and still
 * accepts non-minimal scalars.
 *
 * <p>Ingress rule: a received RLP encoding is validated, never rewritten, since the sender's
 * signature commits to the exact bytes. Structured ingress minimises instead, because it receives a
 * value and chooses the encoding itself.
 *
 * <p>Each {@code requireCanonical*ScalarFields} mirrors a bounds sibling field for field; a scalar
 * added to one must be added to the other.
 */
public final class CommonParsingUtils {

    private CommonParsingUtils() {}

    public static boolean exceedsDataWordLength(byte[] field) {
        return field != null && field.length > Transaction.DATAWORD_LENGTH;
    }

    public static boolean exceedsDataWordLength(Coin coin) {
        return coin != null && coin.getBytes().length > Transaction.DATAWORD_LENGTH;
    }

    public static void requireDataWordBytes(byte[] field, String message) {
        if (exceedsDataWordLength(field)) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void requireDataWordCoin(Coin coin, String message) {
        if (exceedsDataWordLength(coin)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Rejects a scalar field that was received with a leading zero byte.
     *
     * <p>{@code null} and empty are canonical: a zero-length item is how zero, or an absent field,
     * is spelled, so call sites may pass {@code getRLPData()} straight through.
     */
    public static void requireCanonicalScalar(byte[] field, String fieldLabel) {
        if (field != null && field.length > 0 && field[0] == 0) {
            throw new IllegalArgumentException(
                    fieldLabel + " must not have leading zero bytes; zero is encoded as the empty string");
        }
    }

    /**
     * Canonical y_parity parse shared by the typed envelope and the authorization tuple: empty is
     * zero, a leading zero or a payload wider than one byte is rejected, and the value must be 0 or 1.
     */
    public static byte parseCanonicalYParity(byte[] yParityData, String fieldLabel) {
        if (yParityData == null || yParityData.length == 0) {
            return 0;
        }
        requireCanonicalScalar(yParityData, fieldLabel);
        if (yParityData.length > 1) {
            throw new IllegalArgumentException(fieldLabel + " must fit in a single byte");
        }
        byte yParity = yParityData[0];
        if (yParity != 0 && yParity != 1) {
            throw new IllegalArgumentException(fieldLabel + " must be 0 or 1, got: " + (yParity & 0xFF));
        }
        return yParity;
    }

    /**
     * Bounds and encoding check for an r/s component on a canonical-RLP path. The strict
     * counterpart of {@link #requireNormalizedSignatureComponent}.
     */
    public static void requireCanonicalSignatureComponent(byte[] component, String fieldLabel) {
        requireDataWordBytes(component, fieldLabel + " is not valid");
        requireCanonicalScalar(component, fieldLabel);
    }

    /**
     * Checks the component's numeric value, ignoring leading zeros, so it accepts non-minimal
     * encodings. On a canonical-RLP path use {@link #requireCanonicalSignatureComponent} instead.
     */
    public static void requireNormalizedSignatureComponent(byte[] component, String message) {
        if (component == null) {
            return;
        }
        requireDataWordBytes(unsignedBytes(new BigInteger(1, component)), message);
    }

    /**
     * Converts a non-negative {@link BigInteger} to its minimal unsigned byte encoding.
     * Unlike {@link BigInteger#toByteArray()}, this does not add a leading sign byte when the
     * magnitude occupies a full 32-byte data word.
     */
    public static byte[] unsignedBytes(BigInteger value) {
        if (value == null || value.signum() == 0) {
            return new byte[0];
        }
        return BigIntegers.asUnsignedByteArray(value);
    }

    public static void requireLegacyScalarFields(byte[] nonce, Coin gasPrice, byte[] gasLimit, Coin value) {
        requireDataWordBytes(nonce, "Nonce is not valid");
        requireDataWordCoin(gasPrice, "Gas Price is not valid");
        requireDataWordBytes(gasLimit, "Gas Limit is not valid");
        requireDataWordCoin(value, "Value is not valid");
    }

    /**
     * Encoding counterpart of {@link #requireLegacyScalarFields}, for the field set carrying a
     * {@code gasPrice}. Named for the field set, not the type: the bounds sibling serves Type-0 and
     * Type-1, but only Type-1 checks encodings. Any field may be {@code null}.
     */
    public static void requireCanonicalGasPriceScalarFields(byte[] nonce, byte[] gasPrice, byte[] gasLimit, byte[] value) {
        requireCanonicalScalar(nonce, "Nonce");
        requireCanonicalScalar(gasPrice, "Gas Price");
        requireCanonicalScalar(gasLimit, "Gas Limit");
        requireCanonicalScalar(value, "Value");
    }

    /** Encoding counterpart of {@link #requireTypedScalarFields}, for Type-2 and Type-4. */
    public static void requireCanonicalTypedScalarFields(byte[] nonce, byte[] gasLimit, byte[] value,
                                                         byte[] maxPriorityFeePerGas, byte[] maxFeePerGas) {
        requireCanonicalScalar(nonce, "Nonce");
        requireCanonicalScalar(gasLimit, "Gas Limit");
        requireCanonicalScalar(value, "Value");
        requireCanonicalScalar(maxPriorityFeePerGas, "Max priority fee per gas");
        requireCanonicalScalar(maxFeePerGas, "Max fee per gas");
    }

    public static void requireTypedScalarFields(byte[] nonce, byte[] gasLimit, Coin value, Coin... feeFields) {
        requireDataWordBytes(nonce, "Nonce is not valid");
        requireDataWordBytes(gasLimit, "Gas Limit is not valid");
        requireDataWordCoin(value, "Value is not valid");
        for (Coin feeField : feeFields) {
            requireDataWordCoin(feeField, "Gas Price is not valid");
        }
    }

    public static void requireFieldCount(RLPList txFields, int expected, String typeName) {
        if (txFields.size() != expected) {
            throw new IllegalArgumentException(typeName + " transaction must have exactly " + expected + " elements");
        }
    }

    /**
     * Requires a nested-list field — access list, authorization list — to have arrived framed as an
     * RLP list. {@link RLPElement#getRLPRawData()} returns a list's whole frame but a byte string's
     * payload, and both then decode as RLP, so the distinction survives only here while the element
     * is still typed. Raw ingress only.
     */
    public static void requireListFramed(RLPElement field, String fieldLabel) {
        if (!(field instanceof RLPList)) {
            throw new IllegalArgumentException(fieldLabel + " must be encoded as an RLP list");
        }
    }

    /**
     * Requires every envelope field other than the given list-valued ones to be a byte string.
     * The counterpart of {@link #requireListFramed}, which covers the list-valued fields: between
     * them each field carries the framing its schema calls for.
     *
     * <p>Both directions matter because {@link RLPElement#getRLPData()} yields a list's whole frame
     * but a byte string's payload, so a list in a byte-string slot is read as the bytes of its own
     * frame and re-emitted by the encoders as a byte string.
     *
     * <p>Raw ingress only. Structured ingress builds these fields itself and has no frame to check.
     */
    public static void requireByteStringFields(RLPList txFields, int... listFieldIndices) {
        for (int i = 0; i < txFields.size(); i++) {
            if (isListField(i, listFieldIndices)) {
                continue;
            }
            if (txFields.get(i) instanceof RLPList) {
                throw new IllegalArgumentException(
                        "Transaction field at index " + i + " must be encoded as an RLP byte string");
            }
        }
    }

    private static boolean isListField(int index, int... listFieldIndices) {
        for (int listFieldIndex : listFieldIndices) {
            if (listFieldIndex == index) {
                return true;
            }
        }
        return false;
    }

    public static byte[] nullToEmpty(byte[] value) {
        return value == null ? new byte[0] : value;
    }

    public static  byte[] parseHexData(String data) {
        if (data != null) {
            String normalized = data.startsWith("0x") ? data.substring(2) : data;
            return HexUtils.stringHexToByteArray(normalized);
        }
        return new byte[0];
    }

    /**
     * Null address means contract creation transaction.
     */
    public static  RskAddress parseAddress(String address) {
        if(address != null){
            return new RskAddress(HexUtils.stringHexToByteArray(address));
        }
        return RskAddress.nullAddress();
    }

    public static  RskAddress defaultAddress(RskAddress address) {
        return address == null ? RskAddress.nullAddress() : address;
    }

    public static  BigInteger parseBigInteger(String value, Supplier<BigInteger> getDefaultValue) {
        return Optional.ofNullable(value).map(HexUtils::strHexOrStrNumberToBigInteger).orElseGet(getDefaultValue);
    }

    public static  Coin parseCoin(String value) {
        if (value == null || value.isEmpty()) {
            return Coin.ZERO;
        }
        return new Coin(HexUtils.strHexOrStrNumberToBigInteger(value));

    }

    public static  Coin defaultValue(Coin value) {
        return value == null ? Coin.ZERO : value;
    }
}
