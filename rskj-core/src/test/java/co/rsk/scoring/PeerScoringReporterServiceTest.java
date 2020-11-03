package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class PeerScoringReporterServiceTest {

    private PeerScoringReporterService peerScoringReporterService;
    private PeerScoringManager peerScoringManager;

    @Before
    public void setup() {
        peerScoringManager = mock(PeerScoringManager.class);
        peerScoringReporterService = new PeerScoringReporterService(3000L, peerScoringManager);
    }

    @Test
    public void shouldStopThreadOnStop() {
        when(peerScoringManager.getPeersInformation()).thenReturn(mock(List.class));
        assertStop();
    }

    @Test
    public void shouldStopOnException() {
        when(peerScoringManager.getPeersInformation()).thenThrow(Exception.class);
        assertStop();
    }

    public void assertStop() {
        peerScoringReporterService.start();

        Assert.assertFalse(peerScoringReporterService.isSchedulerStopped());

        peerScoringReporterService.stop();

        Assert.assertTrue(peerScoringReporterService.isSchedulerStopped());
    }

}
