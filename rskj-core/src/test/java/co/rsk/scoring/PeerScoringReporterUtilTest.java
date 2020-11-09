package co.rsk.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PeerScoringReporterUtilTest {

    @Test
    public void buildsBadReputationSummary() {
        List<PeerScoringInformation> peerScoringInformationList = new ArrayList<>(goodReputationPeers());
        peerScoringInformationList.addAll(badReputationPeers());
        peerScoringInformationList.addAll(goodReputationPeers());

        PeerScoringBadReputationSummary peerScoringBadReputationSummary =
                PeerScoringReporterUtil.buildBadReputationSummary(peerScoringInformationList);

        Assert.assertEquals(new PeerScoringBadReputationSummary(2, 8, 0,
                0, 10, 6,
                2, 18, 0,
                0, 0, 0,
                8, 0, 0), peerScoringBadReputationSummary);
    }

    @Test
    public void emptyDetailedStatusShouldMatchToEmptySummary() throws JsonProcessingException {
        List<PeerScoringInformation> peerScoringInformationList = goodReputationPeers();

        String detailedStatusResult = PeerScoringReporterUtil.detailedBadReputationStatusString(peerScoringInformationList);
        String summaryResultString =  PeerScoringReporterUtil.badReputationSummaryString(peerScoringInformationList);

        Assert.assertEquals("{\"count\":0,\"successfulHandshakes\":0,\"failedHandshakes\":0," +
                "\"invalidNetworks\":0,\"repeatedMessages\":0,\"validBlocks\":0," +
                "\"validTransactions\":0,\"invalidBlocks\":0," +
                "\"invalidTransactions\":0,\"invalidMessages\":0," +
                "\"timeoutMessages\":0,\"unexpectedMessages\":0," +
                "\"invalidHeader\":0,\"peersTotalScore\":0," +
                "\"punishments\":0}", summaryResultString);
        Assert.assertEquals("[]", detailedStatusResult);
    }

    @Test
    public void badReputationListByNull() {
        Assert.assertEquals(new ArrayList(), PeerScoringReporterUtil.badReputationList(null));
    }

    private List<PeerScoringInformation> badReputationPeers() {
        return Arrays.asList(buildPeerScoringInformation("4", false)
                , buildPeerScoringInformation("5", false));
    }

    private List<PeerScoringInformation> goodReputationPeers() {
        return Arrays.asList(buildPeerScoringInformation("1", true),
                buildPeerScoringInformation("2", true),
                buildPeerScoringInformation("3", true)
        );
    }

    private PeerScoringInformation buildPeerScoringInformation(String id, boolean goodReputation) {
        return new PeerScoringInformation("node", goodReputation,
                4, 0, 0,
                5, 3, 9, 1,
                0, 0, 0, 0,
                4, 0, 0, id);
    }
}
