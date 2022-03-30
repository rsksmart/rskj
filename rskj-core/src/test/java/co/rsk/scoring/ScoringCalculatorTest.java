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
        PeerScoring scoring = new PeerScoring("id1");

        Assert.assertTrue(calculator.hasGoodScore(scoring));
    }

    @Test
    public void scoringWithOneValidBlockHasGoodReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring("id1");
        scoring.updateScoring(EventType.VALID_BLOCK);

        Assert.assertTrue(calculator.hasGoodScore(scoring));
    }

    @Test
    public void scoringWithOneValidTransactionHasGoodReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring("id1");
        scoring.updateScoring(EventType.VALID_TRANSACTION);

        Assert.assertTrue(calculator.hasGoodScore(scoring));
    }

    @Test
    public void scoringWithOneInvalidBlockHasBadReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring("id1");
        scoring.updateScoring(EventType.INVALID_BLOCK);

        Assert.assertFalse(calculator.hasGoodScore(scoring));
    }

    @Test
    public void scoringWithOneInvalidTransactionHasNoBadReputation() {
        ScoringCalculator calculator = new ScoringCalculator();
        PeerScoring scoring = new PeerScoring("id1");
        scoring.updateScoring(EventType.INVALID_TRANSACTION);

        Assert.assertTrue(calculator.hasGoodScore(scoring));
    }
}
