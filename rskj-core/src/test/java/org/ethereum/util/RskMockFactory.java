package org.ethereum.util;

import co.rsk.net.NodeID;
import co.rsk.scoring.PeerScoring;
import co.rsk.scoring.PeerScoringManager;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RskMockFactory {

    public static PeerScoringManager getPeerScoringManager() {
        PeerScoringManager peerScoringManager = mock(PeerScoringManager.class);
        when(peerScoringManager.hasGoodReputation(isA(NodeID.class))).thenReturn(true);
        when(peerScoringManager.getPeerScoring(isA(NodeID.class))).thenReturn(new PeerScoring());
        return peerScoringManager;
    }
}
