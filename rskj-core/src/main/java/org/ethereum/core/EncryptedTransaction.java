package org.ethereum.core;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

public class EncryptedTransaction {
    private final Transaction transaction;
    private final long[][] encryptedParams;

    public EncryptedTransaction(byte[] rawData) {
        RLPList data = RLP.decodeList(rawData);

        // parse tx
        byte[] rawTx = data.get(0).getRLPRawData();
        this.transaction = new Transaction(rawTx);

        // parse encrypted params
        byte[] encryptedParamsBytes = data.get(1).getRLPRawData();
        this.encryptedParams = parseEncryptedParams(encryptedParamsBytes);
    }

    private long[][] parseEncryptedParams(byte[] params) {
        // todo(fedejinich) implement this
        return new long[][]{new long[]{0}};
    }

    public Transaction getTransaction() {
        return this.transaction;
    }
}
