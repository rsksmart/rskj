/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.net;

import co.rsk.config.InternalService;
import co.rsk.core.BlockDifficulty;
import co.rsk.net.messages.*;
import co.rsk.net.sync.*;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieDTOInOrderIterator;
import co.rsk.trie.TrieDTOInOrderRecoverer;
import co.rsk.trie.TrieStore;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * Snapshot Synchronization consist in 3 steps:
 * 1. Status: exchange message with the server, to know which block we are going to sync and what the size of the Unitrie of that block is.
 * it also exchanges previous blocks (4k) and the block of the snapshot, which has a root hash of the state.
 * 2. State chunks: share the state in chunks of N nodes. Each chunk is independently verifiable.
 * 3. Rebuild the state: Rebuild the state in the client side, save it to the db and save also the blocks corresponding to the snapshot.
 * <p>
 * After this process, the node should be able to start the long sync to the tip and then the backward sync to the genesis.
 */
public class SnapshotProcessor implements InternalService {

    private static final Logger logger = LoggerFactory.getLogger("snapshotprocessor");

    public static final int BLOCK_NUMBER_CHECKPOINT = 5000;
    public static final int BLOCK_CHUNK_SIZE = 400;
    public static final int BLOCKS_REQUIRED = 6000;
    public static final long CHUNK_ITEM_SIZE = 1024L;
    private final Blockchain blockchain;
    private final TrieStore trieStore;
    private final BlockStore blockStore;
    private final int chunkSize;
    private final SnapshotPeersInformation peersInformation;
    private final TransactionPool transactionPool;
    private long messageId = 0;

    // flag for parallel requests
    private final boolean parallel;

    private final BlockingQueue<SyncMessageHandler.Job> requestQueue = new LinkedBlockingQueue<>();

    private volatile Boolean isRunning;
    private final Thread thread;
    public SnapshotProcessor(Blockchain blockchain,
                             TrieStore trieStore,
                             SnapshotPeersInformation peersInformation,
                             BlockStore blockStore,
                             TransactionPool transactionPool,
                             int chunkSize,
                             boolean isParallelEnabled) {
        this(blockchain, trieStore, peersInformation, blockStore, transactionPool, chunkSize, isParallelEnabled, null);
    }

    @VisibleForTesting
    SnapshotProcessor(Blockchain blockchain,
                      TrieStore trieStore,
                      SnapshotPeersInformation peersInformation,
                      BlockStore blockStore,
                      TransactionPool transactionPool,
                      int chunkSize,
                      boolean isParallelEnabled,
                      @Nullable SyncMessageHandler.Listener listener) {
        this.blockchain = blockchain;
        this.trieStore = trieStore;
        this.peersInformation = peersInformation;
        this.chunkSize = chunkSize;
        this.blockStore = blockStore;
        this.transactionPool = transactionPool;
        this.parallel = isParallelEnabled;
        this.thread = new Thread(new SyncMessageHandler("SNAP requests", requestQueue, listener) {

            @Override
            public boolean isRunning() {
                return isRunning;
            }
        }, "snap sync request handler");
    }

    public void startSyncing() {
        // get more than one peer, use the peer queue
        // TODO(snap-poc) deal with multiple peers algorithm here
        Peer peer = peersInformation.getBestSnapPeerCandidates().get(0);
        logger.info("CLIENT - Starting Snapshot sync.");
        requestSnapStatus(peer);
    }

    // TODO(snap-poc) should be called on errors too
    private void stopSyncing(SnapSyncState state) {
        state.finish();
    }

    /**
     * STATUS
     */
    private void requestSnapStatus(Peer peer) {
        SnapStatusRequestMessage message = new SnapStatusRequestMessage();
        peer.sendMessage(message);
    }

