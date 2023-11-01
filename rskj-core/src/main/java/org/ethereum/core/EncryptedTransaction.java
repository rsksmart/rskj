package org.ethereum.core;

import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.nio.ByteBuffer;

public class EncryptedTransaction {
    private final Transaction transaction;
    private final byte[] encryptedParams;

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

    private byte[] parseEncryptedParams(RLPList encryptedParamsList) {
        // todo(fedejinich) refactor this to support multiple params
        return decodeElement(encryptedParamsList);
    }

    private byte[] decodeElement(RLPList params) {
        long[] encryptedData = new long[params.size()];
        if(encryptedData.length == 0) {
            // todo(fedejinich) this is not the right way to throw exceptions
            throw new RuntimeException("this shouldn't happen");
        }
//        return params.getElements().stream()
//                .map(e -> ByteUtil.byteArrayToLong(e.getRLPData()))
//                .collect(Collectors.toList()).toArray();
        // decodes rlp encoded data into encrypted data
        for (int i = 0; i < params.size(); i++) {
            byte[] data = params.get(i).getRLPData();
            long encryptedElement = ByteUtil.byteArrayToLong(data);
            encryptedData[i] = encryptedElement;
        }

        return toByteArray(encryptedData);
//        return encryptedData;
    }

    private byte[] toByteArray(long[] longArray) {
        ByteBuffer buffer = ByteBuffer.allocate(8 * longArray.length);
        for (long l : longArray) {
            buffer.putLong(l);
        }
        return buffer.array();
    }

    public Transaction getTransaction() {
        return this.transaction;
    }

    public byte[] getEncryptedParams() {
        return encryptedParams;
    }
}
