package co.rsk.peg.union;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UnionResponseCodeTest {

    @Test
    void getCode_whenSuccess_ok() {
        // Arrange
        Assertions.assertEquals(0, UnionResponseCode.SUCCESS.getCode());
    }

    @Test
    void getCode_whenGenericError_ok() {
        // Arrange
        Assertions.assertEquals(-10, UnionResponseCode.GENERIC_ERROR.getCode());
    }

    @Test
    void getCode_whenUnauthorizedCaller_ok() {
        // Arrange
        Assertions.assertEquals(-1, UnionResponseCode.UNAUTHORIZED_CALLER.getCode());
    }

    @Test
    void getCode_whenInvalidValue_ok() {
        // Arrange
        Assertions.assertEquals(-2, UnionResponseCode.INVALID_VALUE.getCode());
    }

    @Test
    void getCode_whenAlreadyPaused_ok() {
        // Arrange
        Assertions.assertEquals(-3, UnionResponseCode.ALREADY_PAUSED.getCode());
    }

    @Test
    void getCode_whenAlreadyUnpaused_ok() {
        // Arrange
        Assertions.assertEquals(-4, UnionResponseCode.ALREADY_UNPAUSED.getCode());
    }

    @Test
    void getCode_whenEnvironmentDisabled_ok() {
        // Arrange
        Assertions.assertEquals(-5, UnionResponseCode.ENVIRONMENT_DISABLED.getCode());
    }
}
