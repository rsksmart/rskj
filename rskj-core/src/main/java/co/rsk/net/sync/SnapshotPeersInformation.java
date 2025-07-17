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
import co.rsk.scoring.EventType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * This is mostly a workaround because SyncProcessor needs to access Peer instances.
 * TODO(mc) remove this after the logical node abstraction is created, since it will wrap
 *     things such as the underlying communication channel.
 */
public interface SnapshotPeersInformation {
    Optional<Peer> getBestPeer();
    Optional<Peer> getBestSnapPeer();
    List<Peer> getBestSnapPeerCandidates();
    Optional<Peer> getBestPeer(Set<NodeID> exclude);
    Optional<Peer> getBestSnapPeer(Set<NodeID> exclude);
    SyncPeerStatus getOrRegisterPeer(Peer peer);
    void processSyncingError(Peer peer, EventType eventType, String message, Object... arguments);
}
