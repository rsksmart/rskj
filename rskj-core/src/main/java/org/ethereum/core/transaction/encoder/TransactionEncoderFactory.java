package org.ethereum.core.transaction.encoder;

import org.ethereum.core.Transaction;

public class TransactionEncoderFactory {

    private static final TransactionEncoder TYPE_0_ENCODER = new Type0TransactionEncoder();
    private static final TransactionEncoder TYPE_1_ENCODER = new Type1TransactionEncoder();
    private static final TransactionEncoder TYPE_2_ENCODER = new Type2TransactionEncoder();
    private static final TransactionEncoder TYPE_2_RSK_ENCODER = new Type2RSKTransactionEncoder();

    private TransactionEncoderFactory() {}

    public static TransactionEncoder getEncoder(Transaction transaction) {
        return switch (transaction.getType()) {
            case TYPE_1 -> TYPE_1_ENCODER;
            case TYPE_2 -> (transaction.isRskNamespaceTransaction()) ? TYPE_2_RSK_ENCODER : TYPE_2_ENCODER;
            default -> TYPE_0_ENCODER;
        };
    }
}
