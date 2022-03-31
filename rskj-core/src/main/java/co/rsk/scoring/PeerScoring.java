package co.rsk.scoring;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    private static final Logger logger = LoggerFactory.getLogger("peerScoring");

    private final String peerKey;
    private final boolean punishmentEnabled;

    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();
    private final int[] counters = new int[EventType.values().length];
    private boolean goodReputation = true;
    private long timeLostGoodReputation;
    private long punishmentTime;
    private int punishmentCounter;
    private int score;

    public PeerScoring(String peerKey) {
        this(peerKey, true);
    }

    public PeerScoring(String peerKey, boolean punishmentEnabled) {
        this.peerKey = peerKey;
        this.punishmentEnabled = punishmentEnabled;
    }

    /**
     * Updates scoring according to the received event
     * Current implementation has a counter by event type.
     * The score is incremented or decremented, according to the kind of the event.
     * Some negative events alters the score to a negative level, without
     * taking into account its previous positive value
     *
     * @param evt An event type @see EventType
     */
    public void updateScoring(EventType evt) {
        try {
            rwlock.writeLock().lock();

            counters[evt.ordinal()]++;

            switch (evt) {
                case INVALID_NETWORK:
                case INVALID_BLOCK:
                case INVALID_TRANSACTION:
                case INVALID_MESSAGE:
                case INVALID_HEADER:
                    if (score > 0) {
                        score = 0;
                    }
                    score--;
                    break;
                case UNEXPECTED_MESSAGE:
                case FAILED_HANDSHAKE:
                case SUCCESSFUL_HANDSHAKE:
                case REPEATED_MESSAGE:
                case TIMEOUT_MESSAGE:
                    break;

                default:
                    if (score >= 0) {
                        score++;
                    }
                    break;
            }

            logger.trace("New score for node {} after {} is {} ", evt, peerKey, score);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    /**
     * Returns the current computed score.
     * The score is calculated based on previous event recording.
     *
     * @return An integer number, the level of score. Positive value is associated
     * with a good reputation. Negative values indicates a possible punishment.
     */
    public int getScore() {
        try {
            rwlock.readLock().lock();
            return score;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    /**
     * Returns the count of events given a event type.
     *
     * @param evt Event Type (@see EventType)
     * @return The count of events of the specefied type
     */
    public int getEventCounter(EventType evt) {
        try {
            rwlock.readLock().lock();
            return counters[evt.ordinal()];
        } finally {
            rwlock.readLock().unlock();
        }
    }

    /**
     * Returns the count of all events
     *
     * @return The total count of events
     */
    public int getTotalEventCounter() {
        try {
            rwlock.readLock().lock();
            int counter = 0;

            for (int j : counters) {
                counter += j;
            }

            return counter;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    /**
     * Returns <tt>true</tt> if there is no event recorded yet.
     *
     * @return <tt>true</tt> if there is no event
     */
    @VisibleForTesting
    public boolean isEmpty() {
        try {
            rwlock.readLock().lock();
            return getTotalEventCounter() == 0;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    /**
     * Refreshes peer reputation finishing punishment if enough time has passed
     *
     * @return <tt>true</tt> if reputation is good after refresh or <tt>false</tt> otherwise
     */
    public boolean refreshReputationAndPunishment() {
        try {
            rwlock.writeLock().lock();
            if (this.goodReputation) {
                return true;
            }

            if (this.punishmentTime > 0 && this.timeLostGoodReputation > 0 && getPunishedUntil() <= System.currentTimeMillis()) {
                this.endPunishment();
            }

            logger.trace("Reputation for node {} is {}", peerKey, this.goodReputation ? "good" : "bad");

            return this.goodReputation;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    /**
     * Starts the punishment, with specified duration
     * Changes the reputation to not good
     * Increments the punishment counter
     *
     * @param expirationTime punishment duration in milliseconds
     */
    public void startPunishment(long expirationTime) {
        if (!punishmentEnabled) {
            return;
        }

        logger.debug("Punishing node {} for {} min", peerKey, expirationTime / 1000 / 60);

        try {
            rwlock.writeLock().lock();
            this.goodReputation = false;
            this.punishmentTime = expirationTime;
            this.punishmentCounter++;
            this.timeLostGoodReputation = System.currentTimeMillis();
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    /**
     * Ends the punishment
     * Clear the event counters
     */
    private void endPunishment() {
        if (!punishmentEnabled) {
            return;
        }

        logger.debug("Finishing punishment for node {}", peerKey);

        //Check locks before doing this function public
        for (int i = 0; i < counters.length; i++) {
            this.counters[i] = 0;
        }
        this.goodReputation = true;
        this.punishmentTime = 0;
        this.timeLostGoodReputation = 0;
        this.score = 0;
    }

    @VisibleForTesting
    public long getPunishmentTime() {
        return this.punishmentTime;
    }

    /**
     * Returns the number of punishment suffered by this peer.
     *
     * @return the counter of punishments
     */
    public int getPunishmentCounter() {
        return this.punishmentCounter;
    }

    @VisibleForTesting
    public long getTimeLostGoodReputation() {
        return this.timeLostGoodReputation;
    }

    /**
     * Gets the timestamp (ms) for the punishment to finish
     * @return the timestamp for the punishment to finish, 0 if not punished or punishment disabled
     */
    public long getPunishedUntil() {
        return this.punishmentTime + this.timeLostGoodReputation;
    }

    public interface Factory {
        PeerScoring newInstance(String peerKey);
    }
}
