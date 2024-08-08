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
import org.apache.commons.lang3.tuple.Pair;
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
        List<Block> blocks = Lists.newArrayList();
        List<BlockDifficulty> difficulties = Lists.newArrayList();
        for (long i = checkpointBlockNumber - BLOCK_CHUNK_SIZE; i < checkpointBlockNumber; i++) {
            Block block = blockchain.getBlockByNumber(i);
            blocks.add(block);
            difficulties.add(blockStore.getTotalDifficultyForHash(block.getHash().getBytes()));
        }

        Block checkpointBlock = blockchain.getBlockByNumber(checkpointBlockNumber);
        blocks.add(checkpointBlock);
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
        List<Pair<Block, BlockDifficulty>> blocks = state.getBlocks();

        for (int i = 0; i < blocksFromResponse.size(); i++) {
            blocks.add(new ImmutablePair<>(blocksFromResponse.get(i), difficultiesFromResponse.get(i)));
        }
        logger.debug("CLIENT - Processing snapshot status response - last blockNumber: {} rootHash: {} triesize: {}", lastBlock.getNumber(), state.getRemoteRootHash(), state.getRemoteTrieSize());
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
        for (long i = requestMessage.getBlockNumber() - BLOCK_CHUNK_SIZE; i < requestMessage.getBlockNumber(); i++) {
            Block block = blockchain.getBlockByNumber(i);
            blocks.add(block);
            difficulties.add(blockStore.getTotalDifficultyForHash(block.getHash().getBytes()));
        }
        SnapBlocksResponseMessage responseMessage = new SnapBlocksResponseMessage(blocks, difficulties);
        sender.sendMessage(responseMessage);
    }

    public void processSnapBlocksResponse(SnapSyncState state, Peer sender, SnapBlocksResponseMessage responseMessage) {
        logger.debug("CLIENT - Processing snap blocks response");
        List<Block> blocksFromResponse = responseMessage.getBlocks();
        List<BlockDifficulty> difficultiesFromResponse = responseMessage.getDifficulties();
        for (int i = 0; i < blocksFromResponse.size(); i++) {
            state.getBlocks().add(new ImmutablePair<>(blocksFromResponse.get(i), difficultiesFromResponse.get(i)));
        }
        long nextChunk = blocksFromResponse.get(0).getNumber();
        if (nextChunk > state.getLastBlock().getNumber() - BLOCKS_REQUIRED) {
            requestBlocksChunk(sender, nextChunk);
        } else {
            logger.info("CLIENT - Finished Snap blocks sync.");
        }
    }

    /**
     * STATE CHUNK
     */
    private void requestStateChunk(Peer peer, long from, long blockNumber, int chunkSize) {
        logger.debug("CLIENT - Requesting state chunk to node {} - block {} - from {}", peer.getPeerNodeID(), blockNumber, from);
        SnapStateChunkRequestMessage message = new SnapStateChunkRequestMessage(messageId++, blockNumber, from, chunkSize);
        peer.sendMessage(message);
        logger.debug("CLIENT - Request sent state chunk to node {} - block {} - from {}", peer.getPeerNodeID(), blockNumber, from);
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
        logger.debug("SERVER - Processing state chunk request from node {}", sender.getPeerNodeID());
        List<byte[]> trieEncoded = new ArrayList<>();
        Block block = blockchain.getBlockByNumber(request.getBlockNumber());
        final long to = request.getFrom() + (request.getChunkSize() * 1024);
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
        sender.sendMessage(responseMessage);
    }

    public void processStateChunkResponse(SnapSyncState state, Peer peer, SnapStateChunkResponseMessage responseMessage) {
        logger.debug("CLIENT - State chunk received from: {}", responseMessage.getFrom());

        PriorityQueue<SnapStateChunkResponseMessage> queue = state.getSnapStateChunkQueue();
        queue.add(responseMessage);

        while (!queue.isEmpty()) {
            SnapStateChunkResponseMessage nextMessage = queue.peek();
            long nextExpectedFrom = state.getNextExpectedFrom();
            logger.debug("CLIENT - State chunk dequeued from: {} - expected: {}", nextMessage.getFrom(), nextExpectedFrom);
            if (nextMessage.getFrom() == nextExpectedFrom) {
                processOrderedStateChunkResponse(state, peer, queue.poll());
                state.setNextExpectedFrom(nextExpectedFrom + chunkSize * 1024L);
            } else {
                break;
            }
        }

        if (!responseMessage.isComplete()) {
            executeNextChunkRequestTask(state, peer);
        }
    }

    private void processOrderedStateChunkResponse(SnapSyncState state, Peer peer, SnapStateChunkResponseMessage message) {
        try {
            logger.debug("CLIENT - Processing State chunk received from: {}", message.getFrom());
            peersInformation.getOrRegisterPeer(peer);
            state.onNewChunk();

            RLPList nodeLists = RLP.decodeList(message.getChunkOfTrieKeyValue());
            final RLPList preRootElements = RLP.decodeList(nodeLists.get(0).getRLPData());
            final RLPList trieElements = RLP.decodeList(nodeLists.get(1).getRLPData());
            byte[] firstNodeLeftHash = nodeLists.get(2).getRLPData();
            final RLPList lastNodeHashes = RLP.decodeList(nodeLists.get(3).getRLPData());
            final RLPList postRootElements = RLP.decodeList(nodeLists.get(4).getRLPData());
            logger.debug(
                    "CLIENT - Received state chunk of {} elements ({} bytes).",
                    trieElements.size(),
                    message.getChunkOfTrieKeyValue().length
            );
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

            if (TrieDTOInOrderRecoverer.verifyChunk(state.getRemoteRootHash(), preRootNodes, nodes, postRootNodes)) {
                state.getAllNodes().addAll(nodes);
                state.setStateSize(state.getStateSize().add(BigInteger.valueOf(trieElements.size())));
                state.setStateChunkSize(state.getStateChunkSize().add(BigInteger.valueOf(message.getChunkOfTrieKeyValue().length)));
                logger.debug("CLIENT - State progress: {} chunks ({} bytes)", state.getStateSize(), state.getStateChunkSize());
                if (!message.isComplete()) {
                    executeNextChunkRequestTask(state, peer);
                } else {
                    rebuildStateAndSave(state);
                    logger.info("CLIENT - Snapshot sync finished!");
                    stopSyncing(state);
                }
            } else {
                logger.error("Error while verifying chunk response: {}", message);
                throw new Exception("Error verifying chunk.");
            }
        } catch (Exception e) {
            logger.error("Error while processing chunk response.", e);
        }
    }

    /**
     * Once state share is received, rebuild the trie, save it in db and save all the blocks.
     */
    private void rebuildStateAndSave(SnapSyncState state) {
        logger.debug("CLIENT - State Completed! {} chunks ({} bytes) - chunk size = {}",
                state.getStateSize(), state.getStateChunkSize(), this.chunkSize);
        final TrieDTO[] nodeArray = state.getAllNodes().toArray(new TrieDTO[0]);
        logger.debug("CLIENT - Recovering trie...");
        Optional<TrieDTO> result = TrieDTOInOrderRecoverer.recoverTrie(nodeArray, this.trieStore::saveDTO);
        if (!result.isPresent() || !Arrays.equals(state.getRemoteRootHash(), result.get().calculateHash())) {
            logger.error("CLIENT - State final validation FAILED");
        } else {
            logger.debug("CLIENT - State final validation OK!");
        }

        logger.debug("CLIENT - Saving previous blocks...");
        this.blockchain.removeBlocksByNumber(0);
        BlockConnectorHelper blockConnector = new BlockConnectorHelper(this.blockStore, state.getBlocks());
        blockConnector.startConnecting();
        logger.debug("CLIENT - Setting last block as best block...");
        this.blockchain.setStatus(state.getLastBlock(), state.getLastBlockDifficulty());
        this.transactionPool.setBestBlock(state.getLastBlock());
    }

    private void generateChunkRequestTasks(SnapSyncState state) {
        long from = 0;
        logger.debug("Generating chunk request tasks...");
        while (from < state.getRemoteTrieSize()) {
            ChunkTask task = new ChunkTask(state.getLastBlock().getNumber(), from);
            state.getChunkTaskQueue().add(task);
            from += chunkSize * 1024L;
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
