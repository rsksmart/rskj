package co.rsk.scoring;

import com.google.common.annotations.VisibleForTesting;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * PeerScoring records the events associated with a peer
 * identified by node id or IP address (@see PeerScoringManager)
 * An integer score value is calculated based on recorded events.
 * Also, a good reputation flag is calculated.
 * The number of punishment is recorded, as well the initial punishment time and its duration.
 * When the punishment expires, the good reputation is restored and most counters are reset to zero
 * <p>
 * Created by ajlopez on 27/06/2017.
 */
public class PeerScoring {
    private Map<EventType, Integer> counters = new HashMap<>();
    private boolean goodReputation = true;
    private long timeLostGoodReputation;
    private long punishmentTime;
    private int punishmentCounter;
    private int score;

    /**
     * Records an event.
     * Current implementation has a counter by event type.
     * The score is incremented or decremented, acoording to the kind of the event.
     * Some negative events alters the score to a negative level, without
     * taking into account its previous positive value
     *
     * @param evt       An event type @see EventType
     */
    public void recordEvent(EventType evt) {
        if (!counters.containsKey(evt))
            counters.put(evt, 1);
        else
            counters.put(evt, counters.get(evt).intValue() + 1);

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

    /**
     * Returns the current computed score.
     * The score is calculated based on previous event recording.
     *
     * @return  An integer number, the level of score. Positive value is associated
     *          with a good reputation. Negative values indicates a possible punishment.
     */
    public int getScore() {
        return score;
    }

    /**
     * Returns the count of events given a event type.
     *
     * @param evt       Event Type (@see EventType)
     *
     * @return  The count of events of the specefied type
     */
    public int getEventCounter(EventType evt) {
        if (!counters.containsKey(evt))
            return 0;

        return counters.get(evt).intValue();
    }

    /**
     * Returns the count of all events
     *
     * @return  The total count of events
     */
    public int getTotalEventCounter() {
        int counter = 0;

        for (Map.Entry<EventType, Integer> entry : counters.entrySet())
            counter += entry.getValue().intValue();

        return counter;
    }

    /**
     * Returns <tt>true</tt> if there is no event recorded yet.
     *
     * @return <tt>true</tt> if there is no event
     */
    public boolean isEmpty() {
        return counters.isEmpty();
    }

    /**
     * Returns <tt>true</tt> if the peer has good reputation.
     * Returns <tt>false</tt> if not.
     *
     * @return <tt>true</tt> or <tt>false</tt>
     */
    public boolean hasGoodReputation() {
        if (this.goodReputation)
            return true;

        if (this.punishmentTime > 0 && this.timeLostGoodReputation > 0 && this.punishmentTime + this.timeLostGoodReputation <= System.currentTimeMillis())
            this.endPunishment();

        return this.goodReputation;
    }

    /**
     * Starts the punishment, with specified duration
     * Changes the reputation to not good
     * Increments the punishment counter
     *
     * @param   expirationTime  punishment duration in milliseconds
     */
    public void startPunishment(long expirationTime) {
        this.goodReputation = false;
        this.punishmentTime = expirationTime;
        this.punishmentCounter++;
        this.timeLostGoodReputation = System.currentTimeMillis();
    }

    /**
     * Ends the punishment
     * Clear the event counters
     *
     */
    public void endPunishment() {
        this.counters.clear();
        this.goodReputation = true;
        this.timeLostGoodReputation = 0;
    }

    @VisibleForTesting
    public long getPunishmentTime() {
        return this.punishmentTime;
    }

    /**
     * Returns the number of punishment suffered by this peer.
     *
     * @return      the counter of punishments
     */
    public int getPunishmentCounter() {
        return this.punishmentCounter;
    }

    @VisibleForTesting
    public long getTimeLostGoodReputation() {
        return this.timeLostGoodReputation;
    }
}
