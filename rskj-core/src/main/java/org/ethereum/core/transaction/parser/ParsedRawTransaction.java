package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;

public sealed interface ParsedRawTransaction
        permits
        ParsedType0Transaction,
        ParsedType1Transaction,
        ParsedType2Transaction,
        ParsedType2RSKTransaction {

    TransactionTypePrefix typePrefix();

    byte[] nonce();

    byte[] gasLimit();

    RskAddress receiveAddress();

    Coin value();

    byte[] data();

    SignatureState signatureState();

    default byte chainId() {
        SignatureState state = signatureState();
        if (state instanceof SignedSignature signed) {
            return signed.chainId();
        }
        if (state instanceof UnsignedSignature unsigned) {
            return unsigned.chainId() == null ? 0 : unsigned.chainId();
        }
        return 0;
    }

    default ECDSASignature signature() {
        SignatureState state = signatureState();
        return state instanceof SignedSignature signed ? signed.signature() : null;
    }
}
