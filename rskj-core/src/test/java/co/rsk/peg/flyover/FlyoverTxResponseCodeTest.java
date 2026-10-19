package co.rsk.peg.flyover;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FlyoverTxResponseCodeTest {

    private static Stream<Arguments> responseCodeProvider() {
        return Stream.of(
            Arguments.of(FlyoverTxResponseCode.REFUNDED_USER_ERROR, -100),
            Arguments.of(FlyoverTxResponseCode.REFUNDED_LP_ERROR, -200),
            Arguments.of(FlyoverTxResponseCode.UNPROCESSABLE_TX_NOT_CONTRACT_ERROR, -300),
            Arguments.of(FlyoverTxResponseCode.UNPROCESSABLE_TX_INVALID_SENDER_ERROR, -301),
            Arguments.of(FlyoverTxResponseCode.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR, -302),
            Arguments.of(FlyoverTxResponseCode.UNPROCESSABLE_TX_VALIDATIONS_ERROR, -303),
            Arguments.of(FlyoverTxResponseCode.UNPROCESSABLE_TX_VALUE_ZERO_ERROR, -304),
            Arguments.of(FlyoverTxResponseCode.UNPROCESSABLE_TX_UTXO_AMOUNT_SENT_BELOW_MINIMUM_ERROR, -305),
            Arguments.of(FlyoverTxResponseCode.GENERIC_ERROR, -900),
            Arguments.of(FlyoverTxResponseCode.VALID_TX, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("responseCodeProvider")
    void getCode_shouldReturnExpectedCode(FlyoverTxResponseCode responseCode, int expectedCode) {
        assertEquals(expectedCode, responseCode.getCode());
    }

    @Test
    void values_shouldHaveTenResponseCodes() {
        assertEquals(10, FlyoverTxResponseCode.values().length);
    }
}
