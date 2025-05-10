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
import co.rsk.crypto.Keccak256;
import co.rsk.jsonrpc.JsonRpcError;
import co.rsk.logfilter.BlocksBloom;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.rpc.netty.ExecTimeoutContext;
import co.rsk.util.HexUtils;
import org.ethereum.core.*;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.LogInfo;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * Created by ajlopez on 17/01/2018.
 */
public class LogFilter extends Filter {

    private final AddressesTopicsFilter addressesTopicsFilter;
    private final boolean fromLatestBlock;
    private final boolean toLatestBlock;
    private final Blockchain blockchain;
    private final long maxBlocksToQuery;
    private final long maxLogsToReturn;

    private LogFilter(AddressesTopicsFilter addressesTopicsFilter, Blockchain blockchain, boolean fromLatestBlock, boolean toLatestBlock, long maxBlocksToQuery, long maxLogsToReturn) {
        this.maxLogsToReturn = maxLogsToReturn;
        this.addressesTopicsFilter = addressesTopicsFilter;
        this.blockchain = blockchain;
        this.fromLatestBlock = fromLatestBlock;
        this.toLatestBlock = toLatestBlock;
        this.maxBlocksToQuery = maxBlocksToQuery;
    }

    void onLogMatch(LogInfo logInfo, Block b, int txIndex, Transaction tx, int logIdx) {
        add(new LogFilterEvent(new LogFilterElement(logInfo, b, txIndex, tx, logIdx)));
    }

    void onTransaction(Transaction tx, Block block, int txIndex, boolean reverseLogIdxOrder) {
        TransactionInfo txInfo = blockchain.getTransactionInfoByBlock(tx, block.getHash().getBytes());
        TransactionReceipt receipt = txInfo.getReceipt();

        LogFilterElement[] logs = new LogFilterElement[receipt.getLogInfoList().size()];

        for (int i = 0; i < logs.length; i++) {
            int logIdx = reverseLogIdxOrder ? logs.length - i - 1 : i;

            LogInfo logInfo = receipt.getLogInfoList().get(logIdx);

            if (addressesTopicsFilter.matchesExactly(logInfo)) {
                onLogMatch(logInfo, block, txIndex, receipt.getTransaction(), logIdx);
            }
        }
    }
    void onTransaction(TransactionInfo txInfo, Block block, int txIndex, boolean reverseLogIdxOrder) {
        TransactionReceipt receipt = txInfo.getReceipt();

        LogFilterElement[] logs = new LogFilterElement[receipt.getLogInfoList().size()];

        for (int i = 0; i < logs.length; i++) {
            int logIdx = reverseLogIdxOrder ? logs.length - i - 1 : i;

            LogInfo logInfo = receipt.getLogInfoList().get(logIdx);

            if (addressesTopicsFilter.matchesExactly(logInfo)) {
                onLogMatch(logInfo, block, txIndex, receipt.getTransaction(), logIdx);
            }
        }
    }

    void onTransactionV2(TransactionInfo txInfo, Block block, int txIndex, int acc) {
        TransactionReceipt receipt = txInfo.getReceipt();

        LogFilterElement[] logs = new LogFilterElement[receipt.getLogInfoList().size()];

        for (int i = 0; i < logs.length; i++) {
            int logIdx = acc + i;

            LogInfo logInfo = receipt.getLogInfoList().get(i);

            if (addressesTopicsFilter.matchesExactly(logInfo)) {
                onLogMatch(logInfo, block, txIndex, receipt.getTransaction(), logIdx);
            }
        }
    }

    //TODO for this test ignoring reverse order
    void onBlock(Block block, boolean reverseTxOrder) {
        if (!addressesTopicsFilter.matchBloom(new Bloom(block.getLogBloom()))) {
            return;
        }

        List<Transaction> txs = block.getTransactionsList();

        int acc = 0;
        for (int i = 0; i < txs.size(); i++) {
            Transaction tx = txs.get(i);
            TransactionInfo txInfo = blockchain.getTransactionInfoByBlock(tx, block.getHash().getBytes());
            onTransactionV2(txInfo, block, i, acc);
            acc+=txInfo.getReceipt().getLogInfoList().size();
        }
    }

