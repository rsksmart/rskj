package co.rsk.scoring;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PeerScoringReputationSummaryTest {

    @Test
    void equalsTest() {
        Assertions.assertEquals(new PeerScoringReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0), new PeerScoringReputationSummary(
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0));
    }

    @Test
    void equalsNullTest() {
        Assertions.assertNotEquals(null, new PeerScoringReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0));
    }

    @Test
    void equalsOtherClassTest() {
        Assertions.assertNotEquals(new PeerScoringReputationSummary(0,0,0,0,
                0,0,0,0,
                0,0,0,0,
                0,0,0,0,0), this);
    }

    @Test
    void hashCodeTest() {
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

        Assertions.assertEquals(oneSummaryHashcode, otherSummaryHashcode);
        Assertions.assertNotEquals(oneSummaryHashcode, anotherSummaryHashcode);
    }
}
