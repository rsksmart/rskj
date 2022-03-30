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
