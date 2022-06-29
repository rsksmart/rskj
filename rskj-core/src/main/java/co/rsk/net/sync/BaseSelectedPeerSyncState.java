/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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
import co.rsk.scoring.EventType;

/**
 * Base class for those SyncState concrete classes that need a connected peer for their logic
 */
public abstract class BaseSelectedPeerSyncState extends BaseSyncState {

    protected final Peer selectedPeer;

    protected BaseSelectedPeerSyncState(SyncEventsHandler syncEventsHandler, SyncConfiguration syncConfiguration, Peer peer) {
        super(syncEventsHandler, syncConfiguration);
        this.selectedPeer = peer;
    }

    @Override
    protected void onMessageTimeOut() {
        syncEventsHandler.onErrorSyncing(selectedPeer, EventType.TIMEOUT_MESSAGE,
                "Timeout waiting requests on {}", this.getClass());
    }

}
