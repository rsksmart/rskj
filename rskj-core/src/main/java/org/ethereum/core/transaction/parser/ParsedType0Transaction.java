package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;

import java.util.Arrays;
import java.util.Objects;

//Temporal implementation of ParsedType0Transaction, to be used until we have the full implementation of Transaction aligned with AA
public record ParsedType0Transaction(
        TransactionTypePrefix typePrefix,
        byte[] nonce,
        Coin gasPrice,
        byte[] gasLimit,
        RskAddress receiveAddress,
        Coin value,
        byte[] data,
        SignatureState signatureState

) implements ParsedRawTransaction {

    public ParsedType0Transaction {
        Objects.requireNonNull(typePrefix, "typePrefix cannot be null");
     //   Objects.requireNonNull(nonce, "nonce cannot be null");
        Objects.requireNonNull(gasPrice, "gasPrice cannot be null");
        Objects.requireNonNull(gasLimit, "gasLimit cannot be null");
        Objects.requireNonNull(receiveAddress, "receiveAddress cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(signatureState, "signatureState cannot be null");

        nonce = nonce == null ? null : nonce.clone();
        gasLimit = gasLimit.clone();
        data = data.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce == null ? null : nonce.clone();
    }

    @Override
    public byte[] gasLimit() {
        return gasLimit.clone();
    }

    @Override
    public byte[] data() { return data.clone(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParsedType0Transaction that)) return false;

        return Objects.equals(typePrefix, that.typePrefix)
                && Objects.equals(gasPrice, that.gasPrice)
                && Objects.equals(receiveAddress, that.receiveAddress)
                && Objects.equals(value, that.value)
                && Objects.equals(signatureState, that.signatureState)
                && Arrays.equals(nonce, that.nonce)
                && Arrays.equals(gasLimit, that.gasLimit)
                && Arrays.equals(data, that.data);
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

        result = 31 * result + Arrays.hashCode(nonce);
        result = 31 * result + Arrays.hashCode(gasLimit);
        result = 31 * result + Arrays.hashCode(data);

        return result;
    }

    @Override
    public String toString() {
        return "ParsedType0Transaction{" +
                "typePrefix=" + typePrefix +
                ", nonce=" + toHex(nonce) +
                ", gasPrice=" + gasPrice +
                ", gasLimit=" + toHex(gasLimit) +
                ", receiveAddress=" + receiveAddress +
                ", value=" + value +
                ", data=" + toHex(data) +
                ", signatureState=" + signatureState +
                '}';
    }

    private static String toHex(byte[] bytes) {
        return bytes == null ? "null"
                : org.bouncycastle.util.encoders.Hex.toHexString(bytes);
    }
}
