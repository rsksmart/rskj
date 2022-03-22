package co.rsk.scoring;

/**
 * ScoringCalculator calculates the reputation of a peer
 * <p>
 * Created by ajlopez on 30/06/2017.
 */
public class ScoringCalculator {
    /**
     * Calculates the reputation of a peer scoring
     *
     * Current implementation assigns not good reputation to peers
     * having any invalid transaction, block or header recorded events
     *
     * @param scoring   the scoring of the peer
     * @return  <tt>true</tt> if the peer has good reputation
     */
    public boolean hasGoodScore(PeerScoring scoring) {
        //TODO(lsebrie): implement empty messages as responses so timeout can be handled as it should
        return  scoring.getEventCounter(EventType.INVALID_BLOCK) < 1 &&
                scoring.getEventCounter(EventType.INVALID_MESSAGE) < 1 &&
                scoring.getEventCounter(EventType.INVALID_HEADER) < 1;
    }
}
