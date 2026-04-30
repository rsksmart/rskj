package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;

import javax.annotation.Nullable;
import java.util.Objects;

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
    public byte[] data() { return data.clone(); }

}
