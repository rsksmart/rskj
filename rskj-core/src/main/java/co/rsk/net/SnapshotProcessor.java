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
import co.rsk.core.types.bytes.Bytes;
import co.rsk.crypto.Keccak256;
import co.rsk.metrics.profilers.MetricKind;
import co.rsk.net.messages.*;
import co.rsk.net.sync.*;
import co.rsk.scoring.EventType;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieDTOInOrderIterator;
import co.rsk.trie.TrieDTOInOrderRecoverer;
import co.rsk.trie.TrieStore;
import co.rsk.util.HexUtils;
import co.rsk.validators.BlockHeaderParentDependantValidationRule;
import co.rsk.validators.BlockHeaderValidationRule;
import co.rsk.validators.BlockParentDependantValidationRule;
import co.rsk.validators.BlockValidationRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static co.rsk.net.sync.SnapSyncRequestManager.PeerSelector;

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
    public static final String TMP_NODES_DIR_NAME = "snapSyncTmpNodes";
    public static final String TMP_CHUNKS_TESTING_DATA_FILE_NAME_WITH_EXTENSION = "chunksData.json";
    private final Blockchain blockchain;
    private final TrieStore trieStore;
    private final KeyValueDataSource tmpSnapSyncKeyValueDataSource;
    private final String databasedir;
    private final Path pathToChunksFile;
    private static final int TMP_NODES_SIZE_KEY = -1;
    private final BlockStore blockStore;
    private final int chunkSize;
    private final int checkpointDistance;
    private final SnapshotPeersInformation peersInformation;
    private final TransactionPool transactionPool;

    private final BlockParentDependantValidationRule blockParentValidator;
    private final BlockValidationRule blockValidator;

    private final BlockHeaderParentDependantValidationRule blockHeaderParentValidator;
    private final BlockHeaderValidationRule blockHeaderValidator;

    private final boolean checkHistoricalHeaders;
    // flag for parallel requests
    private final boolean parallel;

    private final int maxSenderRequests;
    private final BlockingQueue<SyncMessageHandler.Job> requestQueue = new LinkedBlockingQueue<>();

    private volatile Boolean isRunning;
    private final Thread thread;

    public SnapshotProcessor(Blockchain blockchain,
                             TrieStore trieStore,
                             SnapshotPeersInformation peersInformation,
                             BlockStore blockStore,
                             TransactionPool transactionPool,
                             BlockParentDependantValidationRule blockParentValidator,
                             BlockValidationRule blockValidator,
                             BlockHeaderParentDependantValidationRule blockHeaderParentValidator,
                             BlockHeaderValidationRule blockHeaderValidator,
                             int chunkSize,
                             int checkpointDistance,
                             int maxSenderRequests,
                             boolean checkHistoricalHeaders,
                             boolean isParallelEnabled,
                             KeyValueDataSource tmpSnapSyncKeyValueDataSource,
                             String databasedir) {
        this(blockchain, trieStore, peersInformation, blockStore, transactionPool,
                blockParentValidator, blockValidator, blockHeaderParentValidator, blockHeaderValidator,
                chunkSize, checkpointDistance, maxSenderRequests, checkHistoricalHeaders, isParallelEnabled, null, tmpSnapSyncKeyValueDataSource, databasedir);
    }

    @VisibleForTesting
    SnapshotProcessor(Blockchain blockchain,
                      TrieStore trieStore,
                      SnapshotPeersInformation peersInformation,
                      BlockStore blockStore,
                      TransactionPool transactionPool,
                      BlockParentDependantValidationRule blockParentValidator,
                      BlockValidationRule blockValidator,
                      BlockHeaderParentDependantValidationRule blockHeaderParentValidator,
                      BlockHeaderValidationRule blockHeaderValidator,
                      int chunkSize,
                      int checkpointDistance,
                      int maxSenderRequests,
                      boolean checkHistoricalHeaders,
                      boolean isParallelEnabled,
                      @Nullable SyncMessageHandler.Listener listener,
                      KeyValueDataSource tmpSnapSyncKeyValueDataSource,
                      String databasedir) {
        this.blockchain = blockchain;
        this.trieStore = trieStore;
        this.tmpSnapSyncKeyValueDataSource = tmpSnapSyncKeyValueDataSource;
        this.databasedir = databasedir;
        final var databasePath = Paths.get(databasedir);
        final var tmpDatabasePath = databasePath.resolve(SnapshotProcessor.TMP_NODES_DIR_NAME);
        this.pathToChunksFile = tmpDatabasePath.resolve(SnapshotProcessor.TMP_CHUNKS_TESTING_DATA_FILE_NAME_WITH_EXTENSION);
        this.peersInformation = peersInformation;
        this.chunkSize = chunkSize;
        this.checkpointDistance = checkpointDistance;
        this.maxSenderRequests = maxSenderRequests;
        this.blockStore = blockStore;
        this.transactionPool = transactionPool;

        this.blockParentValidator = blockParentValidator;
        this.blockValidator = blockValidator;

        this.blockHeaderParentValidator = blockHeaderParentValidator;
        this.blockHeaderValidator = blockHeaderValidator;

        this.checkHistoricalHeaders = checkHistoricalHeaders;
        this.parallel = isParallelEnabled;
        this.thread = new Thread(new SyncMessageHandler("SNAP/server", this.requestQueue, listener) {

            @Override
            public boolean isRunning() {
                return isRunning;
            }
        }, "snap sync server handler");
    }

    public void startSyncing(SnapSyncState state) {
        Optional<Peer> bestPeerOpt = peersInformation.getBestSnapPeer();
        if (bestPeerOpt.isEmpty()) {
            logger.warn("No snap-capable peer to start sync against");
            stopSyncing(state);
            return;
        }

        logger.info("Starting Snap sync");
        requestSnapStatus(state, bestPeerOpt.get());
    }

    private void completeSyncing(SnapSyncState state) {
        boolean result = rebuildStateAndSave(state);
        logger.info("Snap sync finished {}", result ? "successfully" : "with errors");
        stopSyncing(state);
    }

    private void stopSyncing(SnapSyncState state) {
        state.finish();
    }

    private void failSyncing(SnapSyncState state, Peer peer, EventType eventType, String message, Object... arguments) {
        state.fail(peer, eventType, message, arguments);
    }

    /**
     * STATUS
     */
    private void requestSnapStatus(SnapSyncState state, Peer peer) {
        state.submitRequest(snapPeerSelector(peer), SnapStatusRequestMessage::new);
    }

    private PeerSelector peerSelector(@Nullable Peer peer) {
        return PeerSelector.builder()
                .withDefaultPeer(() -> peer)
                .withAltPeer(peersInformation::getBestPeer)
                .build();
    }

    private PeerSelector snapPeerSelector(@Nullable Peer snapPeer) {
        return PeerSelector.builder()
                .withDefaultPeer(() -> snapPeer)
                .withAltPeer(peersInformation::getBestSnapPeer)
                .build();
    }

    public void processSnapStatusRequest(Peer sender, SnapStatusRequestMessage requestMessage) {
        if (isRunning != Boolean.TRUE) {
            logger.warn("processSnapStatusRequest: invalid state, isRunning: [{}]", isRunning);
            return;
        }

        scheduleJob(new SyncMessageHandler.Job(sender, requestMessage, MetricKind.SNAP_STATUS_REQUEST) {
            @Override
            public void run() {
                processSnapStatusRequestInternal(sender, requestMessage);
            }
        });
    }

    void processSnapStatusRequestInternal(Peer sender, SnapStatusRequestMessage requestMessage) {
        long bestBlockNumber = blockchain.getBestBlock().getNumber();
        long checkpointBlockNumber = Math.max(0, bestBlockNumber - checkpointDistance);
        long lowerBlockNumberToRetrieve = Math.max(0, checkpointBlockNumber - BLOCK_CHUNK_SIZE);
        logger.debug("Processing snapshot status request, checkpointBlockNumber: {}, bestBlockNumber: {}", checkpointBlockNumber, bestBlockNumber);

        LinkedList<Block> blocks = new LinkedList<>();
        LinkedList<BlockDifficulty> difficulties = new LinkedList<>();

        retrieveBlocksAndDifficultiesBackwards(lowerBlockNumberToRetrieve,checkpointBlockNumber, blocks, difficulties);

        Block currentBlock = blocks.getLast();
        byte[] rootHash = currentBlock.getStateRoot();
        Optional<TrieDTO> opt = trieStore.retrieveDTO(rootHash);
        if (opt.isEmpty()) {
            logger.warn("Trie is not present for rootHash: {}", Bytes.of(rootHash));
            // Handle the error appropriately, e.g., send an error response or throw an exception
            return;
        }
        long trieSize = opt.get().getTotalSize();
        logger.debug("Processing snapshot status request - rootHash: {} trieSize: {}", rootHash, trieSize);

        SnapStatusResponseMessage responseMessage = new SnapStatusResponseMessage(requestMessage.getId(), blocks, difficulties, trieSize);
        sender.sendMessage(responseMessage);
    }

    public void processSnapStatusResponse(SnapSyncState state, Peer sender, SnapStatusResponseMessage responseMessage) {
        if (!state.isRunning()) {
            return;
        }

        List<Block> blocksFromResponse = responseMessage.getBlocks();
        List<BlockDifficulty> difficultiesFromResponse = responseMessage.getDifficulties();
        if (blocksFromResponse.size() != difficultiesFromResponse.size()) {
            failSyncing(state, sender, EventType.INVALID_BLOCK, "Blocks and difficulties size mismatch. Blocks: [{}], Difficulties: [{}]", blocksFromResponse.size(), difficultiesFromResponse.size());
            return;
        }

        Block lastBlock = blocksFromResponse.get(blocksFromResponse.size() - 1);
        BlockDifficulty lastBlockDifficulty = difficultiesFromResponse.get(difficultiesFromResponse.size() - 1);

        state.setLastBlock(lastBlock, lastBlockDifficulty, sender);
        state.setRemoteRootHash(lastBlock.getStateRoot());
        state.setRemoteTrieSize(responseMessage.getTrieSize());

        if (!validateAndSaveBlocks(state, sender, blocksFromResponse, difficultiesFromResponse)) {
            return;
        }

        logger.debug("Processing snapshot status response: {} from {} to {} - last blockNumber: {} trieSize: {}", blocksFromResponse.size(), blocksFromResponse.get(0).getNumber(), blocksFromResponse.get(blocksFromResponse.size() - 1).getNumber(), lastBlock.getNumber(), state.getRemoteTrieSize());

        if (blocksVerified(state)) {
            logger.info("Finished Snap blocks request sending");

            generateChunkRequestTasks(state);
            startRequestingChunks(state);
        } else {
            requestBlocksChunk(state, blocksFromResponse.get(0).getNumber());
        }
    }

    private boolean validateAndSaveBlocks(SnapSyncState state, Peer sender, List<Block> blocks, List<BlockDifficulty> difficulties) {
        Pair<Block, BlockDifficulty> childBlockPair = state.getLastBlockPair();
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Block block = blocks.get(i);
            BlockDifficulty totalDifficulty = difficulties.get(i);

            Pair<Block, BlockDifficulty> blockPair = new ImmutablePair<>(block, totalDifficulty);
            if (!areBlockPairsValid(blockPair, childBlockPair, true)) {
                failSyncing(state, sender, EventType.INVALID_BLOCK, "Block [{}]/[{}] at height: [{}] is not valid", block.getHash(), totalDifficulty, block.getNumber());
                return false;
            }

            Block parentBlock = blockStore.getBlockByHash(block.getParentHash().getBytes());
            if (parentBlock != null) {
                if (areBlockPairsValid(new ImmutablePair<>(parentBlock, blockStore.getTotalDifficultyForBlock(parentBlock)), blockPair, false)) {
                    state.addBlock(blockPair);
                    state.setLastVerifiedBlockHeader(blockPair.getLeft().getHeader());
                    return true;
                } else {
                    failSyncing(state, sender, EventType.INVALID_BLOCK, "Block [{}]/[{}] at height: [{}] is not valid", block.getHash(), totalDifficulty, block.getNumber());
                    return false;
                }
            }

            state.addBlock(blockPair);
            state.setLastVerifiedBlockHeader(blockPair.getLeft().getHeader());

            childBlockPair = blockPair;
        }

        return true;
    }

    private boolean areBlockPairsValid(Pair<Block, BlockDifficulty> blockPair, @Nullable Pair<Block, BlockDifficulty> childBlockPair, boolean validateParent) {
        if (validateParent && !blockValidator.isValid(blockPair.getLeft())) {
            return false;
        }

        if (childBlockPair == null) {
            return true;
        }

        if (!blockPair.getLeft().isParentOf(childBlockPair.getLeft())
                || !blockPair.getRight().equals(childBlockPair.getRight().subtract(childBlockPair.getLeft().getCumulativeDifficulty()))) {
            return false;
        }

        return blockParentValidator.isValid(childBlockPair.getLeft(), blockPair.getLeft());
    }

    /**
     * BLOCK CHUNK
     */
    private void requestBlocksChunk(SnapSyncState state, long blockNumber) {
        state.submitRequest(
                peerSelector(null),
                messageId -> new SnapBlocksRequestMessage(messageId, blockNumber)
        );
    }

    public void processBlockHeaderChunk(SnapSyncState state, Peer sender, List<BlockHeader> chunk) {
        if (!state.isRunning()) {
            return;
        }

        logger.debug("Processing block headers response - chunk: [{}; {}]", chunk.get(0).getNumber(), chunk.get(chunk.size() - 1).getNumber());

        if (!validateBlockHeaders(state, sender, chunk)) {
            state.fail(sender, EventType.INVALID_HEADER, "Invalid block headers received");
            return;
        }

        if (blocksVerified(state)) {
            if (state.isStateFetched()) {
                completeSyncing(state);
            }
        } else {
            requestNextBlockHeadersChunk(state, sender);
        }
    }

    private boolean validateBlockHeaders(SnapSyncState state, Peer sender, List<BlockHeader> blockHeaders) {
        for (int i = 0; i < blockHeaders.size(); i++) {
            BlockHeader blockHeader = blockHeaders.get(i);
            BlockHeader lastVerifiedBlockHeader = state.getLastVerifiedBlockHeader();

            if (!areBlockHeadersValid(blockHeader, lastVerifiedBlockHeader, true)) {
                failSyncing(state, state.getLastBlockSender(), EventType.INVALID_HEADER, "Block header [{}] at height: [{}] is not valid", blockHeader.getHash(), blockHeader.getNumber());
                return false;
            }

            Block parentBlock = blockStore.getBlockByHash(blockHeader.getParentHash().getBytes());
            if (parentBlock != null && !areBlockHeadersValid(parentBlock.getHeader(), blockHeader, false)) {
                failSyncing(state, state.getLastBlockSender(), EventType.INVALID_HEADER, "Block header [{}] at height: [{}] is not valid", blockHeader.getHash(), blockHeader.getNumber());
                return false;
            }

            state.setLastVerifiedBlockHeader(blockHeader);

            if (blocksVerified(state)) {
                return true;
            }
        }

        return true;
    }

    private boolean areBlockHeadersValid(BlockHeader blockHeader, BlockHeader childBlockHeader, boolean validateParent) {
        if (validateParent && !blockHeaderValidator.isValid(blockHeader)) {
            return false;
        }

        if (!blockHeader.isParentOf(childBlockHeader)) {
            return false;
        }

        return blockHeaderParentValidator.isValid(childBlockHeader, blockHeader);
    }

    /**
     * BLOCK HEADER CHUNK
     */
    private void requestNextBlockHeadersChunk(SnapSyncState state, Peer sender) {
        Peer peer = peersInformation.getBestPeer(Collections.singleton(state.getLastBlockSender().getPeerNodeID()))
                .orElse(peersInformation.getBestSnapPeer().orElse(sender));

        BlockHeader lastVerifiedBlockHeader = state.getLastVerifiedBlockHeader();
        Keccak256 parentHash = lastVerifiedBlockHeader.getParentHash();
        if (blockStore.isBlockExist(parentHash.getBytes())) {
            logger.error("No more block headers to request");
            stopSyncing(state);
            return;
        }
        long count = Math.min(state.getBlockHeaderChunkSize(), lastVerifiedBlockHeader.getNumber() - 1);
        if (count < 1) {
            logger.info("No more block headers to request but no genesis found");
            state.fail(state.getLastBlockSender(), EventType.INVALID_HEADER, "Invalid block headers genesis block");
            return;
        }

        logger.debug("Requesting block header chunk to node {} - block [{}/{}]", peer.getPeerNodeID(), lastVerifiedBlockHeader.getNumber() - 1, parentHash);

        state.submitRequest(
                peerSelector(sender),
                messageId -> new BlockHeadersRequestMessage(messageId, parentHash.getBytes(), (int) count)
        );
    }

    public void processSnapBlocksRequest(Peer sender, SnapBlocksRequestMessage requestMessage) {
        if (isRunning != Boolean.TRUE) {
            logger.warn("processSnapBlocksRequest: invalid state, isRunning: [{}]", isRunning);
            return;
        }

        scheduleJob(new SyncMessageHandler.Job(sender, requestMessage, MetricKind.SNAP_BLOCKS_REQUEST) {
            @Override
            public void run() {
                processSnapBlocksRequestInternal(sender, requestMessage);
            }
        });
    }

    void processSnapBlocksRequestInternal(Peer sender, SnapBlocksRequestMessage requestMessage) {
        logger.debug("Processing snap blocks request");
        LinkedList<Block> blocks = new LinkedList<>();
        LinkedList<BlockDifficulty> difficulties = new LinkedList<>();

        long requestBlockNumber = requestMessage.getBlockNumber();
        if (requestBlockNumber < 2 || requestBlockNumber > blockchain.getBestBlock().getNumber()) {
            logger.debug("Snap blocks request from {} failed because of invalid block number {}", sender.getPeerNodeID(), requestBlockNumber);
            return;
        }

        long fromBlock = Math.max(1, requestMessage.getBlockNumber() - BLOCK_CHUNK_SIZE);
        retrieveBlocksAndDifficultiesBackwards(fromBlock,requestBlockNumber-1, blocks, difficulties);
        logger.debug("Sending snap blocks response. From block {} to block {} - chunksize {}", blocks.get(0).getNumber(), blocks.get(blocks.size() - 1).getNumber(), BLOCK_CHUNK_SIZE);
        SnapBlocksResponseMessage responseMessage = new SnapBlocksResponseMessage(requestMessage.getId(), blocks, difficulties);
        sender.sendMessage(responseMessage);
    }

    private void retrieveBlocksAndDifficultiesBackwards(long fromBlock, long toBlock, LinkedList<Block> blocks, LinkedList<BlockDifficulty> difficulties) {
        Block currentBlock = blockchain.getBlockByNumber(toBlock);
        while (currentBlock != null && currentBlock.getNumber() >= fromBlock) {
            blocks.addFirst(currentBlock);
            difficulties.addFirst(blockStore.getTotalDifficultyForBlock(currentBlock));
            currentBlock = blockStore.getBlockByHash(currentBlock.getParentHash().getBytes());
        }
        if (currentBlock == null) {
            logger.warn("No block found for block number {}", currentBlock);
        }
    }

    public void processSnapBlocksResponse(SnapSyncState state, Peer sender, SnapBlocksResponseMessage responseMessage) {
        if (!state.isRunning()) {
            return;
        }

        long lastRequiredBlock = state.getLastBlock().getNumber() - BLOCKS_REQUIRED;
        List<Block> blocksFromResponse = responseMessage.getBlocks();
        logger.debug("Processing snap blocks response. Receiving from block {} to block {} Objective: {}.", blocksFromResponse.get(0).getNumber(), blocksFromResponse.get(blocksFromResponse.size() - 1).getNumber(), lastRequiredBlock);
        List<BlockDifficulty> difficultiesFromResponse = responseMessage.getDifficulties();

        if (!validateAndSaveBlocks(state, sender, blocksFromResponse, difficultiesFromResponse)) {
            return;
        }

        long nextChunk = blocksFromResponse.get(0).getNumber();
        logger.debug("SnapBlock - nexChunk : {} - lastRequired {}, missing {}", nextChunk, lastRequiredBlock, nextChunk - lastRequiredBlock);

        if (blocksVerified(state)) {
            logger.info("Finished Snap blocks request sending. Start requesting state chunks");

            generateChunkRequestTasks(state);
            startRequestingChunks(state);
        } else if (nextChunk > lastRequiredBlock) {
            requestBlocksChunk(state, nextChunk);
        } else if (!this.checkHistoricalHeaders) {
            logger.info("Finished Snap blocks request sending. Start requesting state chunks without historical headers check");

            generateChunkRequestTasks(state);
            startRequestingChunks(state);
        } else {
            logger.info("Finished Snap blocks request sending. Start requesting state chunks and block headers");

            generateChunkRequestTasks(state);
            startRequestingChunks(state);

            requestNextBlockHeadersChunk(state, sender);
        }
    }

    /**
     * STATE CHUNK
     */
    private void requestStateChunk(SnapSyncState state, Peer peer, long from, long blockNumber, int chunkSize) {
        state.submitRequest(
                snapPeerSelector(peer),
                messageId -> new SnapStateChunkRequestMessage(messageId, blockNumber, from, chunkSize)
        );
    }

    public void processStateChunkRequest(Peer sender, SnapStateChunkRequestMessage requestMessage) {
        if (isRunning != Boolean.TRUE) {
            logger.warn("processStateChunkRequest: invalid state, isRunning: [{}]", isRunning);
            return;
        }

        scheduleJob(new SyncMessageHandler.Job(sender, requestMessage, MetricKind.SNAP_STATE_CHUNK_REQUEST) {
            @Override
            public void run() {
                processStateChunkRequestInternal(sender, requestMessage);
            }
        });
    }

    void processStateChunkRequestInternal(Peer sender, SnapStateChunkRequestMessage request) {
        long startChunk = System.currentTimeMillis();

        List<byte[]> trieEncoded = new ArrayList<>();
        Block block = blockchain.getBlockByNumber(request.getBlockNumber());
        final long to = request.getFrom() + (request.getChunkSize() * CHUNK_ITEM_SIZE);
        logger.debug("Processing state chunk request from node {}. From {} to calculated {} being chunksize {}", sender.getPeerNodeID(), request.getFrom(), to, request.getChunkSize());
        logger.debug("Sending state chunk from {} to {}", request.getFrom(), to);
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

        logger.debug("Sending state chunk from {} of {} bytes to node {}, totalTime {}ms", request.getFrom(), chunkBytes.length, sender.getPeerNodeID(), totalChunkTime);
        sender.sendMessage(responseMessage);
    }

    public void processStateChunkResponse(SnapSyncState state, Peer peer, SnapStateChunkResponseMessage responseMessage) {
        if (!state.isRunning()) {
            return;
        }

        logger.debug("State chunk received chunkNumber {}. From {} to {} of total size {}", responseMessage.getFrom() / CHUNK_ITEM_SIZE, responseMessage.getFrom(), responseMessage.getTo(), state.getRemoteTrieSize());

        try {
            final var chunksDataMap = new HashMap<String, String>();

            chunksDataMap.put("state.getNextExpectedFrom()", String.valueOf(state.getNextExpectedFrom()));
            chunksDataMap.put("state.getStateChunkSize()", state.getStateChunkSize().toString());
            chunksDataMap.put("state.getStateSize()", state.getStateSize().toString());
            chunksDataMap.put("state.getRemoteRootHash()", HexUtils.toJsonHex(state.getRemoteRootHash()));
            chunksDataMap.put("state.isRunning()", String.valueOf(state.isRunning()));

            chunksDataMap.put("responseMessage.getFrom()", String.valueOf(responseMessage.getFrom()));
            chunksDataMap.put("responseMessage.getTo()", String.valueOf(responseMessage.getTo()));
            chunksDataMap.put("responseMessage.getId()", String.valueOf(responseMessage.getId()));
            chunksDataMap.put("responseMessage.getBlockNumber()", String.valueOf(responseMessage.getBlockNumber()));
            chunksDataMap.put("responseMessage.getChunkOfTrieKeyValue()", HexUtils.toJsonHex(responseMessage.getChunkOfTrieKeyValue()));
            chunksDataMap.put("responseMessage.isComplete()", String.valueOf(responseMessage.isComplete()));

            final var writer = new BufferedWriter(new FileWriter(pathToChunksFile.toFile(), true));

            writer.append(new ObjectMapper().writeValueAsString(chunksDataMap));
            writer.append('\n');
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        PriorityQueue<SnapStateChunkResponseMessage> queue = state.getSnapStateChunkQueue();
        queue.add(responseMessage);

        while (!queue.isEmpty()) {
            SnapStateChunkResponseMessage nextMessage = queue.peek();
            long nextExpectedFrom = state.getNextExpectedFrom();
            logger.debug("State chunk dequeued from: {} - expected: {}", nextMessage.getFrom(), nextExpectedFrom);
            if (nextMessage.getFrom() == nextExpectedFrom) {
                try {
                    processOrderedStateChunkResponse(state, peer, queue.poll());
                    state.setNextExpectedFrom(nextExpectedFrom + chunkSize * CHUNK_ITEM_SIZE);
                } catch (Exception e) {
                    logger.error("Error while processing chunk response. {}", e.getMessage(), e);
                    onStateChunkResponseError(state, peer, nextMessage);
                }
            } else {
                break;
            }
        }

        if (!responseMessage.isComplete()) {
            logger.debug("State chunk response not complete. Requesting next chunk.");
            executeNextChunkRequestTask(state, peer);
        }
    }

    @VisibleForTesting
    void onStateChunkResponseError(SnapSyncState state, Peer peer, SnapStateChunkResponseMessage responseMessage) {
        logger.error("Error while processing chunk response from {} of peer {}. Asking for chunk again.", responseMessage.getFrom(), peer.getPeerNodeID());
        Peer alternativePeer = peersInformation.getBestSnapPeerCandidates().stream()
                .filter(listedPeer -> !listedPeer.getPeerNodeID().equals(peer.getPeerNodeID()))
                .findFirst()
                .orElse(peer);
        logger.debug("Requesting state chunk \"from\" {} to peer {}", responseMessage.getFrom(), peer.getPeerNodeID());
        requestStateChunk(state, alternativePeer, responseMessage.getFrom(), responseMessage.getBlockNumber(), chunkSize);
    }

    private void processOrderedStateChunkResponse(SnapSyncState state, Peer peer, SnapStateChunkResponseMessage message) throws Exception {
        logger.debug("Processing State chunk received from {} to {}", message.getFrom(), message.getTo());

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
            final var existingNodesSizeInBytes = tmpSnapSyncKeyValueDataSource.get(ByteUtil.intToBytes(TMP_NODES_SIZE_KEY));
            var existingNodesSize = 0;

            if(existingNodesSizeInBytes != null) {
                existingNodesSize = ByteUtil.byteArrayToInt(existingNodesSizeInBytes);
            }

            for (int i = 0; i < trieElements.size(); i++) {
                final RLPElement trieElement = trieElements.get(i);
                byte[] value = trieElement.getRLPData();
                nodes.add(TrieDTO.decodeFromSync(value));

                tmpSnapSyncKeyValueDataSource.put(ByteUtil.intToBytes(existingNodesSize + i), value);
            }
            nodes.get(0).setLeftHash(firstNodeLeftHash);

            tmpSnapSyncKeyValueDataSource.put(ByteUtil.intToBytes(TMP_NODES_SIZE_KEY), ByteUtil.intToBytes(nodes.size() + existingNodesSize));
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
            state.setStateSize(state.getStateSize().add(BigInteger.valueOf(trieElements.size())));
            state.setStateChunkSize(state.getStateChunkSize().add(BigInteger.valueOf(message.getChunkOfTrieKeyValue().length)));
            if (message.isComplete()) {
                if (!this.checkHistoricalHeaders || blocksVerified(state)) {
                    completeSyncing(state);
                } else {
                    state.setStateFetched();
                }
            }
        } else {
            logger.error("Error while verifying chunk response: {}", message);
            throw new Exception("Error verifying chunk.");
        }
    }

    private boolean blocksVerified(SnapSyncState state) {
        BlockHeader lastVerifiedBlockHeader = state.getLastVerifiedBlockHeader();
        return lastVerifiedBlockHeader != null && blockStore.isBlockExist(lastVerifiedBlockHeader.getParentHash().getBytes());
    }

    private void moveNodesToTrie(TrieDTO node, int index) {
        trieStore.saveDTO(node);
    }

    TrieDTO getNodeFromTmpSnapSyncKeyValueDataSource(int index) {
        final var nodeInBytes = tmpSnapSyncKeyValueDataSource.get(ByteUtil.intToBytes(index));
        return TrieDTO.decodeFromSync(nodeInBytes);
    }

    /**
     * Once state share is received, rebuild the trie, save it in db and save all the blocks.
     */
    private boolean rebuildStateAndSave(SnapSyncState state) {
        logger.info("Recovering trie...");
        final var existingNodesSizeInBytes = tmpSnapSyncKeyValueDataSource.get(ByteUtil.intToBytes(TMP_NODES_SIZE_KEY));
        final var existingNodesSize = ByteUtil.byteArrayToInt(existingNodesSizeInBytes);
        final var response = TrieDTOInOrderRecoverer.recoverTrie(this::getNodeFromTmpSnapSyncKeyValueDataSource, existingNodesSize, this::moveNodesToTrie);
        final var result = response.node();

        if (result.isEmpty() || !Arrays.equals(state.getRemoteRootHash(), result.get().calculateHash())) {
            logger.error("State final validation FAILED");

            return false;
        }

        for (int i = 0; i < existingNodesSize; i++) {
            tmpSnapSyncKeyValueDataSource.delete(ByteUtil.intToBytes(i));
        }

        tmpSnapSyncKeyValueDataSource.delete(ByteUtil.intToBytes(TMP_NODES_SIZE_KEY));

        logger.info("State final validation OK!");

        this.blockchain.removeBlocksByNumber(0);
        //genesis is removed so backwards sync will always start.

        BlockConnectorHelper blockConnector = new BlockConnectorHelper(this.blockStore);
        state.connectBlocks(blockConnector);
        logger.info("Setting last block as best block...");
        this.blockchain.setStatus(state.getLastBlock(), state.getLastBlockDifficulty());
        this.transactionPool.setBestBlock(state.getLastBlock());
        return true;
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

            requestStateChunk(state, peer, task.getFrom(), task.getBlockNumber(), chunkSize);
        } else {
            logger.warn("No more chunk request tasks.");
        }
    }

    private void scheduleJob(SyncMessageHandler.Job job) {
        Peer sender = job.getSender();
        NodeID senderId = sender.getPeerNodeID();
        long jobCount = this.requestQueue.stream().filter(j -> j.getSender().getPeerNodeID().equals(senderId)).count();
        if (jobCount >= this.maxSenderRequests) {
            logger.warn("Too many jobs: [{}] from sender: [{}]. Skipping job of type: [{}]", jobCount, sender, job.getMsgType());
            return;
        }

        boolean offered = requestQueue.offer(job);
        if (!offered) {
            logger.warn("Processing of {} message was rejected", job.getMsgType());
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
