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

package co.rsk.net;

import co.rsk.net.messages.*;

import java.time.Duration;
import java.util.Set;

public interface SyncProcessor {
    void processStatus(Peer sender, Status status);

    void processSkeletonResponse(Peer peer, SkeletonResponseMessage message);

    void processBlockHashResponse(Peer peer, BlockHashResponseMessage message);

    void processBlockHeadersResponse(Peer peer, BlockHeadersResponseMessage message);

    void processBodyResponse(Peer peer, BodyResponseMessage message);

    void processNewBlockHash(Peer peer, NewBlockHashMessage message);

    void processBlockResponse(Peer peer, BlockResponseMessage message);

    Set<NodeID> getKnownPeersNodeIDs();

    void onTimePassed(Duration timePassed);

    void stopSyncing();
}
