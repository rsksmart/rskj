package co.rsk.peg.whitelist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WhitelistResponseCodeTest {

    private static Stream<Arguments> responseCodeProvider() {
        return Stream.of(
            Arguments.of(WhitelistResponseCode.GENERIC_ERROR, -10),
            Arguments.of(WhitelistResponseCode.UNAUTHORIZED_CALLER, -10),
            Arguments.of(WhitelistResponseCode.INVALID_ADDRESS_FORMAT, -2),
            Arguments.of(WhitelistResponseCode.DISABLE_BLOCK_DELAY_INVALID, -2),
            Arguments.of(WhitelistResponseCode.ADDRESS_ALREADY_WHITELISTED, -1),
            Arguments.of(WhitelistResponseCode.ADDRESS_NOT_EXIST, -1),
            Arguments.of(WhitelistResponseCode.DELAY_ALREADY_SET, -1),
            Arguments.of(WhitelistResponseCode.UNLIMITED_MODE, 0),
            Arguments.of(WhitelistResponseCode.SUCCESS, 1)
        );
    }

    @ParameterizedTest
    @MethodSource("responseCodeProvider")
    void getCode_shouldReturnExpectedCode(WhitelistResponseCode responseCode, int expectedCode) {
        assertEquals(expectedCode, responseCode.getCode());
    }

    @Test
    void values_shouldHaveNineResponseCodes() {
        assertEquals(9, WhitelistResponseCode.values().length);
    }
}