    @Override
    protected synchronized void add(FilterEvent event) {
        if (maxLogsToReturn > 0 && eventsSize() + 1 > maxLogsToReturn) {
            throw new RskJsonRpcRequestException(JsonRpcError.MAX_ETH_GET_LOGS_LIMIT, "Filter returned more than " + maxLogsToReturn + " logs.");
        }
        super.add(event);
    }

    @Override
    public void newBlockReceived(Block b) {
        if (this.fromLatestBlock) {
            this.clearEvents();
            onBlock(b, false);
        } else if (this.toLatestBlock) {
            onBlock(b, false);
        }
    }

    @Override
    public void newPendingTx(Transaction tx) {
        //empty method
    }

    public static LogFilter fromFilterRequest(FilterRequest fr, Blockchain blockchain, BlocksBloomStore blocksBloomStore) {
        return fromFilterRequest(fr, blockchain, blocksBloomStore, 0L, 0L);
    }

    public static LogFilter fromFilterRequest(FilterRequest fr, Blockchain blockchain, BlocksBloomStore blocksBloomStore, Long maxBlocksToQuery, Long maxBlocksToReturn) {
        RskAddress[] addresses;
        // Now, there is an array of array of topics
        // first level are topic filters by position
        // second level contains OR topic filters for that position
        // null value matches anything
        Topic[][] topics;

        if (fr.getAddress() instanceof String) {
            addresses = new RskAddress[]{new RskAddress(HexUtils.stringHexToByteArray((String) fr.getAddress()))};
        } else if (fr.getAddress() instanceof Collection<?>) {
            Collection<?> iterable = (Collection<?>) fr.getAddress();

            addresses = iterable.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(HexUtils::stringHexToByteArray)
                    .map(RskAddress::new)
                    .toArray(RskAddress[]::new);
        } else {
            addresses = new RskAddress[0];
        }

        if (fr.getTopics() != null) {
            topics = new Topic[fr.getTopics().length][];

            for (int nt = 0; nt < fr.getTopics().length; nt++) {
                Object topic = fr.getTopics()[nt];

                if (topic == null) {
                    topics[nt] = new Topic[0];
                } else if (topic instanceof String) {
                    topics[nt] = new Topic[]{new Topic((String) topic)};
                } else if (topic instanceof Collection<?>) {
                    // TODO list of topics as topic with OR logic

                    Collection<?> iterable = (Collection<?>) topic;

                    topics[nt] = iterable.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .map(HexUtils::stringHexToByteArray)
                            .map(Topic::new)
                            .toArray(Topic[]::new);
                }
            }
        } else {
            topics = null;
        }

        AddressesTopicsFilter addressesTopicsFilter = new AddressesTopicsFilter(addresses, topics);

        // TODO review pending transaction processing
        // when fromBlock and/or toBlock are "pending"

        validateFilterRequestParameters(fr);

        // Default from block value
        if (fr.getFromBlock() == null) {
            fr.setFromBlock("latest");
        }

        // Default to block value
        if (fr.getToBlock() == null) {
            fr.setToBlock("latest");
        }

        boolean fromLatestBlock = "latest".equalsIgnoreCase(fr.getFromBlock());
        boolean toLatestBlock = "latest".equalsIgnoreCase(fr.getToBlock());

        if (maxBlocksToQuery == null) {
            maxBlocksToQuery = 0L;
        }
        if (maxBlocksToReturn == null) {
            maxBlocksToReturn = 0L;
        }

        LogFilter filter = new LogFilterBuilder()
                .addressesTopicsFilter(addressesTopicsFilter)
                .blockchain(blockchain)
                .fromLatestBlock(fromLatestBlock)
                .toLatestBlock(toLatestBlock)
                .maxBlocksToQuery(maxBlocksToQuery)
                .maxBlocksToReturn(maxBlocksToReturn)
                .build();

        retrieveHistoricalData(fr, blockchain, filter, blocksBloomStore);

        return filter;
    }

