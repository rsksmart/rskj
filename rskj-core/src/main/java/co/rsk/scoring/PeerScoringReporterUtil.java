package co.rsk.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PeerScoringReporterUtil {

    private PeerScoringReporterUtil() {
    }

    public static PeerScoringBadReputationSummary buildBadReputationSummary(List<PeerScoringInformation> peerScoringInformationList) {
        List<PeerScoringInformation> badReputationList = badReputationList(peerScoringInformationList);

        return new PeerScoringBadReputationSummary(
                badReputationList.size(),
                sumBy(badReputationList, PeerScoringInformation::getSuccessfulHandshakes),
                sumBy(badReputationList, PeerScoringInformation::getFailedHandshakes),
                sumBy(badReputationList, PeerScoringInformation::getInvalidNetworks),
                sumBy(badReputationList, PeerScoringInformation::getRepeatedMessages),
                sumBy(badReputationList, PeerScoringInformation::getValidBlocks),
                sumBy(badReputationList, PeerScoringInformation::getValidTransactions),
                sumBy(badReputationList, PeerScoringInformation::getInvalidBlocks),
                sumBy(badReputationList, PeerScoringInformation::getInvalidTransactions),
                sumBy(badReputationList, PeerScoringInformation::getInvalidMessages),
                sumBy(badReputationList, PeerScoringInformation::getTimeoutMessages),
                sumBy(badReputationList, PeerScoringInformation::getUnexpectedMessages),
                sumBy(badReputationList, PeerScoringInformation::getInvalidHeader),
                sumBy(badReputationList, PeerScoringInformation::getScore),
                sumBy(badReputationList, PeerScoringInformation::getPunishments)
        );
    }

    @VisibleForTesting
    public static List<PeerScoringInformation> badReputationList(List<PeerScoringInformation> peerScoringInformationList) {
        if(peerScoringInformationList == null) {
            return new ArrayList<>();
        }
        
        return peerScoringInformationList.stream()
                .filter(p -> !p.getGoodReputation())
                .collect(Collectors.toList());
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

    public static String detailedBadReputationStatusString(List<PeerScoringInformation> peerScoringInformationList) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(badReputationList(peerScoringInformationList));
    }

    public static String badReputationSummaryString(List<PeerScoringInformation> peerScoringInformationList) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(buildBadReputationSummary(peerScoringInformationList));
    }
}
