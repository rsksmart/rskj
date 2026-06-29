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

public record ParsedType2RSKTransaction(
        TransactionTypePrefix typePrefix,
        byte[] nonce,
        Coin gasPrice,
        byte[] gasLimit,
        RskAddress receiveAddress,
        Coin value,
        byte[] data,
        byte chainId,
        SignatureState signatureState
) implements ParsedRawTransaction {

    public ParsedType2RSKTransaction {
        Objects.requireNonNull(typePrefix, "typePrefix");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(gasPrice, "gasPrice");
        Objects.requireNonNull(gasLimit, "gasLimit");
        Objects.requireNonNull(receiveAddress, "receiveAddress");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(signatureState, "signatureState");

        if (!typePrefix.isRskNamespace()) {
            throw new IllegalArgumentException("Expected RSK namespace type prefix");
        }

        nonce = nonce.clone();
        gasLimit = gasLimit.clone();
        data = data.clone();
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

    @Override
    public <R> R accept(ParsedRawTransactionVisitor<R> visitor) {
        return visitor.visitType2Rsk(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParsedType2RSKTransaction that)) return false;

        return chainId == that.chainId
                && Objects.equals(typePrefix, that.typePrefix)
                && Objects.equals(gasPrice, that.gasPrice)
                && Objects.equals(receiveAddress, that.receiveAddress)
                && Objects.equals(value, that.value)
                && Objects.equals(signatureState, that.signatureState)
                && java.util.Arrays.equals(nonce, that.nonce)
                && java.util.Arrays.equals(gasLimit, that.gasLimit)
                && java.util.Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                typePrefix,
                gasPrice,
                receiveAddress,
                value,
                chainId,
                signatureState
        );

        result = 31 * result + java.util.Arrays.hashCode(nonce);
        result = 31 * result + java.util.Arrays.hashCode(gasLimit);
        result = 31 * result + java.util.Arrays.hashCode(data);

        return result;
    }

    @Override
    public String toString() {
        return "ParsedType2RSKTransaction{" +
                "typePrefix=" + typePrefix +
                ", nonce=" + toHex(nonce) +
                ", gasPrice=" + gasPrice +
                ", gasLimit=" + toHex(gasLimit) +
                ", receiveAddress=" + receiveAddress +
                ", value=" + value +
                ", data=" + toHex(data) +
                ", chainId=" + chainId +
                ", signatureState=" + signatureState +
                '}';
    }

    private static String toHex(byte[] bytes) {
        return bytes == null ? "null"
                : org.bouncycastle.util.encoders.Hex.toHexString(bytes);
    }
}