    /**
     * Cannot use both blockHash and fromBlock/toBlock filters, according to EIP-234
     */
    private static void validateFilterRequestParameters(FilterRequest fr) {
        if (fr.getBlockHash() != null && (fr.getFromBlock() != null || fr.getToBlock() != null)) {
            throw RskJsonRpcRequestException.invalidParamError("Cannot specify both blockHash and fromBlock/toBlock");
        }
    }

    private static void retrieveHistoricalData(FilterRequest fr, Blockchain blockchain, LogFilter filter, BlocksBloomStore blocksBloomStore) {
        if (fr.getBlockHash() != null) {
            processSingleBlockByHash(fr.getBlockHash(), blockchain, filter, blocksBloomStore);
            return;
        }

        Block blockFrom = isBlockWord(fr.getFromBlock()) ? null : Web3Impl.getBlockByNumberOrStr(fr.getFromBlock(), blockchain);
        Block blockTo = isBlockWord(fr.getToBlock()) ? null : Web3Impl.getBlockByNumberOrStr(fr.getToBlock(), blockchain);

        if (blockFrom == null && "earliest".equalsIgnoreCase(fr.getFromBlock())) {
            blockFrom = blockchain.getBlockByNumber(0);
        }

        if (blockFrom != null) {
            // need to add historical data
            blockTo = blockTo == null ? blockchain.getBestBlock() : blockTo;

            processBlocks(blockFrom, blockTo, filter, blockchain, blocksBloomStore);
        } else if ("latest".equalsIgnoreCase(fr.getFromBlock())) {
            filter.onBlock(blockchain.getBestBlock(), false);
        }
    }

    private static void processSingleBlockByHash(String blockHash, Blockchain blockchain, LogFilter filter, BlocksBloomStore blocksBloomStore) {
        Keccak256 keccak256BlockHash = new Keccak256(HexUtils.stringHexToByteArray(blockHash));
        Block blockByHash = blockchain.getBlockByHash(keccak256BlockHash.getBytes());
        if (blockByHash == null) {
            return;
        }

        processBlocks(blockByHash, blockByHash, filter, blockchain, blocksBloomStore);
    }

    private static void processBlocks(Block fromBlock, Block toBlock, LogFilter filter, Blockchain blockchain, BlocksBloomStore blocksBloomStore) {
        filter.checkLimit(fromBlock.getNumber(), toBlock.getNumber());

        final long bestBlockNumber = blockchain.getBestBlock().getNumber();

        BlocksBloom bloomAccumulator = null;

        Block block = toBlock;
        long blockNumber = block.getNumber();
        Keccak256 blockHash = block.getHash();

        boolean skippingToNumber = false;

        do {
            ExecTimeoutContext.checkIfExpired();

            boolean isConfirmedBlock = blockNumber <= bestBlockNumber - blocksBloomStore.getNoConfirmations();

            BlocksBloom blocksBloom = isConfirmedBlock ? blocksBloomStore.getBlocksBloomByNumber(blockNumber) : null;
            if (canSkipByBloom(blocksBloom, filter)) {
                blockNumber = blocksBloomStore.firstNumberInRange(blockNumber) - 1;
                skippingToNumber = true;
                continue;
            }

            if (skippingToNumber) {
                block = blockchain.getBlockByNumber(blockNumber);
            } else {
                // redundant on first iter as we already have the block, but it's cached so left like this for code simplicity
                block = blockchain.getBlockByHash(blockHash.getBytes());
            }
            skippingToNumber = false;

            // reverseTxOrder=true to process txs in reverse order for later complete list reverse
            filter.onBlock(block, true);

            boolean hasBloom = blocksBloom != null;
            if (isConfirmedBlock && !hasBloom) {
                bloomAccumulator = addBlockBloom(block, bloomAccumulator, blocksBloomStore);
            }

            blockNumber = blockNumber - 1;
            blockHash = block.getParentHash();
        } while (blockNumber >= fromBlock.getNumber());

        // sort in a from-to fashion after looping in reverse order
        filter.reverseEvents();
    }

