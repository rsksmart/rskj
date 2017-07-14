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

        while (counter-- > 0) {
            result = (long)(result * rate);
            if (result > this.parameters.getMaximumDuration())
                return this.parameters.getMaximumDuration();
        }

        long duration = Math.min(this.parameters.getMaximumDuration(), result);

        if (score < 0)
            duration *= -score;

        return duration;
    }
}
