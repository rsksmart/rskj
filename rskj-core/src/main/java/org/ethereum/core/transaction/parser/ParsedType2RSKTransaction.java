package org.ethereum.core.transaction.parser;


import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;

import javax.annotation.Nullable;
import java.util.Objects;

//Temporal implementation of ParsedType0Transaction, to be used until we have the full implementation of Transaction aligned with AA
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
}
