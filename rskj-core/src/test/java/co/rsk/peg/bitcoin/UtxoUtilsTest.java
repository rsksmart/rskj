package co.rsk.peg.bitcoin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.config.BridgeMainNetConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.spongycastle.util.encoders.Hex;

class UtxoUtilsTest {

    private final static Coin MAX_BTC = BridgeMainNetConstants.getInstance().getMaxRbtc();

    private static List<Coin> coinListOf(long ... valuesInSatoshis) {
        return Arrays.stream(valuesInSatoshis)
            .mapToObj(Coin::valueOf)
            .collect(Collectors.toList());
    }

    private static Stream<Arguments> validOutpointValues() {
        List<Arguments> arguments = new ArrayList<>();

        arguments.add(Arguments.of(Hex.decode("00"), Collections.singletonList(Coin.ZERO)));

        arguments.add(Arguments.of(Hex.decode("01"), Collections.singletonList(Coin.SATOSHI)));

        arguments.add(Arguments.of(Hex.decode("01010101010101010101"), Stream.generate(() -> Coin.SATOSHI).limit(10).collect(Collectors.toList())));

        arguments.add(Arguments.of(Hex.decode("FC"), coinListOf(252)));

        arguments.add(Arguments.of(Hex.decode("FCFCBBBBBBFD1934FE9145DC00"),
            coinListOf(
                // 252 = FC in VarInt format
                252,
                252,
                // 187 = BB in VarInt format
                187,
                187,
                187,
                // 13_337 = FE9145DC00 in VarInt format
                13_337,
                // 14_435_729 = FEDC4591 in VarInt format
                14_435_729
            ))
        );

        arguments.add(Arguments.of(Hex.decode("FF0040075AF0750700"), Collections.singletonList(MAX_BTC)));

        arguments.add(Arguments.of(Hex.decode("FF0040075AF0750700FF0040075AF0750700FF0040075AF0750700"), Arrays.asList(MAX_BTC, MAX_BTC, MAX_BTC)));

        arguments.add(Arguments.of(Hex.decode("FF0140075AF0750700"), Collections.singletonList(MAX_BTC.add(Coin.SATOSHI))));

        arguments.add(Arguments.of(Hex.decode("FFFFFFFFFFFFFFFF7F"), coinListOf(Long.MAX_VALUE)));

        final byte[] bigOutpointValueArray = Hex.decode(Stream.iterate("FCFCBBBBBBFD1934FE9145DC00",
                UnaryOperator.identity())
            .limit(1000).collect(Collectors.joining()));
        List<Coin> bigListOfOutpointValues = Stream.generate(() -> coinListOf(
            // 252 = FC in VarInt format
            252,
            252,
            // 187 = BB in VarInt format
            187,
            187,
            187,
            // 13_337 = FE9145DC00 in VarInt format
            13_337,
            // 14_435_729 = FEDC4591 in VarInt format
            14_435_729)
        ).limit(1000).flatMap(Collection::stream).collect(Collectors.toList());
        arguments.add(Arguments.of(
            bigOutpointValueArray,
            bigListOfOutpointValues
        ));

        arguments.add(Arguments.of(new byte[]{}, Collections.EMPTY_LIST));

        return arguments.stream();
    }

    private static Stream<Arguments> invalidOutpointValues() {
        List<Arguments> arguments = new ArrayList<>();

        List<Coin> negativeOutpointValues = Arrays.asList(Coin.valueOf(-10), Coin.valueOf(-1000),
            Coin.valueOf(-100));
        String expectedMessageForNegativeOutpointValues = String.format(
            "Invalid outpoint value: %s. Negative and null values are not allowed.", -10);
        arguments.add(
            Arguments.of(negativeOutpointValues, expectedMessageForNegativeOutpointValues));

        List<Coin> negativeAndPositiveOutpointValues = Arrays.asList(Coin.valueOf(200),
            Coin.valueOf(-100), Coin.valueOf(300));
        String expectedMessageForNegativeAndPositiveOutpointValues = String.format(
            "Invalid outpoint value: %s. Negative and null values are not allowed.", -100);
        arguments.add(Arguments.of(negativeAndPositiveOutpointValues,
            expectedMessageForNegativeAndPositiveOutpointValues));

        return arguments.stream();
    }

