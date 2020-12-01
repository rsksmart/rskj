package co.rsk.scoring;

import co.rsk.config.InternalService;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This internal service prints a summary of peer scoring information, every 5 minutes (configurable)
 * */
public class PeerScoringReporterService implements InternalService {

    private static final Logger logger = LoggerFactory.getLogger("peerScoring");
    private final PeerScoringManager peerScoringManager;
    private final long time;
    protected final ScheduledExecutorService scheduledExecutorService;

    public PeerScoringReporterService(long time, PeerScoringManager peerScoringManager, ScheduledExecutorService scheduledExecutorService) {
        this.peerScoringManager = peerScoringManager;
        this.time = time;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public static PeerScoringReporterService withScheduler(long peerScoringReportTime, PeerScoringManager peerScoringManager) {
        return new PeerScoringReporterService(peerScoringReportTime, peerScoringManager, Executors.newSingleThreadScheduledExecutor());
    }

    @Override
    public void start() {
        logger.debug("starting peer scoring reporter service");
        try {
            List<PeerScoringInformation> peerScoringInformationList = peerScoringManager.getPeersInformation();
            scheduledExecutorService.scheduleAtFixedRate(() -> printReports(peerScoringInformationList),
                    0,
                    time,
                    TimeUnit.MILLISECONDS
            );
            running();
        } catch (Exception e) {
            logger.warn("peer scoring reporter failed", e);
            stop();
        }
    }

    @Override
    public void stop() {
        scheduledExecutorService.shutdown();
        logger.warn("peer scoring reporter service has been stopped");
        stopped();
    }

    public void printReports(List<PeerScoringInformation> peerScoringInformationList) {
        try {
            String reputationSummaryString = PeerScoringReporterUtil.reputationSummaryString(peerScoringInformationList);
            logger.debug("reputation summary {}", reputationSummaryString);

            String detailedReputationString = PeerScoringReporterUtil.detailedReputationString(peerScoringInformationList);
            logger.debug("detailed reputation status {}", detailedReputationString);
        } catch (Exception e) {
            logger.warn("failed to print reports", e);
        }
    }

    @VisibleForTesting
    public boolean initialized() {
        // todo(techdebt) this should be only at TestPeerScoringReporterService class and inject that into a TestRSKContext
        return scheduledExecutorService != null && peerScoringManager != null && time > 0;
    }

    protected void running() {
    }

    protected void stopped() {
    }
}
