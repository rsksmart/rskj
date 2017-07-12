package co.rsk.scoring;

import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 27/06/2017.
 */
public class PeerScoring {
    private Map<EventType, Integer> counters = new HashMap<>();
    private boolean goodReputation = true;
    private long timeLostGoodReputation;
    private long punishmentTime;
    private int punishmentCounter;
    private int score;

    public void recordEvent(EventType evt) {
        if (!counters.containsKey(evt))
            counters.put(evt, new Integer(1));
        else
            counters.put(evt, new Integer(counters.get(evt).intValue() + 1));

        switch (evt) {
            case INVALID_NETWORK:
            case INVALID_BLOCK:
            case INVALID_TRANSACTION:
                if (score > 0)
                    score = 0;
                score--;
                break;

            case FAILED_HANDSHAKE:
            case SUCCESSFUL_HANDSHAKE:
            case REPEATED_MESSAGE:
                break;

            default:
                if (score >= 0)
                    score++;
                break;
        }
    }

    public int getScore() {
        return score;
    }

    public int getEventCounter(EventType evt) {
        if (!counters.containsKey(evt))
            return 0;

        return counters.get(evt).intValue();
    }

    public int getTotalEventCounter() {
        int counter = 0;

        for (Map.Entry<EventType, Integer> entry : counters.entrySet())
            counter += entry.getValue().intValue();

        return counter;
    }

    public boolean isEmpty() {
        return counters.isEmpty();
    }

    public boolean hasGoodReputation() {
        if (this.goodReputation)
            return true;

        if (this.punishmentTime > 0 && this.timeLostGoodReputation > 0 && this.punishmentTime + this.timeLostGoodReputation <= System.currentTimeMillis())
            this.endPunishment();

        return this.goodReputation;
    }

    public void startPunishment(long expirationTime) {
        this.goodReputation = false;
        this.punishmentTime = expirationTime;
        this.punishmentCounter++;
        this.timeLostGoodReputation = System.currentTimeMillis();
    }

    public void endPunishment() {
        this.counters.clear();
        this.goodReputation = true;
        this.timeLostGoodReputation = 0;
    }

    @VisibleForTesting
    public long getPunishmentTime() {
        return this.punishmentTime;
    }

    public int getPunishmentCounter() {
        return this.punishmentCounter;
    }

    @VisibleForTesting
    public long getTimeLostGoodReputation() {
        return this.timeLostGoodReputation;
    }
}
