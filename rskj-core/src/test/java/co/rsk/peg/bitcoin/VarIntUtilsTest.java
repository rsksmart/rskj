package co.rsk.peg.bitcoin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

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
        byte[] encodedValues = VarIntUtils.encode(Collections.emptyList());

        // assert
        byte[] expectedEncodedValues = new byte[]{};
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }

    @Test
    void encode_withSingleZeroValue_shouldReturnEncodedValue() {
        // arrange
        List<Long> values = Collections.singletonList(0L);

        // act
        byte[] encodedValues = VarIntUtils.encode(values);

        // assert
        byte[] expectedEncodedValues = Hex.decode("00");
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }

    @Test
    void encode_withSingleOneValue_shouldReturnEncodedValue() {
        // arrange
        List<Long> values = Collections.singletonList(1L);

        // act
        byte[] encodedValues = VarIntUtils.encode(values);

        // assert
        byte[] expectedEncodedValues = Hex.decode("01");
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }

    @Test
    void encode_withRepeatedValues_shouldReturnEachValueEncoded() {
        // arrange
        List<Long> values = Collections.nCopies(10, 1L);

        // act
        byte[] encodedValues = VarIntUtils.encode(values);

        // assert
        byte[] expectedEncodedValues = Hex.decode("01010101010101010101");
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }

    @Test
    void encode_withMaximumOneByteValue_shouldReturnOneByte() {
        // arrange
        List<Long> values = List.of(252L);

        // act
        byte[] encodedValues = VarIntUtils.encode(values);

        // assert
        byte[] expectedEncodedValues = Hex.decode("FC");
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }

    @Test
    void encode_withValuesOfDifferentSizes_shouldReturnEncodedValuesPreservingOrder() {
        // act
        byte[] encodedValues = VarIntUtils.encode(VALUES_OF_DIFFERENT_SIZES);

        // assert
        byte[] expectedEncodedValues = Hex.decode(ENCODED_VALUES_OF_DIFFERENT_SIZES);
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }

    @Test
    void encode_withMaximumLongValue_shouldReturnNineBytes() {
        // arrange
        List<Long> values = List.of(Long.MAX_VALUE);

        // act
        byte[] encodedValues = VarIntUtils.encode(values);

        // assert
        byte[] expectedEncodedValues = Hex.decode("FFFFFFFFFFFFFFFF7F");
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }

    @Test
    void encode_withLargeListOfValues_shouldReturnEncodedValuesPreservingOrder() {
        // arrange
        List<Long> values = Collections.nCopies(LARGE_LIST_SIZE, VALUES_OF_DIFFERENT_SIZES)
            .stream()
            .flatMap(List::stream)
            .toList();

        // act
        byte[] encodedValues = VarIntUtils.encode(values);

        // assert
        byte[] expectedEncodedValues =
            Hex.decode(ENCODED_VALUES_OF_DIFFERENT_SIZES.repeat(LARGE_LIST_SIZE));
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }

    @Test
    void encode_withNullValue_shouldThrowVarIntException() {
        // arrange
        List<Long> values = Collections.singletonList(null);

        // act & assert
        assertThrows(VarIntException.class, () -> VarIntUtils.encode(values));
    }
}
