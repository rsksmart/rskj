package org.ethereum.rpc;

import org.ethereum.core.Transaction;

import static org.ethereum.rpc.TypeConverter.toJsonHex;

/**
 * Created by ajlopez on 17/01/2018.
 */

public class PendingTransactionFilter extends Filter {
    class PendingTransactionFilterEvent extends FilterEvent {
        private final Transaction tx;

        PendingTransactionFilterEvent(Transaction tx) {
            this.tx = tx;
        }

        @Override
        public String getJsonEventObject() {
            return toJsonHex(tx.getHash());
        }
    }

    @Override
    public void newPendingTx(Transaction tx) {
        add(new PendingTransactionFilterEvent(tx));
    }
}
