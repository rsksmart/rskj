package org.ethereum.core;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.ArrayList;
import java.util.List;

public class EncryptedTransaction {
    private final Transaction transaction;
    private final long[] encryptedParams;

    public EncryptedTransaction(byte[] rawData) {
        RLPList data = RLP.decodeList(rawData);

        if (data.size() != 2) {
            throw new RuntimeException("should contain only two elements");
        }

        // parse tx
        byte[] rawTx = data.get(0).getRLPData();
        this.transaction = new Transaction(rawTx);

        // parse encrypted params
        byte[] encryptedParams = data.get(1).getRLPData();
        RLPList encryptedParamsRlp = RLP.decodeList(encryptedParams);
        this.encryptedParams = parseEncryptedParams(encryptedParamsRlp);
    }

    private long[] parseEncryptedParams(RLPList encryptedParamsList) {
        // todo(fedejinich) refactor this to support multiple params
        return decodeElement(encryptedParamsList);
    }

    private static long[] decodeElement(RLPList params) {
        long[] encryptedData = new long[params.size()];
        if(encryptedData.length == 0) {
            // todo(fedejinich) this is not the right way to throw exceptions
            throw new RuntimeException("this shouldn't happen");
        }
        // decodes rlp encoded data into encrypted data
        for (int i = 0; i < params.size(); i++) {
            byte[] data = params.get(i).getRLPData();
            long encryptedElement = ByteUtil.byteArrayToLong(data);
            encryptedData[i] = encryptedElement;
        }

        return encryptedData;
    }

    public Transaction getTransaction() {
        return this.transaction;
    }
}
