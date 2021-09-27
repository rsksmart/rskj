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
package co.rsk.net.sync;

import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import java.time.Duration;
import java.util.List;

public interface SyncState {
    void newBlockHeaders(List<BlockHeader> chunk);

    // TODO(mc) don't receive a full message
    void newBody(BodyResponseMessage message, Peer peer);

    void newConnectionPointData(byte[] hash);

    /**
     * should only be called when a new peer arrives
     */
    void newPeerStatus();

    void newSkeleton(List<BlockIdentifier> skeletonChunk, Peer peer);

    void onEnter();

    void tick(Duration duration);
}
