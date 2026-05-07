package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;

import javax.annotation.Nullable;
import java.util.Objects;

//Temporal implementation of ParsedType0Transaction, to be used until we have the full implementation of Transaction aligned with AA
public record ParsedType2Transaction(
        TransactionTypePrefix typePrefix,
        byte[] nonce,
        byte[] gasLimit,
        RskAddress receiveAddress,
        Coin value,
        byte[] data,
        SignatureState signatureState,

        byte[] accessListBytes,
        Coin maxPriorityFeePerGas,
        Coin maxFeePerGas

) implements ParsedRawTransaction {

    public ParsedType2Transaction {
        Objects.requireNonNull(typePrefix, "typePrefix");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(gasLimit, "gasLimit");
        Objects.requireNonNull(receiveAddress, "receiveAddress");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(signatureState, "signatureState cannot be null");
        Objects.requireNonNull(accessListBytes, "accessListBytes");
        Objects.requireNonNull(maxPriorityFeePerGas, "maxPriorityFeePerGas");
        Objects.requireNonNull(maxFeePerGas, "maxFeePerGas");

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
        if (!(o instanceof ParsedType2Transaction that)) return false;

        return Objects.equals(typePrefix, that.typePrefix)
                && Objects.equals(receiveAddress, that.receiveAddress)
                && Objects.equals(value, that.value)
                && Objects.equals(signatureState, that.signatureState)
                && Objects.equals(maxPriorityFeePerGas, that.maxPriorityFeePerGas)
                && Objects.equals(maxFeePerGas, that.maxFeePerGas)
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
                maxFeePerGas
        );

        result = 31 * result + java.util.Arrays.hashCode(nonce);
        result = 31 * result + java.util.Arrays.hashCode(gasLimit);
        result = 31 * result + java.util.Arrays.hashCode(data);
        result = 31 * result + java.util.Arrays.hashCode(accessListBytes);

        return result;
    }
}
