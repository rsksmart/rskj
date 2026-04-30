package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;

import javax.annotation.Nullable;
import java.util.Objects;

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
}
