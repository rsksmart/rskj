package co.rsk.scoring;

import java.util.Arrays;

/**
 * This is a presentational object of peers with bad reputation
 * It's used to expose a json rpc message (sco_badReputationSummary())
 */
public class PeerScoringBadReputationSummary {
    private int count;
    private int successfulHandshakes;
    private int failedHandshakes;
    private int invalidNetworks;
    private int repeatedMessages;
    private int validBlocks;
    private int validTransactions;
    private int invalidBlocks;
    private int invalidTransactions;
    private int invalidMessages;
    private int timeoutMessages;
    private int unexpectedMessages;
    private int invalidHeader;
    private int peersTotalScore;
    private int punishments;

    public PeerScoringBadReputationSummary(int count,
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
                                           int punishments) {
        this.count = count;
        this.successfulHandshakes = successfulHandshakes;
        this.failedHandshakes = failedHandshakes;
        this.invalidNetworks = invalidNetworks;
        this.repeatedMessages = repeatedMessages;
        this.validBlocks = validBlocks;
        this.validTransactions = validTransactions;
        this.invalidBlocks = invalidBlocks;
        this.invalidTransactions = invalidTransactions;
        this.invalidMessages = invalidMessages;
        this.timeoutMessages = timeoutMessages;
        this.unexpectedMessages = unexpectedMessages;
        this.invalidHeader = invalidHeader;
        this.peersTotalScore = peersTotalScore;
        this.punishments = punishments;
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

    @Override
    public boolean equals(Object object) {
        if(!(object instanceof PeerScoringBadReputationSummary)) {
            return false;
        }
        PeerScoringBadReputationSummary p = (PeerScoringBadReputationSummary) object;

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
                repeatedMessages, validBlocks).hashCode();
    }
}
