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
    private boolean running = false;

    public PeerScoringReporterService(long time, PeerScoringManager peerScoringManager) {
        this.time = time;
        this.peerScoringManager = peerScoringManager;
    }

    @Override
    public void start() {
        logger.debug("starting peer scoring reporter service");
        try {
            List<PeerScoringInformation> peerScoringInformationList = peerScoringManager.getPeersInformation();
            scheduledExecutorService.scheduleAtFixedRate(() -> printReport(peerScoringInformationList),
                    0,
                    time,
                    TimeUnit.MILLISECONDS
            );
            running = true;
        } catch (Exception e) {
            logger.warn("peer scoring reporter failed", e);
            stop();
        }
    }

    @Override
    public void stop() {
        scheduledExecutorService.shutdown();
        logger.warn("peer scoring reporter service has been stopped");
        running = false;
    }

    public boolean printReport(List<PeerScoringInformation> peerScoringInformationList) {
        try {
            String badReputationSummary = PeerScoringReporterUtil.badReputationSummaryString(peerScoringInformationList);
            logger.debug("bad reputation summary {}", badReputationSummary);

            String peersInformationDetailed = PeerScoringReporterUtil.detailedBadReputationStatusString(peerScoringInformationList);
            logger.debug("detailed bad reputation status {}", peersInformationDetailed);
        } catch (Exception e) {
            logger.warn("failed to print report", e);

            return false;
        }

        return true;
    }

    @VisibleForTesting
    public boolean initialized() {
        return scheduledExecutorService != null && peerScoringManager != null && time > 0;
    }

    @VisibleForTesting
    public boolean isRunning() {
        return running && !scheduledExecutorService.isShutdown();
    }
}
