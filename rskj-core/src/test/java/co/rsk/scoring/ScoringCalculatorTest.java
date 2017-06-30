package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 30/06/2017.
 */
public class ScoringCalculatorTest {
    @Test
    public void emptyScoringHasGoodReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring();

        Assert.assertTrue(calculator.hasGoodReputation(scoring));
    }

    @Test
    public void scoringWithOneValidBlockHasGoodReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring();
        scoring.recordEvent(EventType.VALID_BLOCK);

        Assert.assertTrue(calculator.hasGoodReputation(scoring));
    }

    @Test
    public void scoringWithOneValidTransactionHasGoodReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring();
        scoring.recordEvent(EventType.VALID_TRANSACTION);

        Assert.assertTrue(calculator.hasGoodReputation(scoring));
    }

    @Test
    public void scoringWithOneInvalidBlockHasBadReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring();
        scoring.recordEvent(EventType.INVALID_BLOCK);

        Assert.assertFalse(calculator.hasGoodReputation(scoring));
    }

    @Test
    public void scoringWithOneInvalidTransactionHasBadReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring();
        scoring.recordEvent(EventType.INVALID_TRANSACTION);

        Assert.assertFalse(calculator.hasGoodReputation(scoring));
    }
}
