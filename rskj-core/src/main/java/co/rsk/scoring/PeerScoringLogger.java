package co.rsk.scoring;

import co.rsk.net.NodeID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger for peer scoring analysis.
 * */
public class PeerScoringLogger {

    private static final Logger logger = LoggerFactory.getLogger("peerScoring");

    public static void recordEvent(NodeID id, EventType event) {
        String peersBy = "peers by " + (id != null? "NodeID" : "Address");
        logger.debug("Recorded event {}, {}, Event {}", nodeIdForDebug(id), peersBy, event.toString());
    }

    public static void startPunishment(NodeID id, PeerScoring peerScoring, long punishmentTime, EventType event) {
        logger.debug("{} has been punished for {} milliseconds. Reason ", nodeIdForDebug(id), punishmentTime, event.toString());
        logger.debug("{}", new PeerScoringInformation(peerScoring, nodeIdForDebug(id), ""));
    }

    public static void goodReputation(NodeID id) {
        logger.debug("{} has good reputation", nodeIdForDebug(id));
    }

    private static String nodeIdForDebug(NodeID id) {
        if(id == null) {
            return "NO_NODE_ID";
        }
        return "NodeID " + id.toString().substring(id.toString().length() - 7, id.toString().length() - 1);
    }

    public static void unbannedAddress(String address) {
        logger.debug("Unbanned address block {}", address);
    }

    public static void bannedAddress(String address) {
        logger.debug("Banned address {}", address);
    }
}
