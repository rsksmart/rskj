package org.ethereum.core.transaction.parser;


import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionType;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;

import javax.annotation.Nullable;

public sealed interface ParsedRawTransaction
        permits
        ParsedType0Transaction,
        ParsedType1Transaction,
        ParsedType2Transaction,
        ParsedRskNamespaceType2Transaction {

    TransactionTypePrefix typePrefix();

    byte[] nonce();

    byte[] gasLimit();

    RskAddress receiveAddress();

    Coin value();

    byte[] data();

    byte chainId();

    @Nullable
    ECDSASignature signature();

    default TransactionType type() {
        return typePrefix().type();
    }

    default boolean isRskNamespaceTransaction() {
        return typePrefix().isRskNamespace();
    }

    Coin effectiveGasPrice();
}
