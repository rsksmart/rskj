package co.rsk.peg.bitcoin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class VarIntUtilsTest {

    @Test
    void encode_withNull_shouldReturnEmptyArray() {
        // act
        byte[] encodedValues = VarIntUtils.encode(null);

        // assert
        byte[] expectedEncodedValues = new byte[]{};
        assertArrayEquals(expectedEncodedValues, encodedValues);
    }
}
