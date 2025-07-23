package co.rsk.peg.union;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UnionResponseCodeTest {

    @Test
    void getCode_whenSuccess_shouldReturnCode() {
        // Arrange
        Assertions.assertEquals(0, UnionResponseCode.SUCCESS.getCode());
    }

    @Test
    void getCode_whenGenericError_shouldReturnCode() {
        // Arrange
        Assertions.assertEquals(-10, UnionResponseCode.GENERIC_ERROR.getCode());
    }

    @Test
    void getCode_whenUnauthorizedCaller_shouldReturnCode() {
        // Arrange
        Assertions.assertEquals(-1, UnionResponseCode.UNAUTHORIZED_CALLER.getCode());
    }

    @Test
    void getCode_whenInvalidValue_shouldReturnCode() {
        // Arrange
        Assertions.assertEquals(-2, UnionResponseCode.INVALID_VALUE.getCode());
    }

    @Test
    void getCode_whenRequestDisabled_shouldReturnCode() {
        // Arrange
        Assertions.assertEquals(-3, UnionResponseCode.REQUEST_DISABLED.getCode());
    }

    @Test
    void getCode_whenReleaseDisabled_shouldReturnCode() {
        // Arrange
        Assertions.assertEquals(-3, UnionResponseCode.RELEASE_DISABLED.getCode());
    }

    @Test
    void getCode_whenEnvironmentDisabled_shouldReturnCode() {
        // Arrange
        Assertions.assertEquals(-3, UnionResponseCode.ENVIRONMENT_DISABLED.getCode());
    }
}
