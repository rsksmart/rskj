package org.ethereum.core;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Arrays;
import java.util.List;

public class EncryptedTransaction extends Transaction {

    private long[][] encryptedParams;
    private List<ByteArrayWrapper> fetchKeys;
    public EncryptedTransaction(byte[] rawData) {
        super(rawData);

        RLPList transaction = RLP.decodeList(rawData);

        // this tx type contains two extra fields:
        // 1. encrypted params
        // 2. keys to fetch
        this.encryptedParams = parseEncryptedParams(transaction.get(6).getRLPData());
        this.fetchKeys = parseKeys(transaction.get(7).getRLPData());
    }

    private long[][] parseEncryptedParams(byte[] params) {
        // todo(fedejinich) implement this
        return new long[][]{new long[]{0}};
    }

    private List<ByteArrayWrapper> parseKeys(byte[] keys) {
        // todo(fedejinich) implement this
        return Arrays.asList(new ByteArrayWrapper(new byte[]{}));
    }
}
