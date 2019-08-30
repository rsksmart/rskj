package co.rsk.net.syncrefactor;

import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.messages.*;
import co.rsk.net.sync.PeersInformation;
import co.rsk.scoring.EventType;
import org.ethereum.net.server.ChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SyncMessager {

    private static final int MAX_PENDING_MESSAGES = 100_000;
    private static final Logger logger = LoggerFactory.getLogger(SyncMessager.class);

    private final Map<MessageRegister, CompletableFuture<? extends MessageWithId>> pendingMessages;
    private final Queue<CompletableFuture<Duration>> tickRegister;

    private final ChannelManager channelManager;
    private final PeersInformation peersInformation;
    private long requestId;

    public SyncMessager(ChannelManager channelManager,
                        PeersInformation peersInformation) {
        this.channelManager = channelManager;
        this.peersInformation = peersInformation;
        this.pendingMessages = new LinkedHashMap<MessageRegister, CompletableFuture<? extends MessageWithId>>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<MessageRegister,
                    CompletableFuture<? extends MessageWithId>> eldest) {
                boolean shouldDiscard = size() > MAX_PENDING_MESSAGES;
                if (shouldDiscard) {
                    logger.trace("Pending {}@{} DISCARDED", eldest.getValue(), eldest.getKey());
                }
                return shouldDiscard;
            }
        };
        this.tickRegister = new LinkedList<>();
    }

    public CompletableFuture<BodyResponseMessage> requestBody(NodeID peer, Keccak256 blockHash) {
        BodyRequestMessage message = new BodyRequestMessage(++requestId, blockHash.getBytes());
        return sendMessage(peer, message);
    }

    public CompletableFuture<BlockResponseMessage> requestBlock(NodeID peer, Keccak256 blockHash) {
        BlockRequestMessage message = new BlockRequestMessage(++requestId, blockHash.getBytes());
        return sendMessage(peer, message);
    }

    public CompletableFuture<SkeletonResponseMessage> requestSkeleton(NodeID peer, long height) {
        SkeletonRequestMessage message = new SkeletonRequestMessage(++requestId, height);
        return sendMessage(peer, message);
    }

    public CompletableFuture<BlockHashResponseMessage> requestBlockHash(NodeID peer, long height) {
        BlockHashRequestMessage message = new BlockHashRequestMessage(++requestId, height);
        return sendMessage(peer, message);
    }

    public CompletableFuture<BlockHeadersResponseMessage> requestBlockHeaders(NodeID peer, Keccak256 blockHash, int count) {
        BlockHeadersRequestMessage message = new BlockHeadersRequestMessage(++requestId, blockHash.getBytes(), count);
        return sendMessage(peer, message);
    }

    public synchronized CompletableFuture<Duration> registerForTick() {
        CompletableFuture<Duration> f = new CompletableFuture<>();
        tickRegister.add(f);
        return f;
    }

    public synchronized void tick(Duration elapsedTime) {
        while (!tickRegister.isEmpty()) {
            tickRegister.poll().complete(elapsedTime);
        }
    }

    public synchronized <T extends MessageWithId> void receiveMessage(NodeID peer, T message) {

        peersInformation.getOrRegisterPeer(peer);
        MessageRegister messageRegister = new MessageRegister(message.getId(), peer, message.getMessageType());
        if (!pendingMessages.containsKey(messageRegister)) {
            peersInformation.reportEvent(peer, EventType.UNEXPECTED_MESSAGE);
            return;
        }

        CompletableFuture<T> future = (CompletableFuture<T>) pendingMessages.remove(messageRegister);
        future.complete(message);
    }

    private synchronized <T extends MessageWithId> CompletableFuture<T> sendMessage(NodeID peer,
                                                                                    MessageWithId message) {
        MessageRegister messageRegister = new MessageRegister(requestId, peer, message.getResponseMessageType());

        boolean sent = channelManager.sendMessageTo(peer, message);

        CompletableFuture<T> future = new CompletableFuture<>();
        if (!sent) {
            future.completeExceptionally(new RuntimeException(
                    String.format("Error when sending message %s to peer %s", message.getMessageType(), peer)));
            return future;
        }

        pendingMessages.put(messageRegister, future);

        return future;
    }

    private class MessageRegister {
        private final long requestId;
        private final MessageType messageType;
        private final NodeID peer;

        MessageRegister(long requestId, NodeID peer, MessageType messageType) {
            this.requestId = requestId;
            this.messageType = messageType;
            this.peer = peer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageRegister that = (MessageRegister) o;
            return requestId == that.requestId &&
                    messageType == that.messageType &&
                    Objects.equals(peer, that.peer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestId, messageType, peer);
        }
    }
}
