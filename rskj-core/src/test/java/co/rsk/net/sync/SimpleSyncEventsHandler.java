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
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class SimpleSyncEventsHandler implements SyncEventsHandler {
    private boolean startSyncingWasCalled_;
    private boolean stopSyncingWasCalled_;

    @Override
    public void sendBlockHeadersRequest(Peer peer, ChunkDescriptor chunk) {
    }

    @Override
    public void startFindingConnectionPoint(Peer peer) {

    }

    @Override
    public void backwardSyncing(Peer peer) {
    }

    @Override
    public long sendBodyRequest(Peer peer, BlockHeader header) { return 0L; }

    @Override
    public void startDownloadingBodies(List<Deque<BlockHeader>> pendingHeaders, Map<Peer, List<BlockIdentifier>> skeletons, Peer peer) {

    }

    @Override
    public void sendSkeletonRequest(Peer peer, long height) {
    }

    @Override
    public void sendBlockHashRequest(Peer peer, long height) {
    }

    @Override
    public void startDownloadingHeaders(Map<Peer, List<BlockIdentifier>> skeletons, long connectionPoint, Peer peer) { }

    @Override
    public void startBlockForwardSyncing(Peer peer) {
        this.startSyncingWasCalled_ = true;
    }

    @Override
    public void backwardDownloadBodies(Block parent, List<BlockHeader> toRequest, Peer peer) {

    }

    @Override
    public void startDownloadingSkeleton(long connectionPoint, Peer peer) { }

    @Override
    public void stopSyncing() { this.stopSyncingWasCalled_ = true; }

    @Override
    public void onLongSyncUpdate(boolean isSyncing, @Nullable Long peerBestBlockNumber) {

    }

    @Override
    public void onErrorSyncing(Peer peer, EventType eventType, String message, Object... arguments) {
        stopSyncing();
    }

    @Override
    public void onSyncIssue(Peer peer, String message, Object... arguments) {

    }

    public boolean startSyncingWasCalled() {
        return startSyncingWasCalled_;
    }

    public boolean stopSyncingWasCalled() {
        return stopSyncingWasCalled_;
    }


    @Override
    public void startSnapSync(PeersInformation peers) {

    }

    @Override
    public void snapSyncFinished() {

    }

    @Override
    public boolean isSnapSyncFinished() {
        return false;
    }

}
