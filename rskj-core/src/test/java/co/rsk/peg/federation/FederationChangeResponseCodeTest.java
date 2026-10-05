package co.rsk.peg.federation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FederationChangeResponseCodeTest {

    private static Stream<Arguments> responseCodeProvider() {
        return Stream.of(
            Arguments.of(FederationChangeResponseCode.SUCCESSFUL, 1),
            Arguments.of(FederationChangeResponseCode.PENDING_FEDERATION_ALREADY_EXISTS, -1),
            Arguments.of(FederationChangeResponseCode.EXISTING_FEDERATION_AWAITING_ACTIVATION, -2),
            Arguments.of(FederationChangeResponseCode.RETIRING_FEDERATION_ALREADY_EXISTS, -3),
            Arguments.of(FederationChangeResponseCode.PROPOSED_FEDERATION_ALREADY_EXISTS, -4),
            Arguments.of(FederationChangeResponseCode.FEDERATION_NON_EXISTENT, -1),
            Arguments.of(FederationChangeResponseCode.FEDERATOR_ALREADY_PRESENT, -2),
            Arguments.of(FederationChangeResponseCode.INSUFFICIENT_MEMBERS, -2),
            Arguments.of(FederationChangeResponseCode.PENDING_FEDERATION_MISMATCHED_HASH, -3),
            Arguments.of(FederationChangeResponseCode.NON_EXISTING_FUNCTION_CALLED, -10),
            Arguments.of(FederationChangeResponseCode.UNAUTHORIZED_CALLER, -10),
            Arguments.of(FederationChangeResponseCode.GENERIC_ERROR, -10)
        );
    }

    @ParameterizedTest
    @MethodSource("responseCodeProvider")
    void getCode_shouldReturnExpectedCode(FederationChangeResponseCode responseCode, int expectedCode) {
        assertEquals(expectedCode, responseCode.getCode());
    }

    @Test
    void values_shouldHaveTwelveResponseCodes() {
        assertEquals(12, FederationChangeResponseCode.values().length);
    }
}
