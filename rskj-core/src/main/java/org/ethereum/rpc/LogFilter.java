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

import co.rsk.core.RskAddress;
import org.ethereum.core.*;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.LogInfo;

import java.util.Collection;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

/**
 * Created by ajlopez on 17/01/2018.
 */
public class LogFilter extends Filter {
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

    private AddressesTopicsFilter addressesTopicsFilter;
    private boolean fromLatestBlock;
    private boolean toLatestBlock;
    private final Blockchain blockchain;

    public LogFilter(AddressesTopicsFilter addressesTopicsFilter, Blockchain blockchain, boolean fromLatestBlock, boolean toLatestBlock) {
        this.addressesTopicsFilter = addressesTopicsFilter;
        this.blockchain = blockchain;
        this.fromLatestBlock = fromLatestBlock;
        this.toLatestBlock = toLatestBlock;
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

            if (addressesTopicsFilter.matchesExactly(logInfo)) {
                onLogMatch(logInfo, b, txIndex, receipt.getTransaction(), i);
            }
        }
    }

    void onBlock(Block b) {
        if (addressesTopicsFilter.matchBloom(new Bloom(b.getLogBloom()))) {
            int txIdx = 0;

            for (Transaction tx : b.getTransactionsList()) {
                onTransaction(tx, b, txIdx);
                txIdx++;
            }
        }
    }

    @Override
    public void newBlockReceived(Block b) {
        if (this.fromLatestBlock) {
            this.clearEvents();
            onBlock(b);
        }
        else if (this.toLatestBlock) {
            onBlock(b);
        }
    }

    @Override
    public void newPendingTx(Transaction tx) {
        //empty method
    }

    public static LogFilter fromFilterRequest(Web3.FilterRequest fr, Blockchain blockchain) throws Exception {
        RskAddress[] addresses;

        // TODO get array of topics, with topics, and array of topics inside (the OR operation over topics)
        Topic[] topics = null;

        if (fr.address instanceof String) {
            addresses = new RskAddress[] { new RskAddress(stringHexToByteArray((String) fr.address)) };
        } else if (fr.address instanceof Collection<?>) {
            Collection<?> iterable = (Collection<?>)fr.address;

            addresses = iterable.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(TypeConverter::stringHexToByteArray)
                    .map(RskAddress::new)
                    .toArray(RskAddress[]::new);
        }
        else {
            addresses = new RskAddress[0];
        }

        if (fr.topics != null) {
            for (Object topic : fr.topics) {
                if (topic == null) {
                    topics = null;
                } else if (topic instanceof String) {
                    topics = new Topic[] { new Topic((String) topic) };
                } else if (topic instanceof Collection<?>) {
                    Collection<?> iterable = (Collection<?>)topic;

                    topics = iterable.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .map(TypeConverter::stringHexToByteArray)
                            .map(Topic::new)
                            .toArray(Topic[]::new);
                }
            }
        }
        else {
            topics = null;
        }

        AddressesTopicsFilter addressesTopicsFilter = new AddressesTopicsFilter(addresses, topics);

        // TODO review pending transaction processing
        // when fromBlock and/or toBlock are "pending"

        // Default from block value
        if (fr.fromBlock == null) {
            fr.fromBlock = "latest";
        }

        // Default to block value
        if (fr.toBlock == null) {
            fr.toBlock = "latest";
        }

        boolean fromLatestBlock = "latest".equalsIgnoreCase(fr.fromBlock);
        boolean toLatestBlock = "latest".equalsIgnoreCase(fr.toBlock);

        LogFilter filter = new LogFilter(addressesTopicsFilter, blockchain, fromLatestBlock, toLatestBlock);

        retrieveHistoricalData(fr, blockchain, filter);

        return filter;
    }

    private static void retrieveHistoricalData(Web3.FilterRequest fr, Blockchain blockchain, LogFilter filter) throws Exception {
        Block blockFrom = isBlockWord(fr.fromBlock) ? null : Web3Impl.getBlockByNumberOrStr(fr.fromBlock, blockchain);
        Block blockTo = isBlockWord(fr.toBlock) ? null : Web3Impl.getBlockByNumberOrStr(fr.toBlock, blockchain);

        if (blockFrom == null && "earliest".equalsIgnoreCase(fr.fromBlock)) {
            blockFrom = blockchain.getBlockByNumber(0);
        }

        if (blockFrom != null) {
            // need to add historical data
            blockTo = blockTo == null ? blockchain.getBestBlock() : blockTo;

            for (long blockNum = blockFrom.getNumber(); blockNum <= blockTo.getNumber(); blockNum++) {
                filter.onBlock(blockchain.getBlockByNumber(blockNum));
            }
        }
        else if ("latest".equalsIgnoreCase(fr.fromBlock)) {
            filter.onBlock(blockchain.getBestBlock());
        }
    }

    private static boolean isBlockWord(String id) {
        return "latest".equalsIgnoreCase(id) || "pending".equalsIgnoreCase(id) || "earliest".equalsIgnoreCase(id);
    }
}
