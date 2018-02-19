package org.ethereum.core;

/**
 * Created by ajlopez on 02/08/2017.
 */
public class ImmutableTransaction extends Transaction {
    public ImmutableTransaction(byte[] rawData) {
        super(rawData);
    }

    @Override
    public void sign(byte[] privKeyBytes) {
        throw new ImmutableTransactionException(String.format("trying to sign tx=%s", this.getHash()));
    }

    @Override
    public void setGasLimit(byte[] gasLimit) {
        throw new ImmutableTransactionException(String.format("trying to set gas limit tx=%s", this.getHash()));
    }

    public static class ImmutableTransactionException extends RuntimeException {
        public ImmutableTransactionException(String message) {
            super("Immutable transaction: " + message);
        }
    }
}