package co.rsk.scoring;

/**
 * Created by ajlopez on 10/07/2017.
 */
public class PunishmentCalculator {
    private PunishmentParameters parameters;

    public PunishmentCalculator(PunishmentParameters parameters) {
        this.parameters = parameters;
    }

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
