package co.rsk.peg.union;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnionResponseCodeTest {

    private static Stream<Arguments> responseCodeProvider() {
        return Stream.of(
            Arguments.of(UnionResponseCode.SUCCESS, 0),
            Arguments.of(UnionResponseCode.UNAUTHORIZED_CALLER, -1),
            Arguments.of(UnionResponseCode.INVALID_VALUE, -2),
            Arguments.of(UnionResponseCode.REQUEST_DISABLED, -3),
            Arguments.of(UnionResponseCode.RELEASE_DISABLED, -3),
            Arguments.of(UnionResponseCode.GENERIC_ERROR, -10)
        );
    }

    @ParameterizedTest
    @MethodSource("responseCodeProvider")
    void getCode_shouldReturnExpectedCode(UnionResponseCode responseCode, int expectedCode) {
        assertEquals(expectedCode, responseCode.getCode());
    }

    @Test
    void values_shouldHaveSixResponseCodes() {
        assertEquals(6, UnionResponseCode.values().length);
    }
}
