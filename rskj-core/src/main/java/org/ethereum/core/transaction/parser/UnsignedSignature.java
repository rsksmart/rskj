package org.ethereum.core.transaction.parser;

public record UnsignedSignature(
        Byte chainId
) implements SignatureState {

    public boolean hasChainId() {
        return chainId != null;
    }
}
