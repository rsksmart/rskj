package org.ethereum.core.transaction.parser;


import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;

import javax.annotation.Nullable;
import java.util.Objects;

public record ParsedRskNamespaceType2Transaction(
        TransactionTypePrefix typePrefix,
        byte[] nonce,
        Coin gasPrice,
        byte[] gasLimit,
        RskAddress receiveAddress,
        Coin value,
        byte[] data,
        byte chainId,
        @Nullable ECDSASignature signature
) implements ParsedRawTransaction {

    public ParsedRskNamespaceType2Transaction {
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
    public Coin effectiveGasPrice() {
        return gasPrice;
    }
}
