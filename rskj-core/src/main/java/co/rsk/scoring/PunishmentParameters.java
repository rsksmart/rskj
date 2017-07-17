package co.rsk.scoring;

/**
 * PunishmentParameters has the punishment parameters
 * (initial duration, incremental percentage, maximum duration)
 * <p>
 * Created by ajlopez on 12/07/2017.
 */
public class PunishmentParameters {
    private long duration;
    private int incrementRate;
    private long maximumDuration;

    public PunishmentParameters(long duration, int incrementRate, long maximumDuration) {
        this.duration = duration;
        this.incrementRate = incrementRate;
        this.maximumDuration = maximumDuration;
    }

    /**
     * Returns the initial punishment duration
     *
     * @return duration in milliseconds
     */
    public long getDuration() { return this.duration; }

    /**
     * Returns the incremental percentage
     *
     * @return the percentage of increment to be applied to each new punishment
     */
    public int getIncrementRate() { return this.incrementRate; }

    /**
     * Returns the maximum duration to be applied
     *
     * @return the maximum duration in milliseconds
     * (0 = no maximum)
     */
    public long getMaximumDuration() { return this.maximumDuration; }
}
