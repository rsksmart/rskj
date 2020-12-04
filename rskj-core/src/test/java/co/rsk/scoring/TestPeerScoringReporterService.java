package co.rsk.scoring;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class TestPeerScoringReporterService extends PeerScoringReporterService {

    private boolean running = false;

    public TestPeerScoringReporterService(long time, PeerScoringManager peerScoringManager, ScheduledExecutorService scheduledExecutorService) {
        super(time, peerScoringManager, scheduledExecutorService);
    }

    public static TestPeerScoringReporterService withScheduler(long time, PeerScoringManager peerScoringManager) {
        return new TestPeerScoringReporterService(time, peerScoringManager, Executors.newSingleThreadScheduledExecutor());
    }

    public boolean isRunning() {
        return running && !scheduledExecutorService.isShutdown();
    }

    @Override
    protected void running() {
        running = true;
    }

    @Override
    protected void stopped() {
        running = false;
    }
}
