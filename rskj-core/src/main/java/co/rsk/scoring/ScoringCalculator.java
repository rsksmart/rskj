package co.rsk.scoring;

/**
 * Created by ajlopez on 30/06/2017.
 */
public class ScoringCalculator {
    public boolean hasGoodReputation(PeerScoring scoring) {
        if (scoring.getEventCounter(EventType.INVALID_BLOCK) > 0)
            return false;
        if (scoring.getEventCounter(EventType.INVALID_TRANSACTION) > 0)
            return false;

        return true;
    }
}
