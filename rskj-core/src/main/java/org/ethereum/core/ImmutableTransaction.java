package org.ethereum.core;

import org.ethereum.util.RLPList;

/**
 * Created by ajlopez on 02/08/2017.
 */
public class ImmutableTransaction extends Transaction {
    public ImmutableTransaction(byte[] rawData) {
        super(rawData);
    }
    public ImmutableTransaction(byte[] rawDataWithoutSig, byte[] encodedRSV){
        super(rawDataWithoutSig, encodedRSV);
    }
    
    @Override
    public void sign(byte[] privKeyBytes) {
        throw new ImmutableTransactionException(String.format("trying to sign tx=%s", this.getHash()));
    }

    public static class ImmutableTransactionException extends RuntimeException {
        public ImmutableTransactionException(String message) {
            super("Immutable transaction: " + message);
        }
    }
}