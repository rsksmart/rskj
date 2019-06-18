package org.ethereum.util;

import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.net.NodeID;
import co.rsk.scoring.PeerScoring;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.net.server.ChannelManager;

public class RskMockFactory {

    public static PeerScoringManager getPeerScoringManager() {
        PeerScoringManager peerScoringManager = mock(PeerScoringManager.class);
        when(peerScoringManager.hasGoodReputation(isA(NodeID.class))).thenReturn(true);
        when(peerScoringManager.getPeerScoring(isA(NodeID.class))).thenReturn(new PeerScoring());
        return peerScoringManager;
    }

    public static ChannelManager getChannelManager() {
        return mock(ChannelManager.class);
    }
}
