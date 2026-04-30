package org.ethereum.core.transaction.parser;

public sealed interface SignatureState permits SignedSignature, UnsignedSignature {

    default boolean isSigned() {
        return this instanceof SignedSignature;
    }
}
