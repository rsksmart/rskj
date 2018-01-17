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
import org.ethereum.vm.DataWord;
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
    boolean onNewBlock;
    boolean onPendingTx;
    private final Blockchain blockchain;

    public LogFilter(AddressesTopicsFilter addressesTopicsFilter, Blockchain blockchain) {
        this.addressesTopicsFilter = addressesTopicsFilter;
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
            if (addressesTopicsFilter.matchesContractAddress(logInfo.getAddress())) {
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
        if (onNewBlock) {
            onBlock(b);
        }
    }

    @Override
    public void newPendingTx(Transaction tx) {
        //empty method
    }

    public static LogFilter fromFilterRequest(Web3.FilterRequest fr, Blockchain blockchain) throws Exception {
        byte[][] addresses;
        byte[][] topics = null;

        if (fr.address instanceof String) {
            addresses = new byte[][] { stringHexToByteArray((String) fr.address) };
        } else if (fr.address instanceof Collection<?>) {
            Collection<?> iterable = (Collection<?>)fr.address;

            addresses = iterable.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(TypeConverter::stringHexToByteArray)
                    .toArray(byte[][]::new);
        }
        else {
            addresses = new byte[0][];
        }

        if (fr.topics != null) {
            for (Object topic : fr.topics) {
                if (topic == null) {
                    topics = null;
                } else if (topic instanceof String) {
                    topics = new byte[][] { new DataWord(stringHexToByteArray((String) topic)).getData() };
                } else if (topic instanceof Collection<?>) {
                    Collection<?> iterable = (Collection<?>)topic;

                    topics = iterable.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .map(TypeConverter::stringHexToByteArray)
                            .map(DataWord::new)
                            .map(DataWord::getData)
                            .toArray(byte[][]::new);
                }
            }
        }
        else {
            topics = null;
        }

        AddressesTopicsFilter addressesTopicsFilter = new AddressesTopicsFilter(addresses, topics);

        LogFilter filter = new LogFilter(addressesTopicsFilter, blockchain);

        Block blockFrom = fr.fromBlock == null ? blockchain.getBestBlock() : Web3Impl.getBlockByNumberOrStr(fr.fromBlock, blockchain);
        Block blockTo = fr.toBlock == null ? null : Web3Impl.getBlockByNumberOrStr(fr.toBlock, blockchain);

        if (blockFrom != null) {
            // need to add historical data
            blockTo = blockTo == null ? blockchain.getBestBlock() : blockTo;

            for (long blockNum = blockFrom.getNumber(); blockNum <= blockTo.getNumber(); blockNum++) {
                filter.onBlock(blockchain.getBlockByNumber(blockNum));
            }
        }

        // the following is not precisely documented
        if ("pending".equalsIgnoreCase(fr.fromBlock) || "pending".equalsIgnoreCase(fr.toBlock)) {
            filter.onPendingTx = true;
        } else if ("latest".equalsIgnoreCase(fr.fromBlock) || "latest".equalsIgnoreCase(fr.toBlock)) {
            filter.onNewBlock = true;
        }

        // RSK brute force
        filter.onNewBlock = true;

        return filter;
    }
}
