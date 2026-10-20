package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReceiveHeaderResponseCodeTest {

    private static Stream<Arguments> responseCodeProvider() {
        return Stream.of(
            Arguments.of(ReceiveHeaderResponseCode.SUCCESSFUL, 0),
            Arguments.of(ReceiveHeaderResponseCode.CALLED_TOO_SOON, -1),
            Arguments.of(ReceiveHeaderResponseCode.BLOCK_TOO_OLD, -2),
            Arguments.of(ReceiveHeaderResponseCode.CANNOT_FIND_PREVIOUS_BLOCK, -3),
            Arguments.of(ReceiveHeaderResponseCode.BLOCK_PREVIOUSLY_SAVED, -4),
            Arguments.of(ReceiveHeaderResponseCode.HEADER_SIZE_MISMATCH, -20),
            Arguments.of(ReceiveHeaderResponseCode.UNEXPECTED_EXCEPTION, -99)
        );
    }

    @ParameterizedTest
    @MethodSource("responseCodeProvider")
    void getCode_shouldReturnExpectedCode(ReceiveHeaderResponseCode responseCode, int expectedCode) {
        assertEquals(expectedCode, responseCode.getCode());
    }

    @Test
    void values_shouldHaveSevenResponseCodes() {
        assertEquals(7, ReceiveHeaderResponseCode.values().length);
    }
}
