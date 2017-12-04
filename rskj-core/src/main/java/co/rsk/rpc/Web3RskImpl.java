/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.rpc;

import co.rsk.config.RskMiningConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.NetworkStateExporter;
import co.rsk.core.bc.EventInfo;
import co.rsk.core.bc.EventInfoItem;
import co.rsk.mine.*;
import co.rsk.rpc.exception.JsonRpcSubmitBlockException;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.scoring.PeerScoringManager;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Bloom;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.db.BlockStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.Repository;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3Impl;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

/**
 * Created by adrian.eidelman on 3/11/2016.
 */
public class Web3RskImpl extends Web3Impl {
    private static final Logger logger = LoggerFactory.getLogger("web3");
    private final NetworkStateExporter networkStateExporter;
    private final BlockStore blockStore;

    public Web3RskImpl(Ethereum eth,
                       WorldManager worldManager,
                       RskSystemProperties properties,
                       MinerClient minerClient,
                       MinerServer minerServer,
                       PersonalModule personalModule,
                       EthModule ethModule,
                       ChannelManager channelManager,
                       Repository repository,
                       PeerScoringManager peerScoringManager,
                       NetworkStateExporter networkStateExporter,
                       BlockStore blockStore,
                       PeerServer peerServer) {
        super(eth, worldManager, properties, minerClient, minerServer, personalModule, ethModule, channelManager, repository, peerScoringManager, peerServer);
        this.networkStateExporter = networkStateExporter;
        this.blockStore = blockStore;
    }

    public MinerWork mnr_getWork() {
        if (logger.isDebugEnabled()) {
            logger.debug("mnr_getWork()");
        }
        return minerServer.getWork();
    }

    public SubmittedBlockInfo mnr_submitBitcoinBlock(String bitcoinBlockHex) {
        if (logger.isDebugEnabled()) {
            logger.debug("mnr_submitBitcoinBlock(): {}", bitcoinBlockHex.length());
        }
        
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        new co.rsk.bitcoinj.core.Context(params);
        byte[] bitcoinBlockByteArray = Hex.decode(bitcoinBlockHex);
        co.rsk.bitcoinj.core.BtcBlock bitcoinBlock = params.getDefaultSerializer().makeBlock(bitcoinBlockByteArray);
        co.rsk.bitcoinj.core.BtcTransaction coinbase = bitcoinBlock.getTransactions().get(0);
        byte[] coinbaseAsByteArray = coinbase.bitcoinSerialize();
        List<Byte> coinbaseAsByteList = java.util.Arrays.asList(ArrayUtils.toObject(coinbaseAsByteArray));

        List<Byte> rskTagAsByteList = java.util.Arrays.asList(ArrayUtils.toObject(RskMiningConstants.RSK_TAG));

        int rskTagPosition = Collections.lastIndexOfSubList(coinbaseAsByteList, rskTagAsByteList);
        byte[] blockHashForMergedMiningArray = new byte[SHA3Helper.Size.S256.getValue()/8];
        System.arraycopy(coinbaseAsByteArray, rskTagPosition+ RskMiningConstants.RSK_TAG.length, blockHashForMergedMiningArray, 0, blockHashForMergedMiningArray.length);
        String blockHashForMergedMining = TypeConverter.toJsonHex(blockHashForMergedMiningArray);

        SubmitBlockResult result = minerServer.submitBitcoinBlock(blockHashForMergedMining, bitcoinBlock);

        if("OK".equals(result.getStatus())) {
            return result.getBlockInfo();
        } else {
            throw new JsonRpcSubmitBlockException(result.getMessage());
        }
    }

    public void ext_dumpState()  {
        Block bestBlcock = blockStore.getBestBlock();
        logger.info("Dumping state for block hash {}, block number {}", Hex.toHexString(bestBlcock.getHash()), bestBlcock.getNumber());
        networkStateExporter.exportStatus(System.getProperty("user.dir") + "/" + "rskdump.json");
    }

