package co.rsk.scoring;

/**
 * Created by ajlopez on 10/07/2017.
 */
public class PunishmentCalculator {
    public long calculate(long punishmentDuration, long maxDuration, int percentage, int punishmentCounter, int score) {
        long result = punishmentDuration;
        double rate = ((100.0 + percentage) / 100);

        while (punishmentCounter-- > 0) {
            result = (long)(result * rate);
            if (result > maxDuration)
                return maxDuration;
        }

        long duration = Math.min(maxDuration, result);

        if (score < 0)
            duration *= -score;

        return duration;
    }
}
