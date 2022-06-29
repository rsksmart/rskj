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

package org.ethereum.util;

import co.rsk.net.NodeID;
import co.rsk.scoring.PeerScoring;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.net.server.ChannelManager;

import static org.mockito.Mockito.*;

public class RskMockFactory {

    public static PeerScoringManager getPeerScoringManager() {
        PeerScoringManager peerScoringManager = mock(PeerScoringManager.class);
        when(peerScoringManager.hasGoodReputation(isA(NodeID.class))).thenReturn(true);
        when(peerScoringManager.getPeerScoring(isA(NodeID.class))).thenReturn(new PeerScoring("id1"));
        return peerScoringManager;
    }

    public static ChannelManager getChannelManager() {
        return mock(ChannelManager.class);
    }
}