    /**
     * Export the blockchain tree as a tgf file to user.dir/rskblockchain.tgf
     * @param numberOfBlocks Number of block heights to include. Eg if best block is block 2300 and numberOfBlocks is 10, the graph will include blocks in heights 2290 to 2300.
     * @param includeUncles Whether to show uncle links (recommended value is false)
     */
    public void ext_dumpBlockchain(long numberOfBlocks, boolean includeUncles)  {
        Block bestBlock = blockStore.getBestBlock();
        logger.info("Dumping blockchain starting on block number {}, to best block number {}", bestBlock.getNumber()-numberOfBlocks, bestBlock.getNumber());
        PrintWriter writer = null;
        try {
            File graphFile = new File(System.getProperty("user.dir") + "/" + "rskblockchain.tgf");
            writer = new PrintWriter(new FileWriter(graphFile));

            List<Block> result = new LinkedList<>();
            long firstBlock = bestBlock.getNumber() - numberOfBlocks;
            if (firstBlock < 0) {
                firstBlock = 0;
            }
            for (long i = firstBlock; i < bestBlock.getNumber(); i++) {
                result.addAll(blockStore.getChainBlocksByNumber(i));
            }
            for (Block block : result) {
                writer.println(toSmallHash(block.getHash()) + " " + block.getNumber()+"-"+toSmallHash(block.getHash()));
            }
            writer.println("#");
            for (Block block : result) {
                writer.println(toSmallHash(block.getHash()) + " " + toSmallHash(block.getParentHash()) + " P");
                if (includeUncles) {
                    for (BlockHeader uncleHeader : block.getUncleList()) {
                        writer.println(toSmallHash(block.getHash()) + " " + toSmallHash(uncleHeader.getHash()) + " U");
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Could nos save node graph to file", e);
        } finally {
            if (writer!=null) {
                try {
                    writer.close();
                } catch (Exception e) {}
            }
        }
    }

    private String toSmallHash(byte[] input) {
        return Hex.toHexString(input).substring(56,64);
    }

    public static class Filter {
        abstract static class FilterEvent {
            public abstract Object getJsonEventObject();
        }

        List<FilterEvent> events = new ArrayList<>();

        public synchronized boolean hasNew() {
            return !events.isEmpty();
        }

        public synchronized Object[] poll() {
            Object[] ret = new Object[events.size()];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = events.get(i).getJsonEventObject();
            }
            this.events.clear();
            return ret;
        }

        protected synchronized void add(FilterEvent evt) {
            events.add(evt);
        }

        public void newBlockReceived(Block b,List<EventInfoItem> events) {
        }

        public void newPendingTx(Transaction tx) {
            // add TransactionReceipt for PendingTx
        }
    }
    class JsonEventFilter extends Filter {
        class EventFilterEvent extends FilterEvent {
            private final EventFilterElement el;

            EventFilterEvent(EventFilterElement el) {
                this.el = el;
            }

            @Override
            public EventFilterElement getJsonEventObject() {
                return el;
            }
        }

        EventFilter eventFilter;
        boolean onNewBlock;
        boolean onPendingTx;

        public JsonEventFilter(EventFilter eventFilter) {
            this.eventFilter = eventFilter;
        }

        void onEventMatch(EventInfo eventInfo, Block b, int txIndex, Transaction tx) {
            add(new JsonEventFilter.EventFilterEvent(new EventFilterElement(eventInfo, b, txIndex, tx)));
        }

        void onEventInfoItem(EventInfoItem e, Block b) {
            int txIndex = e.getEventInfo().getTxIndex();
            if (eventFilter.matchBloom(e.getBloomFilter())) {
                EventInfo eventInfo = e.getEventInfo();
                if (eventFilter.matchesExactly(eventInfo)) {
                        onEventMatch(e.getEventInfo(), b, txIndex, b.getTransactionsList().get(txIndex));
                    }
                }
        }


        void onBlock(Block b, List<EventInfoItem> events) {
            if (eventFilter.matchBloom(new Bloom(b.getLogBloom()))) {
                for (EventInfoItem e : events) {
                    if (eventFilter.matchesContractAddress(e.getAddress())) {
                        onEventInfoItem(e, b);
                    }
                }
            }
        }

        @Override
        public void newBlockReceived(Block b, List<EventInfoItem> events) {
            if (onNewBlock) {
                onBlock(b,events);
            }
        }

        @Override
        public void newPendingTx(Transaction tx) {
            //empty method
        }
    }

    AtomicInteger filterCounter = new AtomicInteger(1);
    Map<Integer, Filter> installedFilters = new Hashtable<>();

    //@Override
    public String eth_newEventFilter(FilterRequest fr) throws Exception {
        String str = null;
        try {
            EventFilter eventFilter = new EventFilter();

            if (fr.address instanceof String) {
                eventFilter.withContractAddress(stringHexToByteArray((String) fr.address));
            } else if (fr.address instanceof Collection<?>) {
                Collection<?> iterable = (Collection<?>)fr.address;

                byte[][] addresses = iterable.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(TypeConverter::stringHexToByteArray)
                        .toArray(byte[][]::new);

                eventFilter.withContractAddress(addresses);
            }

            if (fr.topics != null) {
                for (Object topic : fr.topics) {
                    if (topic == null) {
                        eventFilter.withTopic(null);
                    } else if (topic instanceof String) {
                        eventFilter.withTopic(new DataWord(stringHexToByteArray((String) topic)).getData());
                    } else if (topic instanceof Collection<?>) {
                        Collection<?> iterable = (Collection<?>)topic;

                        byte[][] topics = iterable.stream()
                                .filter(String.class::isInstance)
                                .map(String.class::cast)
                                .map(TypeConverter::stringHexToByteArray)
                                .map(DataWord::new)
                                .map(DataWord::getData)
                                .toArray(byte[][]::new);

                        eventFilter.withTopic(topics);
                    }
                }
            }

            JsonEventFilter filter = new JsonEventFilter(eventFilter);

            int id;

            synchronized (filterLock) {
                id = filterCounter.getAndIncrement();
                installedFilters.put(id, filter);
            }

            Block blockFrom = fr.fromBlock == null ? null : getBlockByNumberOrStr(fr.fromBlock);
            Block blockTo = fr.toBlock == null ? null : getBlockByNumberOrStr(fr.toBlock);

            if (blockFrom != null) {
                // need to add historical data
                blockTo = blockTo == null ? this.blockchain.getBestBlock() : blockTo;
                for (long blockNum = blockFrom.getNumber(); blockNum <= blockTo.getNumber(); blockNum++) {
                    filter.onBlock(this.blockchain.getBlockByNumber(blockNum),
                            this.blockchain.getEventsByBlockNumber(blockNum));
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

            return str = TypeConverter.toJsonHex(id);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("eth_newEventFilter(" + fr + "): " + str);
            }
        }
    }
}
