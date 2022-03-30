package co.rsk.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PeerScoringReporterUtilTest {

    @Test
    public void buildsReputationSummary() {
        List<PeerScoringInformation> peerScoringInformationList = new ArrayList<>(goodReputationPeers());
        peerScoringInformationList.addAll(badReputationPeers());
        peerScoringInformationList.addAll(goodReputationPeers());

        PeerScoringReputationSummary peerScoringReputationSummary =
                PeerScoringReporterUtil.buildReputationSummary(peerScoringInformationList);

        Assert.assertEquals(new PeerScoringReputationSummary(8, 32, 0,
                0, 40, 24,
                72, 8, 0,
                0, 0, 0,
                32, 0, 0,6,2), peerScoringReputationSummary);
    }

    @Test
    public void emptyDetailedStatusShouldMatchToEmptySummary() throws JsonProcessingException {
        List<PeerScoringInformation> peerScoringInformationList = new ArrayList<>();

        String detailedStatusResult = PeerScoringReporterUtil.detailedReputationString(peerScoringInformationList);
        String summaryResultString =  PeerScoringReporterUtil.reputationSummaryString(peerScoringInformationList);

        Assert.assertEquals("{\"count\":0,\"successfulHandshakes\":0,\"failedHandshakes\":0," +
                "\"invalidHeader\":0,\"validBlocks\":0,\"invalidBlocks\":0,\"validTransactions\":0," +
                "\"invalidTransactions\":0,\"invalidNetworks\":0,\"invalidMessages\":0," +
                "\"repeatedMessages\":0,\"timeoutMessages\":0,\"unexpectedMessages\":0," +
                "\"peersTotalScore\":0,\"punishments\":0,\"goodReputationCount\":0," +
                "\"badReputationCount\":0}", summaryResultString);
        Assert.assertEquals("[]", detailedStatusResult);
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
        return new PeerScoringInformation(4, 0, 0,
                5, 3, 9, 1,
                0, 0, 0, 0,
                4, 0, 0, goodReputation,  0, id, "node");
    }
}
