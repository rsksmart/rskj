package co.rsk.peg.feeperkb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FeePerKbResponseCodeTest {

    private static Stream<Arguments> responseCodeProvider() {
        return Stream.of(
            Arguments.of(FeePerKbResponseCode.SUCCESSFUL_VOTE, 1),
            Arguments.of(FeePerKbResponseCode.UNSUCCESSFUL_VOTE, -1),
            Arguments.of(FeePerKbResponseCode.EXCESSIVE_FEE_VOTED, -2),
            Arguments.of(FeePerKbResponseCode.NEGATIVE_FEE_VOTED, -1),
            Arguments.of(FeePerKbResponseCode.UNAUTHORIZED_CALLER, -10),
            Arguments.of(FeePerKbResponseCode.GENERIC_ERROR, -10)
        );
    }

    @ParameterizedTest
    @MethodSource("responseCodeProvider")
    void getCode_shouldReturnExpectedCode(FeePerKbResponseCode responseCode, int expectedCode) {
        assertEquals(expectedCode, responseCode.getCode());
    }

    @Test
    void values_shouldHaveSixResponseCodes() {
        assertEquals(6, FeePerKbResponseCode.values().length);
    }
}
