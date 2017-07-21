package co.rsk.net.discovery;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 21/07/2017.
 */
public class ScoreCalculatorTest {
    @Test
    public void createWithoutPeerScoringManager() {
        ScoreCalculator calculator = new ScoreCalculator(null);

        Assert.assertEquals(0, calculator.calculateScore(null));
    }
}