    private static Stream<Arguments> invalidEncodedOutpointValues() {
        List<Arguments> arguments = new ArrayList<>();

        // -100, -200, -300
        final byte[] negativeOutpointValues = Hex.decode(
            "FF9CFFFFFFFFFFFFFFFF38FFFFFFFFFFFFFFFFD4FEFFFFFFFFFFFF");
        String expectedMessageForNegativeOutpointValues = String.format(
            "Invalid outpoint value: %s. Negative and null values are not allowed.", -100);
        arguments.add(
            Arguments.of(negativeOutpointValues, expectedMessageForNegativeOutpointValues));

        // 100, 200, 300, -400
        final byte[] negativeAndPositiveOutpointValues = Hex.decode("64C8FD2C01FF70FEFFFFFFFFFFFF");
        String expectedMessageForNegativeAndPositiveOutpointValues = String.format(
            "Invalid outpoint value: %s. Negative and null values are not allowed.", -400);
        arguments.add(Arguments.of(negativeAndPositiveOutpointValues,
            expectedMessageForNegativeAndPositiveOutpointValues));

        final byte[] invalidOutpointValues = Hex.decode("FC9145DC00FAFF00FE");
        String expectedMessageForInvalidOutpointValues = String.format(
            "Invalid value with invalid VarInt format: %s", "FC9145DC00FAFF00FE");
        arguments.add(Arguments.of(invalidOutpointValues, expectedMessageForInvalidOutpointValues));

        final byte[] anotherInvalidOutpointValues = Hex.decode("FB8267DC00FCFF00FE");
        String expectedMessageForAnotherInvalidOutpointValue = String.format(
            "Invalid value with invalid VarInt format: %s", "FB8267DC00FCFF00FE");
        arguments.add(Arguments.of(anotherInvalidOutpointValues,
            expectedMessageForAnotherInvalidOutpointValue));

        return arguments.stream();
    }

    @ParameterizedTest
    @MethodSource("validOutpointValues")
    void decodeOutpointValues(byte[] encodedValues, List<Coin> expectedDecodedValues) {
        // act
        List<Coin> decodeOutpointValues = UtxoUtils.decodeOutpointValues(encodedValues);

        // assert
        assertArrayEquals(expectedDecodedValues.toArray(), decodeOutpointValues.toArray());
    }

    @Test
    void decodeOutpointValues_nullEncodedValues_shouldReturnEmptyList() {
        // act
        List<Coin> decodeOutpointValues = UtxoUtils.decodeOutpointValues(null);

        // assert
        List<Coin> expectedDecodedValues = Collections.EMPTY_LIST;
        assertArrayEquals(expectedDecodedValues.toArray(), decodeOutpointValues.toArray());
    }

    @ParameterizedTest
    @MethodSource("validOutpointValues")
    void encodeOutpointValues(byte[] expectedEncodedOutpointValues, List<Coin> outpointValues) {
        // act
        byte[] encodeOutpointValues = UtxoUtils.encodeOutpointValues(outpointValues);

        // assert
        assertArrayEquals(expectedEncodedOutpointValues, encodeOutpointValues);
    }

    @Test
    void decodeOutpointValues_nullOutpointValues_shouldReturnEmptyArray() {
        // act
        List<Coin> outpointValues = UtxoUtils.decodeOutpointValues(null);

        // assert
        List<Coin> expectedDecodedValues = Collections.EMPTY_LIST;
        assertArrayEquals(expectedDecodedValues.toArray(), outpointValues.toArray());
    }

    @ParameterizedTest
    @MethodSource("invalidOutpointValues")
    void encodeOutpointValues_invalidOutpointValues_shouldThrowInvalidOutpointValueException(
        List<Coin> outpointValues, String expectedMessage) {
        // act
        InvalidOutpointValueException invalidOutpointValueException = assertThrows(
            InvalidOutpointValueException.class,
            () -> UtxoUtils.encodeOutpointValues(outpointValues));
        String actualMessage = invalidOutpointValueException.getMessage();

        // assert
        assertEquals(expectedMessage, actualMessage);
    }

    @ParameterizedTest
    @MethodSource("invalidEncodedOutpointValues")
    void decodeOutpointValues_invalidOutpointValues_shouldThrowInvalidOutpointValueException(
        byte[] encodedOutpointValues, String expectedMessage) {
        // act
        InvalidOutpointValueException invalidOutpointValueException = assertThrows(
            InvalidOutpointValueException.class,
            () -> UtxoUtils.decodeOutpointValues(encodedOutpointValues));
        String actualMessage = invalidOutpointValueException.getMessage();

        // assert
        assertEquals(expectedMessage, actualMessage);
    }
}
