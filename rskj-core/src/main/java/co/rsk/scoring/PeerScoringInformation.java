package co.rsk.scoring;

import com.google.common.annotations.VisibleForTesting;

/**
 * PeerScoringInformation is a simple class to expose
 * the recorded scoring information for a peer
 * <p>
 * Created by ajlopez on 12/07/2017.
 */
public class PeerScoringInformation {
    private final int successfulHandshakes;
    private final int failedHandshakes;
    private final int invalidNetworks;
    private final int repeatedMessages;
    private final int validBlocks;
    private final int validTransactions;
    private final int invalidBlocks;
    private final int invalidTransactions;
    private final int invalidMessages;
    private final int timeoutMessages;
    private final int unexpectedMessages;
    private final int invalidHeader;
    private final int score;
    private final int punishments;
    private final boolean goodReputation;
    private final String id;
    private final String type; //todo(techdebt) use an enum or constants

    public PeerScoringInformation(int successfulHandshakes, int failedHandshakes, int invalidNetworks,
                                  int repeatedMessages, int validBlocks, int validTransactions, int invalidBlocks,
                                  int invalidTransactions, int invalidMessages, int timeoutMessages,
                                  int unexpectedMessages, int invalidHeader, int score, int punishments,
                                  boolean goodReputation, String id, String type) {
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
        this.score = score;
        this.punishments = punishments;
        this.goodReputation = goodReputation;
        this.id = id;
        this.type = type;
    }

    public static PeerScoringInformation buildByScoring(PeerScoring scoring, String id, String type) {
        int successfulHandshakes = scoring.getEventCounter(EventType.SUCCESSFUL_HANDSHAKE);
        int failedHandshakes = scoring.getEventCounter(EventType.FAILED_HANDSHAKE);
        int invalidNetworks = scoring.getEventCounter(EventType.INVALID_NETWORK);
        int repeatedMessages = scoring.getEventCounter(EventType.REPEATED_MESSAGE);
        int validBlocks = scoring.getEventCounter(EventType.VALID_BLOCK);
        int validTransactions = scoring.getEventCounter(EventType.VALID_TRANSACTION);
        int invalidBlocks = scoring.getEventCounter(EventType.INVALID_BLOCK);
        int invalidTransactions = scoring.getEventCounter(EventType.INVALID_TRANSACTION);
        int invalidMessages = scoring.getEventCounter(EventType.INVALID_MESSAGE);
        int timeoutMessages = scoring.getEventCounter(EventType.TIMEOUT_MESSAGE);
        int unexpectedMessages = scoring.getEventCounter(EventType.UNEXPECTED_MESSAGE);
        int invalidHeader = scoring.getEventCounter(EventType.INVALID_HEADER);
        int score = scoring.getScore();
        int punishments = scoring.getPunishmentCounter();
        boolean goodReputation = scoring.hasGoodReputation();

        return new PeerScoringInformation(successfulHandshakes, failedHandshakes, invalidNetworks,
                repeatedMessages, validBlocks, validTransactions, invalidBlocks, invalidTransactions,
                invalidMessages, timeoutMessages, unexpectedMessages, invalidHeader, score, punishments,
                goodReputation, id, type);
    }

    public String getId() {
        return this.id;
    }

    @VisibleForTesting
    public String getType() { return this.type; }

    public boolean getGoodReputation() {
        return this.goodReputation;
    }

    public int getSuccessfulHandshakes() { return this.successfulHandshakes; }

    public int getFailedHandshakes() { return this.failedHandshakes; }

    public int getInvalidNetworks() { return this.invalidNetworks; }

    public int getRepeatedMessages() { return this.repeatedMessages; }

    public int getValidBlocks() {
        return this.validBlocks;
    }

    public int getScore() {
        return this.score;
    }

    public int getInvalidBlocks() {
        return this.invalidBlocks;
    }

    public int getValidTransactions() {
        return this.validTransactions;
    }

    public int getInvalidTransactions() {
        return this.invalidTransactions;
    }

    public int getInvalidMessages() {
        return this.invalidMessages;
    }

    public int getUnexpectedMessages() {
        return this.unexpectedMessages;
    }

    public int getTimeoutMessages() {
        return this.timeoutMessages;
    }

    public int getInvalidHeader() {
        return this.invalidHeader;
    }

    public int getPunishments() { return this.punishments; }

    public int goodReputationCount() {
        return getGoodReputation() ? 1 : 0;
    }

    public int badReputationCount() {
        return !getGoodReputation() ? 1 : 0;
    }
}


