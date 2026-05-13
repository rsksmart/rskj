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
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;

import java.util.Objects;

//Temporal implementation of ParsedType0Transaction, to be used until we have the full implementation of Transaction aligned with AA
public record ParsedType1Transaction(
        TransactionTypePrefix typePrefix,
        byte[] nonce,
        Coin gasPrice,
        byte[] gasLimit,
        RskAddress receiveAddress,
        Coin value,
        byte[] data,
        SignatureState signatureState,
        byte[] accessListBytes
) implements ParsedRawTransaction {

    public ParsedType1Transaction {
        Objects.requireNonNull(typePrefix, "typePrefix");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(gasPrice, "gasPrice");
        Objects.requireNonNull(gasLimit, "gasLimit");
        Objects.requireNonNull(receiveAddress, "receiveAddress");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(signatureState, "signatureState cannot be null");
        Objects.requireNonNull(accessListBytes, "accessListBytes");

        nonce = nonce.clone();
        gasLimit = gasLimit.clone();
        data = data.clone();
        accessListBytes = accessListBytes.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    @Override
    public byte[] gasLimit() {
        return gasLimit.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    public byte[] accessListBytes() {
        return accessListBytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParsedType1Transaction that)) return false;

        return Objects.equals(typePrefix, that.typePrefix)
                && Objects.equals(gasPrice, that.gasPrice)
                && Objects.equals(receiveAddress, that.receiveAddress)
                && Objects.equals(value, that.value)
                && Objects.equals(signatureState, that.signatureState)
                && java.util.Arrays.equals(nonce, that.nonce)
                && java.util.Arrays.equals(gasLimit, that.gasLimit)
                && java.util.Arrays.equals(data, that.data)
                && java.util.Arrays.equals(accessListBytes, that.accessListBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                typePrefix,
                gasPrice,
                receiveAddress,
                value,
                signatureState
        );

        result = 31 * result + java.util.Arrays.hashCode(nonce);
        result = 31 * result + java.util.Arrays.hashCode(gasLimit);
        result = 31 * result + java.util.Arrays.hashCode(data);
        result = 31 * result + java.util.Arrays.hashCode(accessListBytes);

        return result;
    }

    @Override
    public String toString() {
        return "ParsedType1Transaction{" +
                "typePrefix=" + typePrefix +
                ", nonce=" + toHex(nonce) +
                ", gasPrice=" + gasPrice +
                ", gasLimit=" + toHex(gasLimit) +
                ", receiveAddress=" + receiveAddress +
                ", value=" + value +
                ", data=" + toHex(data) +
                ", signatureState=" + signatureState +
                ", accessListBytes=" + toHex(accessListBytes) +
                '}';
    }

    private static String toHex(byte[] bytes) {
        return bytes == null ? "null"
                : org.bouncycastle.util.encoders.Hex.toHexString(bytes);
    }

}
