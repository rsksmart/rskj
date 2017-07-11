package co.rsk.scoring;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 10/07/2017.
 */
public class PunishmentCalculatorTest {
    @Test
    public void calculatePunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator();

        Assert.assertEquals(100, calculator.calculate(100, 1000, 10, 0, 0));
    }

    @Test
    public void calculatePunishmentTimeWithNegativeScore() {
        PunishmentCalculator calculator = new PunishmentCalculator();

        Assert.assertEquals(200, calculator.calculate(100, 1000, 10, 0, -2));
    }

    @Test
    public void calculateSecondPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator();

        Assert.assertEquals(110, calculator.calculate(100, 1000, 10, 1, 0));
    }

    @Test
    public void calculateThirdPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator();

        Assert.assertEquals(121, calculator.calculate(100, 1000, 10, 2, 0));
    }

    @Test
    public void calculateThirdPunishmentTimeAndNegativeScore() {
        PunishmentCalculator calculator = new PunishmentCalculator();

        Assert.assertEquals(242, calculator.calculate(100, 1000, 10, 2, -2));
    }

    @Test
    public void calculateUsingMaxPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator();

        Assert.assertEquals(120, calculator.calculate(100, 120, 10, 2, 0));
    }
}
