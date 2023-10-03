package org.ethereum.core;

import org.ethereum.db.ByteArrayWrapper;

import java.util.List;

public class EncryptedTransaction extends Transaction {

    private long[][] transcipherList;
    private List<ByteArrayWrapper> keysToFetch;
    public EncryptedTransaction(byte[] rawData) {
        super(rawData);
        // add logic to set params to transcipher
        // add logic to set keys to fetch encrypted data from storage
    }
}
