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

package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import co.rsk.net.Peer;
import co.rsk.net.SnapshotProcessor;
import co.rsk.net.messages.MessageType;
import co.rsk.net.messages.SnapBlocksResponseMessage;
import co.rsk.net.messages.SnapStateChunkResponseMessage;
import co.rsk.net.messages.SnapStatusResponseMessage;
import co.rsk.scoring.EventType;
import co.rsk.trie.TrieDTO;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SnapSyncState extends BaseSyncState {

    private static final Logger logger = LoggerFactory.getLogger("SnapSyncState");

    private final SnapshotProcessor snapshotProcessor;

    // queue for processing of SNAP responses
    private final BlockingQueue<SyncMessageHandler.Job> responseQueue = new LinkedBlockingQueue<>();

    // priority queue for ordering chunk responses
    private final PriorityQueue<SnapStateChunkResponseMessage> snapStateChunkQueue = new PriorityQueue<>(
            Comparator.comparingLong(SnapStateChunkResponseMessage::getFrom)
    );

    private final Queue<ChunkTask> chunkTaskQueue = new LinkedList<>();

    private BigInteger stateSize = BigInteger.ZERO;
    private BigInteger stateChunkSize = BigInteger.ZERO;
    private boolean stateFetched;
    private final List<TrieDTO> allNodes;

    private long remoteTrieSize;
    private byte[] remoteRootHash;
    private final List<Pair<Block, BlockDifficulty>> blocks;

    private Block lastBlock;
    private BlockDifficulty lastBlockDifficulty;
    private Peer lastBlockSender;

    private BlockHeader lastVerifiedBlockHeader;

    private long nextExpectedFrom = 0L;

    private volatile Boolean isRunning;
    private final Thread thread;

    public SnapSyncState(SyncEventsHandler syncEventsHandler, SnapshotProcessor snapshotProcessor, SyncConfiguration syncConfiguration) {
        this(syncEventsHandler, snapshotProcessor, syncConfiguration, null);
    }

    @VisibleForTesting
    SnapSyncState(SyncEventsHandler syncEventsHandler, SnapshotProcessor snapshotProcessor,
                  SyncConfiguration syncConfiguration, @Nullable SyncMessageHandler.Listener listener) {
        super(syncEventsHandler, syncConfiguration);
        this.snapshotProcessor = snapshotProcessor; // TODO(snap-poc) code in SnapshotProcessor should be moved here probably
        this.allNodes = Lists.newArrayList();
        this.blocks = Lists.newArrayList();
        this.thread = new Thread(new SyncMessageHandler("SNAP/client", responseQueue, listener) {

            @Override
            public boolean isRunning() {
                return isRunning;
            }
        }, "snap sync client handler");
    }

    @Override
    public void onEnter() {
        if (isRunning != null) {
            logger.warn("Invalid state, isRunning: [{}]", isRunning);
            return;
        }
        isRunning = Boolean.TRUE;
        thread.start();
        snapshotProcessor.startSyncing(this);
    }

    @Override
    public void onSnapStatus(Peer sender, SnapStatusResponseMessage responseMessage) {
        try {
            responseQueue.put(new SyncMessageHandler.Job(sender, responseMessage) {
                @Override
                public void run() {
                    snapshotProcessor.processSnapStatusResponse(SnapSyncState.this, sender, responseMessage);
                }
            });
        } catch (InterruptedException e) {
            logger.warn("SnapStatusResponseMessage processing was interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onSnapBlocks(Peer sender, SnapBlocksResponseMessage responseMessage) {
        try {
            responseQueue.put(new SyncMessageHandler.Job(sender, responseMessage) {
                @Override
                public void run() {
                    snapshotProcessor.processSnapBlocksResponse(SnapSyncState.this, sender, responseMessage);
                }
            });
        } catch (InterruptedException e) {
            logger.warn("SnapBlocksResponseMessage processing was interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onSnapStateChunk(Peer sender, SnapStateChunkResponseMessage responseMessage) {
        try {
            responseQueue.put(new SyncMessageHandler.Job(sender, responseMessage) {
                @Override
                public void run() {
                    snapshotProcessor.processStateChunkResponse(SnapSyncState.this, sender, responseMessage);
                }
            });
        } catch (InterruptedException e) {
            logger.warn("SnapStateChunkResponseMessage processing was interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void newBlockHeaders(Peer peer, List<BlockHeader> chunk) {
        try {
            responseQueue.put(new SyncMessageHandler.Job(peer, MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE) {
                @Override
                public void run() {
                    snapshotProcessor.processBlockHeaderChunk(SnapSyncState.this, peer, chunk);
                }
            });
        } catch (InterruptedException e) {
            logger.warn("SnapStateChunkResponseMessage processing was interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    public SyncEventsHandler getSyncEventsHandler() {
        return this.syncEventsHandler;
    }

    public void onNewChunk() {
        resetTimeElapsed();
    }

    @Override
    public void tick(Duration duration) {
        // TODO(snap-poc) handle multiple peers casuistry, similarly to co.rsk.net.sync.DownloadingBodiesSyncState.tick

        timeElapsed = timeElapsed.plus(duration);
        if (timeElapsed.compareTo(syncConfiguration.getTimeoutWaitingSnapChunk()) >= 0) {
            onMessageTimeOut();
        }
    }

    @Override
    protected void onMessageTimeOut() {
        // TODO: call syncEventsHandler.onErrorSyncing() and punish peers after SNAP feature discovery is implemented

        finish();
//         resetTimeElapsed();
    }

    public Block getLastBlock() {
        return lastBlock;
    }

    public void setLastBlock(Block lastBlock, BlockDifficulty lastBlockDifficulty, Peer lastBlockSender) {
        this.lastBlock = lastBlock;
        this.lastBlockDifficulty = lastBlockDifficulty;
        this.lastBlockSender = lastBlockSender;
    }

    public BlockHeader getLastVerifiedBlockHeader() {
        return lastVerifiedBlockHeader;
    }

    public void setLastVerifiedBlockHeader(BlockHeader lastVerifiedBlockHeader) {
        this.lastVerifiedBlockHeader = lastVerifiedBlockHeader;
    }

    public long getNextExpectedFrom() {
        return nextExpectedFrom;
    }

    public void setNextExpectedFrom(long nextExpectedFrom) {
        this.nextExpectedFrom = nextExpectedFrom;
    }

    public BlockDifficulty getLastBlockDifficulty() {
        return lastBlockDifficulty;
    }

    public Peer getLastBlockSender() {
        return lastBlockSender;
    }

    public byte[] getRemoteRootHash() {
        return remoteRootHash;
    }

    public void setRemoteRootHash(byte[] remoteRootHash) {
        this.remoteRootHash = remoteRootHash;
    }

    public long getRemoteTrieSize() {
        return remoteTrieSize;
    }

    public void setRemoteTrieSize(long remoteTrieSize) {
        this.remoteTrieSize = remoteTrieSize;
    }

    public void addBlock(Pair<Block, BlockDifficulty> blockPair) {
        blocks.add(blockPair);
    }

    public Pair<Block, BlockDifficulty> getLastBlockPair() {
        return blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
    }

    public void connectBlocks(BlockConnectorHelper blockConnectorHelper) {
        blockConnectorHelper.startConnecting(blocks);
    }

    public List<TrieDTO> getAllNodes() {
        return allNodes;
    }

    public BigInteger getStateSize() {
        return stateSize;
    }

    public void setStateSize(BigInteger stateSize) {
        this.stateSize = stateSize;
    }

    public boolean isStateFetched() {
        return stateFetched;
    }

    public void setStateFetched() {
        this.stateFetched = true;
    }

    public BigInteger getStateChunkSize() {
        return stateChunkSize;
    }

    public void setStateChunkSize(BigInteger stateChunkSize) {
        this.stateChunkSize = stateChunkSize;
    }

    public PriorityQueue<SnapStateChunkResponseMessage> getSnapStateChunkQueue() {
        return snapStateChunkQueue;
    }

    public Queue<ChunkTask> getChunkTaskQueue() {
        return chunkTaskQueue;
    }

    public int getBlockHeaderChunkSize() {
        return syncConfiguration.getChunkSize();
    }

    public boolean isRunning() {
        return isRunning == Boolean.TRUE;
    }

    public void finish() {
        if (isRunning != Boolean.TRUE) {
            logger.warn("Invalid state, isRunning: [{}]", isRunning);
            return;
        }

        isRunning = Boolean.FALSE;
        thread.interrupt();

        syncEventsHandler.stopSyncing();
    }

    public void fail(Peer peer, EventType eventType, String message, Object... arguments) {
        if (isRunning != Boolean.TRUE) {
            logger.warn("Invalid state, isRunning: [{}]", isRunning);
            return;
        }

        isRunning = Boolean.FALSE;
        thread.interrupt();

        syncEventsHandler.onErrorSyncing(peer, eventType, message, arguments);
    }

    @VisibleForTesting
    public void setRunning() {
        isRunning = true;
    }
}
