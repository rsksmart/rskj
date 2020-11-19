package co.rsk.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class PeerScoringReporterUtil {

    private PeerScoringReporterUtil() {
    }

    public static PeerScoringReputationSummary buildReputationSummary(List<PeerScoringInformation> peerScoringInformationList) {

        return new PeerScoringReputationSummary(
                peerScoringInformationList.size(),
                sumBy(peerScoringInformationList, PeerScoringInformation::getSuccessfulHandshakes),
                sumBy(peerScoringInformationList, PeerScoringInformation::getFailedHandshakes),
                sumBy(peerScoringInformationList, PeerScoringInformation::getInvalidNetworks),
                sumBy(peerScoringInformationList, PeerScoringInformation::getRepeatedMessages),
                sumBy(peerScoringInformationList, PeerScoringInformation::getValidBlocks),
                sumBy(peerScoringInformationList, PeerScoringInformation::getValidTransactions),
                sumBy(peerScoringInformationList, PeerScoringInformation::getInvalidBlocks),
                sumBy(peerScoringInformationList, PeerScoringInformation::getInvalidTransactions),
                sumBy(peerScoringInformationList, PeerScoringInformation::getInvalidMessages),
                sumBy(peerScoringInformationList, PeerScoringInformation::getTimeoutMessages),
                sumBy(peerScoringInformationList, PeerScoringInformation::getUnexpectedMessages),
                sumBy(peerScoringInformationList, PeerScoringInformation::getInvalidHeader),
                sumBy(peerScoringInformationList, PeerScoringInformation::getScore),
                sumBy(peerScoringInformationList, PeerScoringInformation::getPunishments),
                sumBy(peerScoringInformationList, PeerScoringInformation::goodReputationCount),
                sumBy(peerScoringInformationList, PeerScoringInformation::badReputationCount)
        );
    }

    private static int sumBy(List<PeerScoringInformation> peerScoringInformationList, Function<PeerScoringInformation, Integer> mapper) {
        if(peerScoringInformationList == null || peerScoringInformationList.isEmpty()) {
            return 0;
        }
        Optional<Integer> result = peerScoringInformationList.stream()
                .map(mapper)
                .reduce((a, b) -> a + b);

        return result.isPresent() ? result.get() : 0;
    }

    public static String detailedReputationString(List<PeerScoringInformation> peerScoringInformationList) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(peerScoringInformationList);
    }

    public static String reputationSummaryString(List<PeerScoringInformation> peerScoringInformationList) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(buildReputationSummary(peerScoringInformationList));
    }
}
