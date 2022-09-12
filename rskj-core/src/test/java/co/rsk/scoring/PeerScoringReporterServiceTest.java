package co.rsk.scoring;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class PeerScoringReporterServiceTest {

    private TestPeerScoringReporterService peerScoringReporterService;
    private PeerScoringManager peerScoringManager;

    @BeforeEach
    void setup() {
        peerScoringManager = mock(PeerScoringManager.class);
        peerScoringReporterService = TestPeerScoringReporterService.withScheduler(3000L, peerScoringManager);
    }

    @Test
    void shouldStopOnStop() {
        testStart();

        peerScoringReporterService.stop();

        Assertions.assertFalse(peerScoringReporterService.isRunning());
    }

    @Test
    void shouldStartOnStart() {
        testStart();
    }

    void testStart() {
        when(peerScoringManager.getPeersInformation()).thenReturn(mock(List.class));

        peerScoringReporterService.start();

        Assertions.assertTrue(peerScoringReporterService.isRunning());
    }

    @Test
    void shouldStopOnException() {
        when(peerScoringManager.getPeersInformation()).thenThrow(new RuntimeException());

        peerScoringReporterService.start();

        Assertions.assertFalse(peerScoringReporterService.isRunning());
    }
}