    public void processSnapStatusRequest(Peer sender, SnapStatusRequestMessage requestMessage) {
        if (isRunning != Boolean.TRUE) {
            logger.warn("processSnapStatusRequest: invalid state, isRunning: [{}]", isRunning);
            return;
        }

        try {
            requestQueue.put(new SyncMessageHandler.Job(sender, requestMessage) {
                @Override
                public void run() {
                    processSnapStatusRequestInternal(sender, requestMessage);
                }
            });
        } catch (InterruptedException e) {
            logger.warn("SnapStatusRequestMessage processing was interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    void processSnapStatusRequestInternal(Peer sender, SnapStatusRequestMessage ignoredRequestMessage) {
        logger.debug("SERVER - Processing snapshot status request.");
        long bestBlockNumber = blockchain.getBestBlock().getNumber();
        long checkpointBlockNumber = bestBlockNumber - (bestBlockNumber % BLOCK_NUMBER_CHECKPOINT);
        logger.debug("SERVER - checkpointBlockNumber: {}, bestBlockNumber: {}", checkpointBlockNumber, bestBlockNumber);
        List<Block> blocks = Lists.newArrayList();
        List<BlockDifficulty> difficulties = Lists.newArrayList();
        for (long i = checkpointBlockNumber - BLOCK_CHUNK_SIZE; i < checkpointBlockNumber; i++) {
            Block block = blockchain.getBlockByNumber(i);
            blocks.add(block);
            difficulties.add(blockStore.getTotalDifficultyForHash(block.getHash().getBytes()));
        }

        logger.trace("SERVER - Sending snapshot status response. From block {} to block {} - chunksize {}", blocks.get(0).getNumber(), blocks.get(blocks.size() - 1).getNumber(), BLOCK_CHUNK_SIZE);
        Block checkpointBlock = blockchain.getBlockByNumber(checkpointBlockNumber);
        blocks.add(checkpointBlock);
        logger.trace("SERVER - adding checkpoint block: {}", checkpointBlock.getNumber());
        difficulties.add(blockStore.getTotalDifficultyForHash(checkpointBlock.getHash().getBytes()));
        byte[] rootHash = checkpointBlock.getStateRoot();
        Optional<TrieDTO> opt = trieStore.retrieveDTO(rootHash);

        long trieSize = 0;
        if (opt.isPresent()) {
            trieSize = opt.get().getTotalSize();
        } else {
            logger.debug("SERVER - trie is notPresent");
        }
        logger.debug("SERVER - processing snapshot status request - rootHash: {} trieSize: {}", rootHash, trieSize);
        SnapStatusResponseMessage responseMessage = new SnapStatusResponseMessage(blocks, difficulties, trieSize);
        sender.sendMessage(responseMessage);
    }

    public void processSnapStatusResponse(SnapSyncState state, Peer sender, SnapStatusResponseMessage responseMessage) {
        List<Block> blocksFromResponse = responseMessage.getBlocks();
        List<BlockDifficulty> difficultiesFromResponse = responseMessage.getDifficulties();
        Block lastBlock = blocksFromResponse.get(blocksFromResponse.size() - 1);

        state.setLastBlock(lastBlock);
        state.setLastBlockDifficulty(lastBlock.getCumulativeDifficulty());
        state.setRemoteRootHash(lastBlock.getStateRoot());
        state.setRemoteTrieSize(responseMessage.getTrieSize());

        for (int i = 0; i < blocksFromResponse.size(); i++) {
            state.addBlock(new ImmutablePair<>(blocksFromResponse.get(i), difficultiesFromResponse.get(i)));
        }
        logger.debug("CLIENT - Processing snapshot status response - last blockNumber: {} triesize: {}", lastBlock.getNumber(), state.getRemoteTrieSize());
        logger.debug("Blocks included in the response: {} from {} to {}", blocksFromResponse.size(), blocksFromResponse.get(0).getNumber(), blocksFromResponse.get(blocksFromResponse.size() - 1).getNumber());
        requestBlocksChunk(sender, blocksFromResponse.get(0).getNumber());
        generateChunkRequestTasks(state);
        startRequestingChunks(state);
    }

    /**
     * BLOCK CHUNK
     */
    private void requestBlocksChunk(Peer sender, long blockNumber) {
        logger.debug("CLIENT - Requesting block chunk to node {} - block {}", sender.getPeerNodeID(), blockNumber);
        sender.sendMessage(new SnapBlocksRequestMessage(blockNumber));
    }

    public void processSnapBlocksRequest(Peer sender, SnapBlocksRequestMessage requestMessage) {
        if (isRunning != Boolean.TRUE) {
            logger.warn("processSnapBlocksRequest: invalid state, isRunning: [{}]", isRunning);
            return;
        }

        try {
            requestQueue.put(new SyncMessageHandler.Job(sender, requestMessage) {
                @Override
                public void run() {
                    processSnapBlocksRequestInternal(sender, requestMessage);
                }
            });
        } catch (InterruptedException e) {
            logger.warn("SnapBlocksRequestMessage processing was interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    void processSnapBlocksRequestInternal(Peer sender, SnapBlocksRequestMessage requestMessage) {
        logger.debug("SERVER - Processing snap blocks request");
        List<Block> blocks = Lists.newArrayList();
        List<BlockDifficulty> difficulties = Lists.newArrayList();
        long startingBlockNumber = requestMessage.getBlockNumber() - BLOCK_CHUNK_SIZE;
        for (long i = startingBlockNumber; i < requestMessage.getBlockNumber(); i++) {
            Block block = blockchain.getBlockByNumber(i);
            blocks.add(block);
            difficulties.add(blockStore.getTotalDifficultyForHash(block.getHash().getBytes()));
        }
        logger.debug("SERVER - Sending snap blocks response. From block {} to block {} - chunksize {}", blocks.get(0).getNumber(), blocks.get(blocks.size() - 1).getNumber(), BLOCK_CHUNK_SIZE);
        SnapBlocksResponseMessage responseMessage = new SnapBlocksResponseMessage(blocks, difficulties);
        sender.sendMessage(responseMessage);
    }

    public void processSnapBlocksResponse(SnapSyncState state, Peer sender, SnapBlocksResponseMessage responseMessage) {
        long lastRequiredBlock = state.getLastBlock().getNumber() - BLOCKS_REQUIRED;
        List<Block> blocksFromResponse = responseMessage.getBlocks();
        logger.debug("CLIENT - Processing snap blocks response. Receiving from block {} to block {} Objective: {}.", blocksFromResponse.get(0).getNumber(), blocksFromResponse.get(blocksFromResponse.size() - 1).getNumber(), lastRequiredBlock);
        logger.debug("dummy - lastRequiredBlock  "+ lastRequiredBlock);
        logger.debug("dummy - Processing snap blocks response. Receiving from block {} to block {} Objective: {}.", blocksFromResponse.get(0).getNumber(), blocksFromResponse.get(blocksFromResponse.size() - 1).getNumber(), lastRequiredBlock);
        List<BlockDifficulty> difficultiesFromResponse = responseMessage.getDifficulties();

        for (int i = 0; i < blocksFromResponse.size(); i++) {
            state.addBlock(new ImmutablePair<>(blocksFromResponse.get(i), difficultiesFromResponse.get(i)));
        }
        long nextChunk = blocksFromResponse.get(0).getNumber();
        logger.debug("dummy next chunk: {}",nextChunk);
        logger.debug("CLIENT - SnapBlock - nexChunk : {} - lastRequired {}, missing {}", nextChunk, lastRequiredBlock, nextChunk - lastRequiredBlock);
        logger.debug("dummy: nextChunk   "+ nextChunk + "    lastRequiredBlock  "+ lastRequiredBlock);
        if (nextChunk > lastRequiredBlock) {
            requestBlocksChunk(sender, nextChunk);
        } else {
            logger.info("CLIENT - Finished Snap blocks request sending.");
        }
    }

    /**
     * STATE CHUNK
     */
    private void requestStateChunk(Peer peer, long from, long blockNumber, int chunkSize) {
        logger.debug("CLIENT - Requesting state chunk to node {} - block {} - chunkNumber {}", peer.getPeerNodeID(), blockNumber, from / chunkSize);
        SnapStateChunkRequestMessage message = new SnapStateChunkRequestMessage(messageId++, blockNumber, from, chunkSize);
        peer.sendMessage(message);
    }

    public void processStateChunkRequest(Peer sender, SnapStateChunkRequestMessage requestMessage) {
        if (isRunning != Boolean.TRUE) {
            logger.warn("processStateChunkRequest: invalid state, isRunning: [{}]", isRunning);
            return;
        }

        try {
            requestQueue.put(new SyncMessageHandler.Job(sender, requestMessage) {
                @Override
                public void run() {
                    processStateChunkRequestInternal(sender, requestMessage);
                }
            });
        } catch (InterruptedException e) {
            logger.warn("SnapStateChunkRequestMessage processing was interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    void processStateChunkRequestInternal(Peer sender, SnapStateChunkRequestMessage request) {
        long startChunk = System.currentTimeMillis();

        List<byte[]> trieEncoded = new ArrayList<>();
        Block block = blockchain.getBlockByNumber(request.getBlockNumber());
        final long to = request.getFrom() + (request.getChunkSize() * CHUNK_ITEM_SIZE);
        logger.debug("SERVER - Processing state chunk request from node {}. From {} to calculated {} being chunksize {}", sender.getPeerNodeID(), request.getFrom(), to, request.getChunkSize());
        logger.debug("SERVER - Sending state chunk from {} to {}", request.getFrom(), to);
        TrieDTOInOrderIterator it = new TrieDTOInOrderIterator(trieStore, block.getStateRoot(), request.getFrom(), to);

        // First we add the root nodes on the left of the current node. They are used to validate the chunk.
        List<byte[]> preRootNodes = it.getPreRootNodes().stream().map((t) -> RLP.encodeList(RLP.encodeElement(t.getEncoded()), RLP.encodeElement(getBytes(t.getLeftHash())))).collect(Collectors.toList());
        byte[] preRootNodesBytes = !preRootNodes.isEmpty() ? RLP.encodeList(preRootNodes.toArray(new byte[0][0])) : RLP.encodedEmptyList();

        // Then we add the nodes corresponding to the chunk.
        TrieDTO first = it.peek();
        TrieDTO last = null;
        while (it.hasNext()) {
            TrieDTO e = it.next();
            if (it.hasNext() || it.isEmpty()) {
                last = e;
                trieEncoded.add(RLP.encodeElement(e.getEncoded()));
            }
        }
        byte[] firstNodeLeftHash = RLP.encodeElement(first.getLeftHash());
        byte[] nodesBytes = RLP.encodeList(trieEncoded.toArray(new byte[0][0]));
        byte[] lastNodeHashes = last != null ? RLP.encodeList(RLP.encodeElement(getBytes(last.getLeftHash())), RLP.encodeElement(getBytes(last.getRightHash()))) : RLP.encodedEmptyList();
        // Last we add the root nodes on the right of the last visited node. They are used to validate the chunk.
        List<byte[]> postRootNodes = it.getNodesLeftVisiting().stream().map((t) -> RLP.encodeList(RLP.encodeElement(t.getEncoded()), RLP.encodeElement(getBytes(t.getRightHash())))).collect(Collectors.toList());
        byte[] postRootNodesBytes = !postRootNodes.isEmpty() ? RLP.encodeList(postRootNodes.toArray(new byte[0][0])) : RLP.encodedEmptyList();
        byte[] chunkBytes = RLP.encodeList(preRootNodesBytes, nodesBytes, firstNodeLeftHash, lastNodeHashes, postRootNodesBytes);

        SnapStateChunkResponseMessage responseMessage = new SnapStateChunkResponseMessage(request.getId(), chunkBytes, request.getBlockNumber(), request.getFrom(), to, it.isEmpty());

        long totalChunkTime = System.currentTimeMillis() - startChunk;

        logger.debug("SERVER - Sending state chunk from {} of {} bytes to node {}, totalTime {}ms", request.getFrom(), chunkBytes.length, sender.getPeerNodeID(), totalChunkTime);
        logger.debug("dummy - responseMessage.isComplete()?:  "+ responseMessage.isComplete());
        sender.sendMessage(responseMessage);
    }

    public void processStateChunkResponse(SnapSyncState state, Peer peer, SnapStateChunkResponseMessage responseMessage) {
        logger.debug("CLIENT - State chunk received chunkNumber {}. From {} to {} of total size {}", responseMessage.getFrom() / CHUNK_ITEM_SIZE, responseMessage.getFrom(), responseMessage.getTo(), state.getRemoteTrieSize());

        PriorityQueue<SnapStateChunkResponseMessage> queue = state.getSnapStateChunkQueue();
        queue.add(responseMessage);

        while (!queue.isEmpty()) {
            SnapStateChunkResponseMessage nextMessage = queue.peek();
            long nextExpectedFrom = state.getNextExpectedFrom();
            logger.debug("CLIENT - State chunk dequeued from: {} - expected: {}", nextMessage.getFrom(), nextExpectedFrom);
            if (nextMessage.getFrom() == nextExpectedFrom) {
                try {
                    logger.debug("dummy    _  !message.isComplete()   "+ !queue.peek().isComplete());
                    processOrderedStateChunkResponse(state, peer, queue.poll());
                    state.setNextExpectedFrom(nextExpectedFrom + chunkSize * CHUNK_ITEM_SIZE);
                } catch (Exception e) {
                    logger.error("Error while processing chunk response. {}", e.getMessage(), e);
                    onStateChunkResponseError(peer, nextMessage);
                }
            } else {
                break;
            }
        }

        if (!responseMessage.isComplete()) {
            logger.debug("CLIENT - State chunk response not complete. Requesting next chunk.");
            executeNextChunkRequestTask(state, peer);
        }
    }

    @VisibleForTesting
    void onStateChunkResponseError(Peer peer, SnapStateChunkResponseMessage responseMessage) {
        logger.error("Error while processing chunk response from {} of peer {}. Asking for chunk again.", responseMessage.getFrom(), peer.getPeerNodeID());
        Peer alternativePeer = peersInformation.getBestSnapPeerCandidates().stream()
                .filter(listedPeer -> !listedPeer.getPeerNodeID().equals(peer.getPeerNodeID()))
                .findFirst()
                .orElse(peer);
        logger.debug("Requesting state chunk \"from\" {} to peer {}", responseMessage.getFrom(), peer.getPeerNodeID());
        requestStateChunk(alternativePeer, responseMessage.getFrom(), responseMessage.getBlockNumber(), chunkSize);
    }


    private void processOrderedStateChunkResponse(SnapSyncState state, Peer peer, SnapStateChunkResponseMessage message) throws Exception {
        logger.debug("CLIENT - Processing State chunk received from {} to {}", message.getFrom(), message.getTo());
        logger.debug("dummy - Processing State chunk received from {} to {}", message.getFrom(), message.getTo());
        peersInformation.getOrRegisterPeer(peer);
        state.onNewChunk();

        RLPList nodeLists = RLP.decodeList(message.getChunkOfTrieKeyValue());
        final RLPList preRootElements = RLP.decodeList(nodeLists.get(0).getRLPData());
        final RLPList trieElements = RLP.decodeList(nodeLists.get(1).getRLPData());
        byte[] firstNodeLeftHash = nodeLists.get(2).getRLPData();
        final RLPList lastNodeHashes = RLP.decodeList(nodeLists.get(3).getRLPData());
        final RLPList postRootElements = RLP.decodeList(nodeLists.get(4).getRLPData());
        List<TrieDTO> preRootNodes = new ArrayList<>();
        List<TrieDTO> nodes = new ArrayList<>();
        List<TrieDTO> postRootNodes = new ArrayList<>();


        for (int i = 0; i < preRootElements.size(); i++) {
            final RLPList trieElement = (RLPList) preRootElements.get(i);
            final byte[] value = trieElement.get(0).getRLPData();
            final byte[] leftHash = trieElement.get(1).getRLPData();
            TrieDTO node = TrieDTO.decodeFromSync(value);
            node.setLeftHash(leftHash);
            preRootNodes.add(node);
        }

        if (trieElements.size() > 0) {
            for (int i = 0; i < trieElements.size(); i++) {
                final RLPElement trieElement = trieElements.get(i);
                byte[] value = trieElement.getRLPData();
                nodes.add(TrieDTO.decodeFromSync(value));
            }
            nodes.get(0).setLeftHash(firstNodeLeftHash);
        }

        if (lastNodeHashes.size() > 0) {
            TrieDTO lastNode = nodes.get(nodes.size() - 1);
            lastNode.setLeftHash(lastNodeHashes.get(0).getRLPData());
            lastNode.setRightHash(lastNodeHashes.get(1).getRLPData());
        }

        for (int i = 0; i < postRootElements.size(); i++) {
            final RLPList trieElement = (RLPList) postRootElements.get(i);
            final byte[] value = trieElement.get(0).getRLPData();
            final byte[] rightHash = trieElement.get(1).getRLPData();
            TrieDTO node = TrieDTO.decodeFromSync(value);
            node.setRightHash(rightHash);
            postRootNodes.add(node);
        }

        logger.debug("dummy INSIDE processOrderedStateChunkResponse    _  !message.isComplete()   "+ !message.isComplete());
        if (TrieDTOInOrderRecoverer.verifyChunk(state.getRemoteRootHash(), preRootNodes, nodes, postRootNodes)) {
            state.getAllNodes().addAll(nodes);
            state.setStateSize(state.getStateSize().add(BigInteger.valueOf(trieElements.size())));
            state.setStateChunkSize(state.getStateChunkSize().add(BigInteger.valueOf(message.getChunkOfTrieKeyValue().length)));
            if (!message.isComplete()) {
                executeNextChunkRequestTask(state, peer);
            } else {
                boolean result = rebuildStateAndSave(state);
                logger.debug("dummy -  SNAPSHOT SYNC FINISHED");
                logger.info("CLIENT - Snapshot sync finished {}! ", result ? "successfully" : "with errors");
                stopSyncing(state);
            }
        } else {
            logger.error("Error while verifying chunk response: {}", message);
            throw new Exception("Error verifying chunk.");
        }
    }

    /**
     * Once state share is received, rebuild the trie, save it in db and save all the blocks.
     */
    private boolean rebuildStateAndSave(SnapSyncState state) {
        logger.info("CLIENT - Recovering trie...");
        final TrieDTO[] nodeArray = state.getAllNodes().toArray(new TrieDTO[0]);
        Optional<TrieDTO> result = TrieDTOInOrderRecoverer.recoverTrie(nodeArray, this.trieStore::saveDTO);

        if (result.isPresent() && Arrays.equals(state.getRemoteRootHash(), result.get().calculateHash())) {
            logger.info("CLIENT - State final validation OK!");

            this.blockchain.removeBlocksByNumber(0);
            //genesis is removed so backwards sync will always start.

            BlockConnectorHelper blockConnector = new BlockConnectorHelper(this.blockStore);
            state.connectBlocks(blockConnector);
            logger.info("CLIENT - Setting last block as best block...");
            this.blockchain.setStatus(state.getLastBlock(), state.getLastBlockDifficulty());
            this.transactionPool.setBestBlock(state.getLastBlock());
            return true;
        }
        logger.error("CLIENT - State final validation FAILED");
        return false;
    }

    private void generateChunkRequestTasks(SnapSyncState state) {
        long from = 0;
        logger.debug("Generating chunk request tasks... chunksize {}", chunkSize);
        while (from < state.getRemoteTrieSize()) {
            ChunkTask task = new ChunkTask(state.getLastBlock().getNumber(), from);
            state.getChunkTaskQueue().add(task);
            from += chunkSize * CHUNK_ITEM_SIZE;
        }
    }

    private void startRequestingChunks(SnapSyncState state) {
        List<Peer> bestPeerCandidates = peersInformation.getBestSnapPeerCandidates();
        List<Peer> peerList = bestPeerCandidates.subList(0, !parallel ? 1 : bestPeerCandidates.size());
        for (Peer peer : peerList) {
            executeNextChunkRequestTask(state, peer);
        }
    }

    private void executeNextChunkRequestTask(SnapSyncState state, Peer peer) {
        Queue<ChunkTask> taskQueue = state.getChunkTaskQueue();
        if (!taskQueue.isEmpty()) {
            ChunkTask task = taskQueue.poll();

            requestStateChunk(peer, task.getFrom(), task.getBlockNumber(), chunkSize);
        } else {
            logger.warn("No more chunk request tasks.");
        }
    }

    private static byte[] getBytes(byte[] result) {
        return result != null ? result : new byte[0];
    }

    @Override
    public void start() {
        if (isRunning != null) {
            logger.warn("Invalid state, isRunning: [{}]", isRunning);
            return;
        }

        isRunning = Boolean.TRUE;
        thread.start();
    }

    @Override
    public void stop() {
        if (isRunning != Boolean.TRUE) {
            logger.warn("Invalid state, isRunning: [{}]", isRunning);
            return;
        }

        isRunning = Boolean.FALSE;
        thread.interrupt();
    }
}
