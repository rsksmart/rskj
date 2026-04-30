package org.ethereum.core.transaction.parser;

import org.ethereum.crypto.signature.ECDSASignature;

import java.util.Objects;

public record SignedSignature(
        byte chainId,
        ECDSASignature signature
) implements SignatureState {

    public SignedSignature {
        Objects.requireNonNull(signature, "signature cannot be null");
    }
}
