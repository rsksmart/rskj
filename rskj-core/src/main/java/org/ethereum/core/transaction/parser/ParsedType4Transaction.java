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
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.util.ByteUtil;

import java.util.List;
import java.util.Objects;

public record ParsedType4Transaction(
        TransactionTypePrefix typePrefix,
        byte[] nonce,
        byte[] gasLimit,
        RskAddress receiveAddress,
        Coin value,
        byte[] data,
        SignatureState signatureState,
        byte[] accessListBytes,
        Coin maxPriorityFeePerGas,
        Coin maxFeePerGas,
        List<SetCodeAuthorization> authorizationList
) implements ParsedRawTransaction {

    public ParsedType4Transaction {
        Objects.requireNonNull(typePrefix, "typePrefix");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(gasLimit, "gasLimit");
        Objects.requireNonNull(receiveAddress, "receiveAddress");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(signatureState, "signatureState");
        Objects.requireNonNull(accessListBytes, "accessListBytes");
        Objects.requireNonNull(maxPriorityFeePerGas, "maxPriorityFeePerGas");
        Objects.requireNonNull(maxFeePerGas, "maxFeePerGas");
        Objects.requireNonNull(authorizationList, "authorizationList");
        if (authorizationList.isEmpty()) {
            throw new IllegalArgumentException("Set-code transaction authorization_list must not be empty");
        }

        nonce = ByteUtil.cloneBytes(nonce);
        gasLimit = ByteUtil.cloneBytes(gasLimit);
        data = ByteUtil.cloneBytes(data);
        accessListBytes = ByteUtil.cloneBytes(accessListBytes);
        authorizationList = List.copyOf(authorizationList);
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
    public Coin maxFeePerGas() {
        return maxFeePerGas;
    }

    @Override
    public Coin maxPriorityFeePerGas() {
        return maxPriorityFeePerGas;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParsedType4Transaction that)) return false;

        return Objects.equals(typePrefix, that.typePrefix)
                && Objects.equals(receiveAddress, that.receiveAddress)
                && Objects.equals(value, that.value)
                && Objects.equals(signatureState, that.signatureState)
                && Objects.equals(maxPriorityFeePerGas, that.maxPriorityFeePerGas)
                && Objects.equals(maxFeePerGas, that.maxFeePerGas)
                && Objects.equals(authorizationList, that.authorizationList)
                && java.util.Arrays.equals(nonce, that.nonce)
                && java.util.Arrays.equals(gasLimit, that.gasLimit)
                && java.util.Arrays.equals(data, that.data)
                && java.util.Arrays.equals(accessListBytes, that.accessListBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                typePrefix,
                receiveAddress,
                value,
                signatureState,
                maxPriorityFeePerGas,
                maxFeePerGas,
                authorizationList
        );

        result = 31 * result + java.util.Arrays.hashCode(nonce);
        result = 31 * result + java.util.Arrays.hashCode(gasLimit);
        result = 31 * result + java.util.Arrays.hashCode(data);
        result = 31 * result + java.util.Arrays.hashCode(accessListBytes);

        return result;
    }

    @Override
    public String toString() {
        return "ParsedType4Transaction{" +
                "typePrefix=" + typePrefix +
                ", nonce=" + toHex(nonce) +
                ", gasLimit=" + toHex(gasLimit) +
                ", receiveAddress=" + receiveAddress +
                ", value=" + value +
                ", data=" + toHex(data) +
                ", signatureState=" + signatureState +
                ", accessListBytes=" + toHex(accessListBytes) +
                ", maxPriorityFeePerGas=" + maxPriorityFeePerGas +
                ", maxFeePerGas=" + maxFeePerGas +
                ", authorizationList=" + authorizationList +
                '}';
    }

    private static String toHex(byte[] bytes) {
        return bytes == null ? "null" : org.bouncycastle.util.encoders.Hex.toHexString(bytes);
    }
}
