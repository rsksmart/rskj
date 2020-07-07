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

package co.rsk.net.light.state;

import co.rsk.net.light.LightPeer;
import co.rsk.net.light.LightSyncProcessor;
import org.ethereum.core.BlockHeader;

import java.util.List;

public class DecidingLightSyncState implements LightSyncState {
    private final LightSyncProcessor lightSyncProcessor;
    private final LightPeer lightPeer;
    private final byte[] bestBlockHash;
    private final long bestBlockNumber;

    public DecidingLightSyncState(LightSyncProcessor lightSyncProcessor, LightPeer lightPeer, BlockHeader bestBlockHeader) {
        this.lightSyncProcessor = lightSyncProcessor;
        this.lightPeer = lightPeer;
        this.bestBlockHash = bestBlockHeader.getHash().getBytes();
        this.bestBlockNumber = bestBlockHeader.getNumber();
    }

    @Override
    public void sync() {
        if (bestBlockNumber != 0) {
            lightSyncProcessor.startAncestorSearchFrom(lightPeer,
                    bestBlockHash, bestBlockNumber);
        }
    }

    @Override
    public void newBlockHeaders(LightPeer lightPeer, List<BlockHeader> blockHeaders) {
        //Nothing to do here
    }
}
