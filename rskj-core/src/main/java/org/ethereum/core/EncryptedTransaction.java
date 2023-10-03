package org.ethereum.core;

import co.rsk.pcc.BFVPrecompiled;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    public static void reverse(byte[] array) {
        if (array == null) {
            return;
        }
        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    // todo(fedejinich) remove size from param
    public static long[] toLongArray(byte[] message, int size, ByteOrder order) {
        ByteBuffer buff = ByteBuffer.wrap(message).order(order);
        long[] result = new long[size];
        for (int i = 0; i < result.length/Long.BYTES; i++) {
            result[i] = buff.getLong();
        }

        return result;
    }

    private byte[] parseEncryptedParams(RLPList encryptedParamsList) {
        // todo(fedejinich) refactor this to support multiple params
        return decodeElement(encryptedParamsList);
    }

    private byte[] decodeElement(RLPList params) {
        int paramsSize = params.size();

        if(paramsSize == 0) {
            // todo(fedejinich) this is not the right way to throw exceptions
            throw new RuntimeException("this shouldn't happen");
        }

        // decodes rlp encoded data into encrypted data
//        List<Long> lo = params.getElements().stream()
//                .map(e -> ByteUtil.byteArrayToLong(e.getRLPData()))
//                .collect(Collectors.toList());

        long[] encryptedData = new long[paramsSize];
        for (int i = 0; i < paramsSize; i++) {
            byte[] data = params.get(i).getRLPData();
            long encryptedElement = ByteUtil.byteArrayToLong(data);
            encryptedData[i] = encryptedElement;
        }

        byte[] result = toByteArray(encryptedData);

        return result;
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
