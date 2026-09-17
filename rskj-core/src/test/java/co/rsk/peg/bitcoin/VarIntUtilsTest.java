package co.rsk.peg.bitcoin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VarIntUtilsTest {

    // 252 = FC, 187 = BB, 13_337 = FD1934, 14_435_729 = FE9145DC00
    private static final List<Long> VALUES_OF_DIFFERENT_SIZES =
        List.of(252L, 252L, 187L, 187L, 187L, 13_337L, 14_435_729L);
    private static final String ENCODED_VALUES_OF_DIFFERENT_SIZES = "FCFCBBBBBBFD1934FE9145DC00";
    private static final int LARGE_LIST_SIZE = 1000;

    @Test
    void encode_withNull_shouldReturnEmptyArray() {
        // act
        byte[] encodedValues = VarIntUtils.encode(null);

        // assert
        byte[] expectedEncodedValues = new byte[]{};
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }

    @Test
    void encode_withEmptyList_shouldReturnEmptyArray() {
        // act
        byte[] encodedValues = VarIntUtils.encode(List.of());

        // assert
        byte[] expectedEncodedValues = new byte[]{};
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }

    @Test
    void encodeDecode_withSingleZeroValue_shouldMatchEncodedValues() {
        // arrange
        List<Long> values = List.of(0L);
        byte[] encodedValues = Hex.decode("00");

        // act & assert
        assertArrayEquals(encodedValues, VarIntUtils.encode(values));
        assertEquals(values, VarIntUtils.decode(encodedValues));
    }

    @Test
    void encodeDecode_withSingleOneValue_shouldMatchEncodedValues() {
        // arrange
        List<Long> values = List.of(1L);
        byte[] encodedValues = Hex.decode("01");

        // act & assert
        assertArrayEquals(encodedValues, VarIntUtils.encode(values));
        assertEquals(values, VarIntUtils.decode(encodedValues));
    }

    @Test
    void encodeDecode_withRepeatedValues_shouldMatchEncodedValues() {
        // arrange
        List<Long> values = Collections.nCopies(10, 1L);
        byte[] encodedValues = Hex.decode("01010101010101010101");

        // act & assert
        assertArrayEquals(encodedValues, VarIntUtils.encode(values));
        assertEquals(values, VarIntUtils.decode(encodedValues));
    }

    @Test
    void encodeDecode_withMaximumOneByteValue_shouldMatchEncodedValues() {
        // arrange
        List<Long> values = List.of(252L);
        byte[] encodedValues = Hex.decode("FC");

        // act & assert
        assertArrayEquals(encodedValues, VarIntUtils.encode(values));
        assertEquals(values, VarIntUtils.decode(encodedValues));
    }

    @Test
    void encodeDecode_withValuesOfDifferentSizes_shouldMatchEncodedValuesPreservingOrder() {
        // arrange
        byte[] encodedValues = Hex.decode(ENCODED_VALUES_OF_DIFFERENT_SIZES);

        // act & assert
        assertArrayEquals(encodedValues, VarIntUtils.encode(VALUES_OF_DIFFERENT_SIZES));
        assertEquals(VALUES_OF_DIFFERENT_SIZES, VarIntUtils.decode(encodedValues));
    }

    @Test
    void encodeDecode_withMaximumLongValue_shouldMatchEncodedValues() {
        // arrange
        List<Long> values = List.of(Long.MAX_VALUE);
        byte[] encodedValues = Hex.decode("FFFFFFFFFFFFFFFF7F");

        // act & assert
        assertArrayEquals(encodedValues, VarIntUtils.encode(values));
        assertEquals(values, VarIntUtils.decode(encodedValues));
    }

    @Test
    void encodeDecode_withLargeListOfValues_shouldMatchEncodedValuesPreservingOrder() {
        // arrange
        List<Long> values = Collections.nCopies(LARGE_LIST_SIZE, VALUES_OF_DIFFERENT_SIZES)
            .stream()
            .flatMap(List::stream)
            .toList();
        byte[] encodedValues = Hex.decode(ENCODED_VALUES_OF_DIFFERENT_SIZES.repeat(LARGE_LIST_SIZE));

        // act & assert
        assertArrayEquals(encodedValues, VarIntUtils.encode(values));
        assertEquals(values, VarIntUtils.decode(encodedValues));
    }

    @Test
    void encode_withNullValue_shouldThrowVarIntException() {
        // arrange
        List<Long> values = Collections.singletonList(null);

        // act & assert
        assertThrows(VarIntException.class, () -> VarIntUtils.encode(values));
    }

    @Test
    void encode_withNegativeValue_shouldThrowVarIntException() {
        // arrange
        List<Long> values = List.of(-1L);

        // act & assert
        assertThrows(VarIntException.class, () -> VarIntUtils.encode(values));
    }

    @Test
    void encode_withNegativeValueAfterValidValues_shouldThrowVarIntException() {
        // arrange
        List<Long> values = List.of(0L, 1L, -1L);

        // act & assert
        assertThrows(VarIntException.class, () -> VarIntUtils.encode(values));
    }

    @Test
    void encode_withNullValueAfterValidValues_shouldThrowVarIntException() {
        // arrange
        List<Long> values = Arrays.asList(0L, 1L, null);

        // act & assert
        assertThrows(VarIntException.class, () -> VarIntUtils.encode(values));
    }

    @Test
    void decode_withNull_shouldReturnEmptyList() {
        // act
        List<Long> values = VarIntUtils.decode(null);

        // assert
        List<Long> expectedValues = List.of();
        assertArrayEquals(expectedValues.toArray(), values.toArray());
    }

    @Test
    void decode_withEmptyArray_shouldReturnEmptyList() {
        // act
        List<Long> values = VarIntUtils.decode(new byte[]{});

        // assert
        List<Long> expectedValues = List.of();
        assertArrayEquals(expectedValues.toArray(), values.toArray());
    }

    @Test
    void decode_withTruncatedVarInt_shouldThrowVarIntException() {
        // arrange
        // FE (254) announces a five byte VarInt, but only two bytes follow it
        byte[] encodedValues = Hex.decode("FE0100");

        // act & assert
        assertThrows(VarIntException.class, () -> VarIntUtils.decode(encodedValues));
    }

    @Test
    void decode_withValueAboveMaximumLong_shouldThrowVarIntException() {
        // arrange
        // 2^63, one above Long.MAX_VALUE. A VarInt encodes an unsigned integer, but it is
        // read back into a signed long, so this value wraps around to Long.MIN_VALUE
        byte[] encodedValues = Hex.decode("FF0000000000000080");

        // act & assert
        assertThrows(VarIntException.class, () -> VarIntUtils.decode(encodedValues));
    }

    @Test
    void decode_withMaximumUnsignedValue_shouldThrowVarIntException() {
        // arrange
        // eight bytes of ones is 2^64 - 1, the largest value a VarInt can encode, which read
        // back into a signed long is -1
        byte[] encodedValues = Hex.decode("FFFFFFFFFFFFFFFFFF");

        // act & assert
        assertThrows(VarIntException.class, () -> VarIntUtils.decode(encodedValues));
    }
}
