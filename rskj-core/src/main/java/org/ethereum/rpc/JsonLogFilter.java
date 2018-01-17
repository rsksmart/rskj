/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.rpc;

import org.ethereum.core.*;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.LogInfo;

/**
 * Created by ajlopez on 17/01/2018.
 */
public class JsonLogFilter extends Filter {
    class LogFilterEvent extends FilterEvent {
        private final LogFilterElement el;

        LogFilterEvent(LogFilterElement el) {
            this.el = el;
        }

        @Override
        public LogFilterElement getJsonEventObject() {
            return el;
        }
    }

    private LogFilter logFilter;
    boolean onNewBlock;
    boolean onPendingTx;
    private final Blockchain blockchain;

    public JsonLogFilter(LogFilter logFilter, Blockchain blockchain) {
        this.logFilter = logFilter;
        this.blockchain = blockchain;
    }

    void onLogMatch(LogInfo logInfo, Block b, int txIndex, Transaction tx, int logIdx) {
        add(new LogFilterEvent(new LogFilterElement(logInfo, b, txIndex, tx, logIdx)));
    }

    void onTransaction(Transaction tx, Block b, int txIndex) {
        TransactionInfo txInfo = blockchain.getTransactionInfo(tx.getHash());
        TransactionReceipt receipt = txInfo.getReceipt();

        LogFilterElement[] logs = new LogFilterElement[receipt.getLogInfoList().size()];

        for (int i = 0; i < logs.length; i++) {
            LogInfo logInfo = receipt.getLogInfoList().get(i);
            if (logFilter.matchesContractAddress(logInfo.getAddress())) {
                onLogMatch(logInfo, b, txIndex, receipt.getTransaction(), i);
            }
        }
    }

    void onBlock(Block b) {
        if (logFilter.matchBloom(new Bloom(b.getLogBloom()))) {
            int txIdx = 0;

            for (Transaction tx : b.getTransactionsList()) {
                onTransaction(tx, b, txIdx);
                txIdx++;
            }
        }
    }

    @Override
    public void newBlockReceived(Block b) {
        if (onNewBlock) {
            onBlock(b);
        }
    }

    @Override
    public void newPendingTx(Transaction tx) {
        //empty method
    }
}
