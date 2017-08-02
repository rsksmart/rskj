package org.ethereum.core;

import org.ethereum.crypto.ECKey;

/**
 * Created by ajlopez on 02/08/2017.
 */
public class ImmutableTransaction extends Transaction {
    public ImmutableTransaction(byte[] rawData) {
        super(rawData);
    }

    public void sign(byte[] privKeyBytes) {
        throw new RuntimeException("immutable transaction");
    }

    public void setGasLimit(byte[] gasLimit) {
        throw new RuntimeException("immutable transaction");
    }
}