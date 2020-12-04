package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class PeerScoringReporterServiceTest {

    private TestPeerScoringReporterService peerScoringReporterService;
    private PeerScoringManager peerScoringManager;

    @Before
    public void setup() {
        peerScoringManager = mock(PeerScoringManager.class);
        peerScoringReporterService = TestPeerScoringReporterService.withScheduler(3000L, peerScoringManager);
    }

    @Test
    public void shouldStopOnStop() {
        testStart();

        peerScoringReporterService.stop();

        Assert.assertFalse(peerScoringReporterService.isRunning());
    }

    @Test
    public void shouldStartOnStart() {
        testStart();
    }

    public void testStart() {
        when(peerScoringManager.getPeersInformation()).thenReturn(mock(List.class));

        peerScoringReporterService.start();

        Assert.assertTrue(peerScoringReporterService.isRunning());
    }

    @Test
    public void shouldStopOnException() {
        when(peerScoringManager.getPeersInformation()).thenThrow(new RuntimeException());

        peerScoringReporterService.start();

        Assert.assertFalse(peerScoringReporterService.isRunning());
    }
}
