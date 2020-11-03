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
 * This service prints a summary of nodes with bad reputation, every 5 minutes (configurable)
 * */
public class PeerScoringReporterService implements InternalService {

    private static final Logger logger = LoggerFactory.getLogger("peerScoring");

    private final PeerScoringManager peerScoringManager;
    private final long time;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public PeerScoringReporterService(long time, PeerScoringManager peerScoringManager) {
        this.time = time;
        this.peerScoringManager = peerScoringManager;
    }

    @Override
    public void start() {
        logger.debug("starting peer scoring summary service");
        try {
            scheduledExecutorService.scheduleAtFixedRate(() -> printReport(peerScoringManager.getPeersInformation()),
                    0,
                    time,
                    TimeUnit.MILLISECONDS
            );
        } catch (Exception e) {
            logger.warn("peer scoring reporter failed");
            logger.warn(e.getMessage());
            stop();
        }
    }

    @Override
    public void stop() {
        scheduledExecutorService.shutdown();
        logger.warn("peer scoring summary service has been stopped");
    }

    public boolean printReport(List<PeerScoringInformation> peerScoringInformationList) {
        try {
            String badReputationSummary = PeerScoringReporterUtil.badReputationSummaryString(peerScoringInformationList);
            logger.debug("bad reputation summary {}", badReputationSummary);

            String peersInformationDetailed = PeerScoringReporterUtil.detailedBadReputationStatusString(peerScoringInformationList);
            logger.debug("detailed bad reputation status {}", peersInformationDetailed);
        } catch (Exception e) {
            logger.warn("failed to print report");
            logger.warn(e.getMessage());
        }

        return true;
    }

    @VisibleForTesting
    public boolean isSchedulerStopped() {
        return scheduledExecutorService.isShutdown();
    }

    @VisibleForTesting
    public boolean initialized() {
        return scheduledExecutorService != null && peerScoringManager != null && time > 0;
    }
}
