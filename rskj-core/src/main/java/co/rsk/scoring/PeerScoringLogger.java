package co.rsk.scoring;

import co.rsk.net.NodeID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger for peer scoring analysis.
 * */
public class PeerScoringLogger {

    private PeerScoringLogger() {}

    private static final Logger logger = LoggerFactory.getLogger("peerScoring");

    public static void recordEvent(NodeID nodeID, EventType event) {
        String peersBy = "peers by " + (nodeID != null? "NodeID" : "Address");
        String nodeIDFormated = nodeIdForDebug(nodeID);
        logger.debug("Recorded event {}, {}, Event {}", nodeIDFormated, peersBy, event);
    }

    public static void startPunishment(NodeID nodeID, PeerScoring peerScoring, long punishmentTime, EventType event) {
        String nodeIDFormated = nodeIdForDebug(nodeID);
        logger.debug("{} has been punished for {} milliseconds. Reason {}", nodeIDFormated, punishmentTime, event);
        logger.debug("{}", new PeerScoringInformation(peerScoring, nodeIDFormated, ""));
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
