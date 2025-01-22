/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.messages.MessageWithId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class SnapSyncRequestManager {

    private static final Logger logger = LoggerFactory.getLogger("snapshotprocessor");

    private static final long MAX_RETRY_NUM = 2;

    private final SyncConfiguration syncConfiguration;
    private final SyncEventsHandler syncEventsHandler;

    private final Map<Long, PendingRequest> pendingRequests = new HashMap<>();

    public SnapSyncRequestManager(@Nonnull SyncConfiguration syncConfiguration, @Nonnull SyncEventsHandler syncEventsHandler) {
        this.syncConfiguration = Objects.requireNonNull(syncConfiguration);
        this.syncEventsHandler = Objects.requireNonNull(syncEventsHandler);
    }

    synchronized void submitRequest(@Nonnull PeerSelector peerSelector, @Nonnull RequestFactory requestFactory)  throws SendRequestException {
        long messageId = syncEventsHandler.nextMessageId();
        PendingRequest pendingRequest = new PendingRequest(peerSelector, requestFactory);
        pendingRequests.put(messageId, pendingRequest);

        MessageWithId messageWithId = pendingRequest.send(messageId, System.currentTimeMillis());
        syncEventsHandler.registerPendingMessage(messageWithId);
    }

    synchronized boolean processResponse(@Nonnull MessageWithId responseMessage) {
        return pendingRequests.remove(responseMessage.getId()) != null;
    }

    synchronized void resendExpiredRequests() throws SendRequestException {
        long requestTimeout = syncConfiguration.getTimeoutWaitingRequest().toMillis();
        long now = System.currentTimeMillis();
        long exp = now - requestTimeout;
        Map<Long, PendingRequest> resentRequests = null;

        for (Iterator<Map.Entry<Long, PendingRequest>> iter = pendingRequests.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry<Long, PendingRequest> msgEntry = iter.next();
            PendingRequest pendingRequest = msgEntry.getValue();
            if (pendingRequest.isExpired(exp)) {
                iter.remove();

                long messageId = syncEventsHandler.nextMessageId();
                MessageWithId messageWithId = pendingRequest.reSend(msgEntry.getKey(), messageId, now);
                syncEventsHandler.registerPendingMessage(messageWithId);
                if (resentRequests == null) {
                    resentRequests = new HashMap<>();
                }
                resentRequests.put(messageId, pendingRequest);
            }
        }

        if (resentRequests != null) {
            pendingRequests.putAll(resentRequests);
        }
    }

    @FunctionalInterface
    public interface RequestFactory {
        MessageWithId createRequest(long messageId);
    }

    @FunctionalInterface
    public interface PeerSelector {
        Optional<Peer> selectPeer(@Nullable NodeID failedPeerIds);

        static Builder builder() {
            return new Builder();
        }

        class Builder {
            private Supplier<Optional<Peer>> defaultPeerSupplier = Optional::empty;
            private Function<Set<NodeID>, Optional<Peer>> altPeerSupplier = failedPeerIds -> Optional.empty();

            public Builder withDefaultPeerOption(Supplier<Optional<Peer>> defaultPeerOptionSupplier) {
                this.defaultPeerSupplier = Objects.requireNonNull(defaultPeerOptionSupplier);
                return this;
            }

            public Builder withDefaultPeer(Supplier<Peer> defaultPeerSupplier) {
                Objects.requireNonNull(defaultPeerSupplier);
                this.defaultPeerSupplier = () -> Optional.ofNullable(defaultPeerSupplier.get());
                return this;
            }

            public Builder withAltPeer(Function<Set<NodeID>, Optional<Peer>> altPeerSupplier) {
                this.altPeerSupplier = Objects.requireNonNull(altPeerSupplier);
                return this;
            }

            public PeerSelector build() {
                return failedPeerId -> Optional.ofNullable(failedPeerId)
                        .flatMap(peerId -> altPeerSupplier.apply(Collections.singleton(peerId)))
                        .or(defaultPeerSupplier)
                        .or(() -> altPeerSupplier.apply(Collections.emptySet()));
            }
        }
    }

    private static class PendingRequest {
        private final PeerSelector peerSelector;
        private final RequestFactory requestFactory;

        private Peer selectedPeer;
        private long started;
        private int retries;

        PendingRequest(@Nonnull PeerSelector peerSelector, @Nonnull RequestFactory requestFactory) {
            this.peerSelector = Objects.requireNonNull(peerSelector);
            this.requestFactory = Objects.requireNonNull(requestFactory);
        }

        MessageWithId send(long messageId, long now) throws SendRequestException {
            this.started = now;

            Optional<Peer> selectedPeerOpt = this.peerSelector.selectPeer(null);
            if (selectedPeerOpt.isEmpty()) {
                throw new SendRequestException("Failed to send request - no peer available");
            }

            this.selectedPeer = selectedPeerOpt.get();

            MessageWithId msg = requestFactory.createRequest(messageId);

            logger.debug("Sending request: [{}] with id: [{}] to: [{}]", msg.getMessageType(), msg.getId(), this.selectedPeer.getPeerNodeID());
            selectedPeer.sendMessage(msg);

            return msg;
        }

        MessageWithId reSend(long previousMessageId, long newMessageId, long now) throws SendRequestException {
            this.started = now;

            if (this.retries >= MAX_RETRY_NUM) {
                throw new SendRequestException("Failed to re-send expired request with previous messageId: [" + previousMessageId + "] - max retries reached");
            }

            Optional<Peer> selectedPeerOpt = this.peerSelector.selectPeer(this.selectedPeer.getPeerNodeID());
            if (selectedPeerOpt.isEmpty()) {
                throw new SendRequestException("Failed to re-send expired request with previous messageId: [" + previousMessageId + "] - no peer available");
            }

            this.selectedPeer = selectedPeerOpt.get();

            MessageWithId msg = this.requestFactory.createRequest(newMessageId);

            logger.debug("Re-sending expired request: [{}] with old id: [{}] to: [{}] with new id: [{}]", msg.getMessageType(), previousMessageId, this.selectedPeer.getPeerNodeID(), msg.getId());
            this.selectedPeer.sendMessage(msg);

            this.retries++;

            return msg;
        }

        boolean isExpired(long exp) {
            return started <= exp;
        }
    }

    public static class SendRequestException extends Exception {

        public SendRequestException(String message) {
            super(message);
        }
    }
}
