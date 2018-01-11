package org.ethereum.core;

import co.rsk.config.RskSystemProperties;

/**
 * Created by ajlopez on 02/08/2017.
 */
public class ImmutableTransaction extends Transaction {
    public ImmutableTransaction(RskSystemProperties config, byte[] rawData) {
        super(config, rawData);
    }

    @Override
    public void sign(byte[] privKeyBytes) {
        throw new ImmutableTransactionException("trying to sign");
    }

    @Override
    public void setGasLimit(byte[] gasLimit) {
        throw new ImmutableTransactionException("trying to set gas limit");
    }

    public static class ImmutableTransactionException extends RuntimeException {
        public ImmutableTransactionException(String message) {
            super("Immutable transaction: " + message);
        }
    }
}