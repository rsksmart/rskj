package co.rsk.scoring;

/**
 * PunishmentCalculator calculates the punishment duration
 * given the punishment parameters (@see PunishmentParameters)
 * <p>
 * Created by ajlopez on 10/07/2017.
 */
public class PunishmentCalculator {
    private PunishmentParameters parameters;

    public PunishmentCalculator(PunishmentParameters parameters) {
        this.parameters = parameters;
    }

    /**
     * Calculate the punishment duration (in milliseconds)
     * given the count of previous punishment and current peer score.
     *
     * The duration is incremented according the number of previous punishment
     * using an initial duration and a percentage increment
     *
     * The duration cannot be greater than the maximum duration specified in parameters
     * (0 = no maximum duration)
     *
     * @param punishmentCounter the count of previous punishment for a peer
     * @param score     the peer score
     *
     * @return  the punishment duration in milliseconds
     */
    public long calculate(int punishmentCounter, int score) {
        long result = this.parameters.getDuration();
        double rate = ((100.0 + this.parameters.getIncrementRate()) / 100);
        int counter = punishmentCounter;
        long maxDuration = this.parameters.getMaximumDuration();

        while (counter-- > 0) {
            result = (long)(result * rate);
            if (maxDuration > 0 && result > maxDuration)
                return maxDuration;
        }

        long duration;

        if (maxDuration > 0)
            duration = Math.min(this.parameters.getMaximumDuration(), result);
        else
            duration = result;

        if (score < 0)
            duration *= -score;

        return duration;
    }
}
