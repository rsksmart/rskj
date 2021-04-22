package co.rsk.util;

import org.ethereum.core.Transaction;
import org.ethereum.util.RLP;

import java.util.ArrayList;
import java.util.List;

public class EncodeUtils {
    public static byte[] encodeTransactionList(List<Transaction> transactions) {
        List<byte[]> encodedElements = new ArrayList<>();

        for (Transaction tx : transactions) {
            encodedElements.add(tx.getEncoded());
        }

        byte[][] encodedElementArray = encodedElements.toArray(new byte[encodedElements.size()][]);

        return RLP.encodeList(encodedElementArray);
    }
}
