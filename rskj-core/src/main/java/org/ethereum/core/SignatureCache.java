package org.ethereum.core;

import co.rsk.core.RskAddress;
import co.rsk.panic.PanicProcessor;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SignatureCache {
    private static final Logger logger = LoggerFactory.getLogger(SignatureCache.class);
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final int MAX_BROADCAST_TX_SIZE = 6000;
    private static final int TX_IN_THREE_BLOCKS = 900;

    private final Map<Transaction, RskAddress> sendersByBroadcastTx;
    private final Map<ByteArrayWrapper, RskAddress> sendersByTxOnBlock;

    public SignatureCache() {
        sendersByBroadcastTx = new MaxSizeHashMap<>(MAX_BROADCAST_TX_SIZE,true);
        sendersByTxOnBlock = new MaxSizeHashMap<>(TX_IN_THREE_BLOCKS,false);
    }

    public RskAddress getSender(Transaction transaction) {
        return transaction.getSender();
    }
}
