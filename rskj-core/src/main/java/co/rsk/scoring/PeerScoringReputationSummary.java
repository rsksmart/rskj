package co.rsk.scoring;

import java.util.Arrays;

/**
 * This is a presentational object
 * It's used to expose a json rpc message (sco_reputationSummary())
 */
public class PeerScoringReputationSummary {
    private int count;
    private int successfulHandshakes;
    private int failedHandshakes;
    private int invalidHeader;
    private int validBlocks;
    private int invalidBlocks;
    private int validTransactions;
    private int invalidTransactions;
    private int invalidNetworks;
    private int invalidMessages;
    private int repeatedMessages;
    private int timeoutMessages;
    private int unexpectedMessages;
    private int peersTotalScore;
    private int punishments;
    private int goodReputationCount;
    private int badReputationCount;

    public PeerScoringReputationSummary(int count,
                                        int successfulHandshakes,
                                        int failedHandshakes,
                                        int invalidNetworks,
                                        int repeatedMessages,
                                        int validBlocks,
                                        int validTransactions,
                                        int invalidBlocks,
                                        int invalidTransactions,
                                        int invalidMessages,
                                        int timeoutMessages,
                                        int unexpectedMessages,
                                        int invalidHeader,
                                        int peersTotalScore,
                                        int punishments,
                                        int goodReputationCount,
                                        int badReputationCount) {
        this.count = count;
        this.successfulHandshakes = successfulHandshakes;
        this.failedHandshakes = failedHandshakes;
        this.invalidHeader = invalidHeader;
        this.validBlocks = validBlocks;
        this.invalidBlocks = invalidBlocks;
        this.validTransactions = validTransactions;
        this.invalidTransactions = invalidTransactions;
        this.invalidNetworks = invalidNetworks;
        this.invalidMessages = invalidMessages;
        this.repeatedMessages = repeatedMessages;
        this.timeoutMessages = timeoutMessages;
        this.unexpectedMessages = unexpectedMessages;
        this.peersTotalScore = peersTotalScore;
        this.punishments = punishments;
        this.goodReputationCount = goodReputationCount;
        this.badReputationCount = badReputationCount;
    }

    public int getCount() {
        return count;
    }

    public int getSuccessfulHandshakes() {
        return successfulHandshakes;
    }

    public int getFailedHandshakes() {
        return failedHandshakes;
    }

    public int getInvalidNetworks() {
        return invalidNetworks;
    }

    public int getRepeatedMessages() {
        return repeatedMessages;
    }

    public int getValidBlocks() {
        return validBlocks;
    }

    public int getValidTransactions() {
        return validTransactions;
    }

    public int getInvalidBlocks() {
        return invalidBlocks;
    }

    public int getInvalidTransactions() {
        return invalidTransactions;
    }

    public int getInvalidMessages() {
        return invalidMessages;
    }

    public int getTimeoutMessages() {
        return timeoutMessages;
    }

    public int getUnexpectedMessages() {
        return unexpectedMessages;
    }

    public int getInvalidHeader() {
        return invalidHeader;
    }

    public int getPeersTotalScore() {
        return peersTotalScore;
    }

    public int getPunishments() {
        return punishments;
    }

    public int getGoodReputationCount() {
        return goodReputationCount;
    }

    public int getBadReputationCount() {
        return badReputationCount;
    }

    @Override
    public boolean equals(Object object) {
        if(!(object instanceof PeerScoringReputationSummary)) {
            return false;
        }
        PeerScoringReputationSummary p = (PeerScoringReputationSummary) object;

        return getCount() == p.getCount() &&
                getFailedHandshakes() == p.getFailedHandshakes() &&
                getInvalidMessages() == p.getInvalidMessages() &&
                getInvalidNetworks() == p.getInvalidNetworks() &&
                getInvalidHeader() == p.getInvalidHeader() &&
                getInvalidBlocks() == p.getInvalidBlocks() &&
                getInvalidTransactions() == p.getInvalidTransactions() &&
                getSuccessfulHandshakes() == p.getSuccessfulHandshakes() &&
                getValidTransactions() == p.getValidTransactions() &&
                getPunishments() == p.getPunishments() &&
                getPeersTotalScore() == p.getPeersTotalScore() &&
                getUnexpectedMessages() == p.getUnexpectedMessages() &&
                getTimeoutMessages() == p.getTimeoutMessages() &&
                getRepeatedMessages() == p.getRepeatedMessages() &&
                getValidBlocks() == p.getValidBlocks();
    }

    @Override
    public int hashCode() {
        return Arrays.asList(count, failedHandshakes, invalidMessages, invalidNetworks,
                invalidHeader, invalidBlocks, invalidTransactions, successfulHandshakes,
                validTransactions, punishments, peersTotalScore, unexpectedMessages, timeoutMessages,
                repeatedMessages, validBlocks, goodReputationCount, badReputationCount).hashCode();
    }
}
