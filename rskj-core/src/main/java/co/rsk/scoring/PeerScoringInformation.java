package co.rsk.scoring;

/**
 * Created by ajlopez on 12/07/2017.
 */
public class PeerScoringInformation {
    private int validBlocks;
    private int validTransactions;
    private int invalidBlocks;
    private int invalidTransactions;
    private int score;
    private int punishments;
    private boolean goodReputation;
    private String id;

    public PeerScoringInformation(PeerScoring scoring, String id) {
        this.goodReputation = scoring.hasGoodReputation();
        this.validBlocks = scoring.getEventCounter(EventType.VALID_BLOCK);
        this.invalidBlocks = scoring.getEventCounter(EventType.INVALID_BLOCK);
        this.validTransactions = scoring.getEventCounter(EventType.VALID_TRANSACTION);
        this.invalidTransactions = scoring.getEventCounter(EventType.INVALID_TRANSACTION);
        this.score = scoring.getScore();
        this.punishments = scoring.getPunishmentCounter();
        this.id = id;
    }

    public boolean getGoodReputation() {
        return this.goodReputation;
    }

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

    public String getId() {
        return this.id;
    }
}
