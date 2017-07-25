package co.rsk.scoring;

/**
 * PeerScoringInformation is a simple class to exposte
 * the recorded scoring information for a peer
 * <p>
 * Created by ajlopez on 12/07/2017.
 */
public class PeerScoringInformation {
    private int successfulHandshakes;
    private int failedHandshakes;
    private int invalidNetworks;
    private int repeatedMessages;
    private int validBlocks;
    private int validTransactions;
    private int invalidBlocks;
    private int invalidTransactions;
    private int score;
    private int punishments;
    private boolean goodReputation;
    private String id;
    private String type;

    public PeerScoringInformation(PeerScoring scoring, String id, String type) {
        this.type = type;
        this.goodReputation = scoring.hasGoodReputation();
        this.successfulHandshakes = scoring.getEventCounter(EventType.SUCCESSFUL_HANDSHAKE);
        this.failedHandshakes = scoring.getEventCounter(EventType.FAILED_HANDSHAKE);
        this.invalidNetworks = scoring.getEventCounter(EventType.INVALID_NETWORK);
        this.repeatedMessages = scoring.getEventCounter(EventType.REPEATED_MESSAGE);
        this.validBlocks = scoring.getEventCounter(EventType.VALID_BLOCK);
        this.invalidBlocks = scoring.getEventCounter(EventType.INVALID_BLOCK);
        this.validTransactions = scoring.getEventCounter(EventType.VALID_TRANSACTION);
        this.invalidTransactions = scoring.getEventCounter(EventType.INVALID_TRANSACTION);
        this.score = scoring.getScore();
        this.punishments = scoring.getPunishmentCounter();
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

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

    public int getPunishments() { return this.punishments; }
}
