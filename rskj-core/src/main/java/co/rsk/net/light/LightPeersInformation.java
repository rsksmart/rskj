/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light;

import co.rsk.core.BlockDifficulty;
import com.google.common.annotations.VisibleForTesting;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LightPeersInformation {

    private Map<LightPeer, LightStatus> peerStatuses = new HashMap<>();
    private Map<LightPeer, Boolean> peerTxRelay = new HashMap<>();

    public int getConnectedPeersSize() {
        return peerStatuses.size();
    }

    public void registerLightPeer(LightPeer lightPeer, LightStatus status, boolean txRelay) {

        if (!peerStatuses.containsKey(lightPeer)) {
            peerStatuses.put(lightPeer, status);
        }

        peerTxRelay.put(lightPeer, txRelay);
    }

    public LightStatus getLightPeerStatus(LightPeer peer) {
        return peerStatuses.get(peer);
    }

    @VisibleForTesting
    public boolean hasTxRelay(LightPeer peer) {
        if (!peerTxRelay.containsKey(peer)) {
            return false;
        }

        return peerTxRelay.get(peer);
    }

    public Optional<LightPeer> getBestPeer() {
        Comparator<Map.Entry<LightPeer, LightStatus>> comparator = this::comparePeerTotalDifficulty;
        return peerStatuses.entrySet().stream().max(comparator).map(Map.Entry::getKey);
    }

    private int comparePeerTotalDifficulty(
            Map.Entry<LightPeer, LightStatus> entry,
            Map.Entry<LightPeer, LightStatus> other) {
        BlockDifficulty ttd = entry.getValue().getTotalDifficulty();
        BlockDifficulty otd = other.getValue().getTotalDifficulty();

        // status messages from outdated nodes might have null difficulties
        if (ttd == null && otd == null) {
            return 0;
        }

        if (ttd == null) {
            return -1;
        }

        if (otd == null) {
            return 1;
        }

        return ttd.compareTo(otd);
    }
}
