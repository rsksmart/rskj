package co.rsk.scoring;

import static java.lang.Math.multiplyExact;

/**
 * PunishmentCalculator calculates the punishment duration
 * given the punishment parameters (@see PunishmentParameters)
 * <p>
 * Created by ajlopez on 10/07/2017.
 */
public class PunishmentCalculator {
    private final PunishmentParameters parameters;

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
        long rate = 100L + this.parameters.getIncrementRate();
        int counter = punishmentCounter;
        long maxDuration = this.parameters.getMaximumDuration();

        while (counter-- > 0) {
            result = multiplyExact(result, rate) / 100;
            if (maxDuration > 0 && result > maxDuration) {
                return maxDuration;
            }
        }

        if (score < 0) {
            result *= -score;
        }

        return maxDuration > 0 ? Math.min(maxDuration, result) : result;
    }
}
