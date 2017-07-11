package co.rsk.scoring;

/**
 * Created by ajlopez on 10/07/2017.
 */
public class PunishmentCalculator {
    public long calculate(long punishmentDuration, long maxTime, int percentage, int counter) {
        long result = punishmentDuration;
        double rate = ((100.0 + percentage) / 100);

        while (counter-- > 0) {
            result = (long)(result * rate);
            if (result > maxTime)
                return maxTime;
        }

        return Math.min(maxTime, result);
    }
}
