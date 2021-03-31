package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

public class PeerScoringReputationSummaryTest {

    @Test
    public void equalsTest() {
        Assert.assertEquals(new PeerScoringReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0), new PeerScoringReputationSummary(
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0));
    }

    @Test
    public void equalsNullTest() {
        Assert.assertNotEquals(new PeerScoringReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0), null);
    }

    @Test
    public void equalsOtherClassTest() {
        Assert.assertNotEquals(new PeerScoringReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0), this);
    }

    @Test
    public void hashCodeTest() {
        int oneSummaryHashcode = (new PeerScoringReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0)).hashCode();
        int otherSummaryHashcode = (new PeerScoringReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0)).hashCode();
        int anotherSummaryHashcode = (new PeerScoringReputationSummary(1,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0)).hashCode();

        Assert.assertEquals(oneSummaryHashcode, otherSummaryHashcode);
        Assert.assertNotEquals(oneSummaryHashcode, anotherSummaryHashcode);
    }
}
