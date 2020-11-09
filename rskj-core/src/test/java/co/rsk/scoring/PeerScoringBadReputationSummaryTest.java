package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

public class PeerScoringBadReputationSummaryTest {

    @Test
    public void equalsTest() {
        Assert.assertEquals(new PeerScoringBadReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0), new PeerScoringBadReputationSummary(
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0));
    }

    @Test
    public void equalsNullTest() {
        Assert.assertNotEquals(new PeerScoringBadReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0), null);
    }

    @Test
    public void equalsOtherClassTest() {
        Assert.assertNotEquals(new PeerScoringBadReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0), this);
    }

    @Test
    public void hashCodeTest() {
        int oneSummaryHashcode = (new PeerScoringBadReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0)).hashCode();
        int otherSummaryHashcode = (new PeerScoringBadReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0)).hashCode();
        int anotherSummaryHashcode = (new PeerScoringBadReputationSummary(1,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0)).hashCode();

        Assert.assertEquals(oneSummaryHashcode, otherSummaryHashcode);
        Assert.assertNotEquals(oneSummaryHashcode, anotherSummaryHashcode);
    }
}
