package org.ethereum.core.transaction.encoder;

import org.ethereum.core.Transaction;

public interface TransactionEncoder {

    /**
     * Encodes the transaction as it is sent over the network (includes signature).
     */
    byte[] encodeSigned(Transaction tx);

    /**
     * Encodes the transaction payload used for signing (excludes signature).
     */
    byte[] encodeForSigning(Transaction tx);
}
