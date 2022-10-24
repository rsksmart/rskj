package co.rsk.scoring;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 10/07/2017.
 */
class PunishmentCalculatorTest {
    @Test
    void calculatePunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 1000));

        Assertions.assertEquals(100, calculator.calculate(0, 0));
    }

    @Test
    void calculatePunishmentTimeWithNegativeScore() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 1000));

        Assertions.assertEquals(200, calculator.calculate(0, -2));
    }

    @Test
    void calculateSecondPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 1000));

        Assertions.assertEquals(110, calculator.calculate(1, 0));
    }

    @Test
    void calculateThirdPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 1000));

        Assertions.assertEquals(121, calculator.calculate(2, 0));
    }

    @Test
    void calculateThirdPunishmentTimeAndNegativeScore() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 1000));

        Assertions.assertEquals(242, calculator.calculate(2, -2));
    }

    @Test
    void calculateUsingMaxPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 120));

        Assertions.assertEquals(120, calculator.calculate(2, 0));
    }

    @Test
    void calculateUsingNoMaxPunishmentTime() {
        PunishmentCalculator calculator = new PunishmentCalculator(new PunishmentParameters(100, 10, 0));

        Assertions.assertEquals(121, calculator.calculate(2, 0));
    }
}
