package co.rsk.scoring;

/**
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

    public long getDuration() { return this.duration; }

    public int getIncrementRate() { return this.incrementRate; }

    public long getMaximumDuration() { return this.maximumDuration; }
}
