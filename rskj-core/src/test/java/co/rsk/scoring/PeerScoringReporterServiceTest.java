package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
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
        peerScoringReporterService.start();

        Assert.assertTrue(peerScoringReporterService.isRunning());

        peerScoringReporterService.stop();

        Assert.assertFalse(peerScoringReporterService.isRunning());
    }

    @Test
    public void shouldStopOnException() {
        //todo(techdebt) should assert that it's actually running
        when(peerScoringManager.getPeersInformation()).thenThrow(new RuntimeException());
        peerScoringReporterService.start();
        Assert.assertFalse(peerScoringReporterService.isRunning());
    }

    @Test
    public void shouldPrintReport() {
        Assert.assertTrue(peerScoringReporterService.printReport(badReputationPeers()));
    }

    @Test
    public void shouldntPrintReportOnError() {
        List<PeerScoringInformation> peerScoringInformationList = new ArrayList<>();
        peerScoringInformationList.add(null);

        Assert.assertFalse(peerScoringReporterService.printReport(peerScoringInformationList));
    }

    private List<PeerScoringInformation> badReputationPeers() {
        return Arrays.asList(buildPeerScoringInformation("4", false)
                , buildPeerScoringInformation("5", false));
    }

    private PeerScoringInformation buildPeerScoringInformation(String id, boolean goodReputation) {
        return new PeerScoringInformation("node", goodReputation,
                4, 0, 0,
                5, 3, 9, 1,
                0, 0, 0, 0,
                4, 0, 0, id);
    }
}
