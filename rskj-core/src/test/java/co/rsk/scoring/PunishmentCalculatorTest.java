package co.rsk.scoring;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 10/07/2017.
 */
public class PunishmentCalculatorTest {
    @Test
    public void calculatePunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 1000));

        Assertions.assertEquals(100, calculator.calculate(0, 0));
    }

    @Test
    public void calculatePunishmentTimeWithNegativeScore() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 1000));

        Assertions.assertEquals(200, calculator.calculate(0, -2));
    }

    @Test
    public void calculateSecondPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 1000));

        Assertions.assertEquals(110, calculator.calculate(1, 0));
    }

    @Test
    public void calculateThirdPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 1000));

        Assertions.assertEquals(121, calculator.calculate(2, 0));
    }

    @Test
    public void calculateThirdPunishmentTimeAndNegativeScore() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 1000));

        Assertions.assertEquals(242, calculator.calculate(2, -2));
    }

    @Test
    public void calculateUsingMaxPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 120));

        Assertions.assertEquals(120, calculator.calculate(2, 0));
    }

    @Test
    public void calculateUsingNoMaxPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 0));

        Assertions.assertEquals(121, calculator.calculate(2, 0));
    }
}