    private static boolean canSkipByBloom(BlocksBloom blocksBloom, LogFilter filter) {
        return blocksBloom != null && !filter.addressesTopicsFilter.matchBloom(blocksBloom.getBloom());
    }

    @Nullable
    private static BlocksBloom addBlockBloom(Block block, BlocksBloom bloomAccumulator, BlocksBloomStore blocksBloomStore) {
        // reset bloomAccumulator on every confirmed lastInRange block to start a new bloom
        if (blocksBloomStore.lastNumberInRange(block.getNumber()) == block.getNumber()) {
            bloomAccumulator = BlocksBloom.createEmptyWithBackwardsAddition();
        }

        // start accumulating blocks the first time a lastInRange block is reached to keep only complete blooms
        if (bloomAccumulator == null) {
            return null;
        }

        bloomAccumulator.addBlockBloom(block.getNumber(), new Bloom(block.getLogBloom()));

        boolean firstInRange = blocksBloomStore.firstNumberInRange(block.getNumber()) == block.getNumber();
        if (firstInRange) {
            blocksBloomStore.addBlocksBloom(bloomAccumulator);
            bloomAccumulator = null;
        }

        return bloomAccumulator;
    }

    private static boolean isBlockWord(String id) {
        return "latest".equalsIgnoreCase(id) || "pending".equalsIgnoreCase(id) || "earliest".equalsIgnoreCase(id);
    }

    private void checkLimit(long from, long to) {
        long totalToQuery = to - from;
        if (this.maxBlocksToQuery == 0 || totalToQuery == 0) {
            return;
        }
        if (totalToQuery > this.maxBlocksToQuery) {
            throw new RskJsonRpcRequestException(JsonRpcError.MAX_ETH_GET_LOGS_LIMIT, "Cannot query more than " + this.maxBlocksToQuery + " blocks at once");
        }
    }

    public static class LogFilterBuilder {

        private AddressesTopicsFilter addressesTopicsFilter;
        private Blockchain blockchain;
        private boolean fromLatestBlock;
        private boolean toLatestBlock;
        private long maxBlocksToQuery;
        private long maxBlocksToReturn;

        public LogFilterBuilder() {
            this.addressesTopicsFilter = null;
            this.blockchain = null;
            this.fromLatestBlock = false;
            this.toLatestBlock = false;
            this.maxBlocksToQuery = 0;
            this.maxBlocksToReturn = 0;
        }

        public LogFilterBuilder addressesTopicsFilter(AddressesTopicsFilter addressesTopicsFilter) {
            this.addressesTopicsFilter = addressesTopicsFilter;
            return this;
        }

        public LogFilterBuilder blockchain(Blockchain blockchain) {
            this.blockchain = blockchain;
            return this;
        }

        public LogFilterBuilder fromLatestBlock(boolean fromLatestBlock) {
            this.fromLatestBlock = fromLatestBlock;
            return this;
        }

        public LogFilterBuilder toLatestBlock(boolean toLatestBlock) {
            this.toLatestBlock = toLatestBlock;
            return this;
        }

        public LogFilterBuilder maxBlocksToQuery(long maxBlocksToQuery) {
            this.maxBlocksToQuery = maxBlocksToQuery;
            return this;
        }

        public LogFilterBuilder maxBlocksToReturn(long maxBlocksToReturn) {
            this.maxBlocksToReturn = maxBlocksToReturn;
            return this;
        }

        public LogFilter build() {
            return new LogFilter(addressesTopicsFilter, blockchain, fromLatestBlock, toLatestBlock, maxBlocksToQuery, maxBlocksToReturn);
        }
    }

    static class LogFilterEvent implements FilterEvent {
        private final LogFilterElement el;

        LogFilterEvent(LogFilterElement el) {
            this.el = el;
        }

        @Override
        public LogFilterElement getJsonEventObject() {
            return el;
        }
    }
}
