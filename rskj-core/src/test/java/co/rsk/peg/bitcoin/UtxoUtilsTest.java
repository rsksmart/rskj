package co.rsk.peg.bitcoin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.rsk.bitcoinj.core.Coin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.spongycastle.util.encoders.Hex;

class UtxoUtilsTest {

    private final static Coin MAX_BTC = Coin.valueOf(21_000_000, 0);

    private static Stream<Arguments> validOutpointValues() {
        List<Arguments> arguments = new ArrayList<>();

        final byte[] encodedZeroValue = Hex.decode("00");
        List<Coin> decodedZeroOutpointValue = Collections.singletonList(Coin.ZERO);
        arguments.add(Arguments.of(encodedZeroValue, decodedZeroOutpointValue));

        final byte[] encodedOneSatoshiValue = Hex.decode("01");
        List<Coin> decodedOneSatoshiOutpointValue = Collections.singletonList(Coin.SATOSHI);
        ;
        arguments.add(Arguments.of(encodedOneSatoshiValue, decodedOneSatoshiOutpointValue));

        final byte[] encodedManySatoshiValues = Hex.decode("01010101010101010101");
        List<Coin> decodedManySatoshiOutpointValues = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            decodedManySatoshiOutpointValues.add(Coin.SATOSHI);
        }
        arguments.add(Arguments.of(encodedManySatoshiValues, decodedManySatoshiOutpointValues));

        final byte[] encoded252Value = Hex.decode("FC");
        List<Coin> decoded252OutpointValue = Collections.singletonList(Coin.valueOf(252));
        ;
        arguments.add(Arguments.of(encoded252Value, decoded252OutpointValue));

        final byte[] encodedMultipleValues = Hex.decode("FCFCBBBBBBFD1934FE9145DC00");
        List<Coin> decodedMultipleOutpointValues = new ArrayList<>();
        // 252 = FC in VarInt format
        decodedMultipleOutpointValues.add(Coin.valueOf(252));
        decodedMultipleOutpointValues.add(Coin.valueOf(252));
        // 187 = BB in VarInt format
        decodedMultipleOutpointValues.add(Coin.valueOf(187));
        decodedMultipleOutpointValues.add(Coin.valueOf(187));
        decodedMultipleOutpointValues.add(Coin.valueOf(187));
        // 13_337 = FE9145DC00 in VarInt format
        decodedMultipleOutpointValues.add(Coin.valueOf(13_337));
        // 14_435_729 = FEDC4591 in VarInt format
        decodedMultipleOutpointValues.add(Coin.valueOf(14_435_729));
        arguments.add(Arguments.of(encodedMultipleValues, decodedMultipleOutpointValues));

        final byte[] encodedMaxBtcValue = Hex.decode("FF0040075AF0750700");
        List<Coin> decodedMaxBtcOutpointValue = Collections.singletonList(MAX_BTC);
        arguments.add(Arguments.of(encodedMaxBtcValue, decodedMaxBtcOutpointValue));

        final byte[] encodedManyMaxBtcValue = Hex.decode(
            "FF0040075AF0750700FF0040075AF0750700FF0040075AF0750700");
        List<Coin> decodedManyMaxBtcOutpointValues = new ArrayList<>();
        decodedManyMaxBtcOutpointValues.add(MAX_BTC);
        decodedManyMaxBtcOutpointValues.add(MAX_BTC);
        decodedManyMaxBtcOutpointValues.add(MAX_BTC);
        arguments.add(Arguments.of(encodedManyMaxBtcValue, decodedManyMaxBtcOutpointValues));

        final byte[] encodedSurpassMaxBtcOutpointValue = Hex.decode("FF0140075AF0750700");
        List<Coin> decodedSurpassMaxBtcOutpointValue = Collections.singletonList(
            MAX_BTC.add(Coin.SATOSHI));
        arguments.add(
            Arguments.of(encodedSurpassMaxBtcOutpointValue, decodedSurpassMaxBtcOutpointValue));

        final byte[] encodedMaxLongOutpointValue = Hex.decode("FFFFFFFFFFFFFFFF7F");
        List<Coin> decodedMaxLongOutpointValue = Collections.singletonList(
            Coin.valueOf(Long.MAX_VALUE));
        arguments.add(Arguments.of(encodedMaxLongOutpointValue, decodedMaxLongOutpointValue));

        String bigOutpointValuesAsHex = Stream.iterate("FCFCBBBBBBFD1934FE9145DC00", s -> s)
            .limit(1000).collect(Collectors.joining());
        final byte[] bigOutpointValueArray = Hex.decode(bigOutpointValuesAsHex);
        List<Coin> decodedBigOutpointValueList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // 252 = FC in VarInt format
            decodedBigOutpointValueList.add(Coin.valueOf(252));
            decodedBigOutpointValueList.add(Coin.valueOf(252));
            // 187 = BB in VarInt format
            decodedBigOutpointValueList.add(Coin.valueOf(187));
            decodedBigOutpointValueList.add(Coin.valueOf(187));
            decodedBigOutpointValueList.add(Coin.valueOf(187));
            // 13_337 = FE9145DC00 in VarInt format
            decodedBigOutpointValueList.add(Coin.valueOf(13_337));
            // 14_435_729 = FEDC4591 in VarInt format
            decodedBigOutpointValueList.add(Coin.valueOf(14_435_729));
        }
        arguments.add(Arguments.of(bigOutpointValueArray, decodedBigOutpointValueList));

        final byte[] emptyArray = new byte[]{};
        List<Coin> emptyList = Collections.EMPTY_LIST;
        arguments.add(Arguments.of(emptyArray, emptyList));

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
