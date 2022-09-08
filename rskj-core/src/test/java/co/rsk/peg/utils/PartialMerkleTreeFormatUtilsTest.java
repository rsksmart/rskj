package co.rsk.peg.utils;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.bouncycastle.util.encoders.Hex;

import static org.hamcrest.Matchers.is;

public class PartialMerkleTreeFormatUtilsTest {

    @Test
    public void getHashesCount() {
        String pmtSerializedEncoded = "030000000279e7c0da739df8a00f12c0bff55e5438f530aa5859ff9874258cd7bad3fe709746aff89" +
                "7e6a851faa80120d6ae99db30883699ac0428fc7192d6c3fec0ca6409010d";
        byte[] pmtSerialized = Hex.decode(pmtSerializedEncoded);
        MatcherAssert.assertThat(PartialMerkleTreeFormatUtils.getHashesCount(pmtSerialized).value, is(2L));
    }

    @Test
    public void getFlagBitsCount() {
        String pmtSerializedEncoded = "030000000279e7c0da739df8a00f12c0bff55e5438f530aa5859ff9874258cd7bad3fe709746aff89" +
                "7e6a851faa80120d6ae99db30883699ac0428fc7192d6c3fec0ca6409010d";
        byte[] pmtSerialized = Hex.decode(pmtSerializedEncoded);
        MatcherAssert.assertThat(PartialMerkleTreeFormatUtils.getFlagBitsCount(pmtSerialized).value, is(1L));
    }

    @Test
    public void hasExpectedSize() {
        String pmtSerializedEncoded = "030000000279e7c0da739df8a00f12c0bff55e5438f530aa5859ff9874258cd7bad3fe709746aff89" +
                "7e6a851faa80120d6ae99db30883699ac0428fc7192d6c3fec0ca6409010d";
        byte[] pmtSerialized = Hex.decode(pmtSerializedEncoded);
        MatcherAssert.assertThat(PartialMerkleTreeFormatUtils.hasExpectedSize(pmtSerialized), is(true));
    }

    @Test
    public void doesntHaveExpectedSize() {
        String pmtSerializedEncoded = "030000000279e7c0da739df8a00f12c0bff55e5438f530aa5859ff9874258cd7bad3fe709746aff89" +
                "7e6a851faa80120d6ae99db30883699ac0428fc7192d6c3fec0ca64010d";
        byte[] pmtSerialized = Hex.decode(pmtSerializedEncoded);
        MatcherAssert.assertThat(PartialMerkleTreeFormatUtils.hasExpectedSize(pmtSerialized), is(false));
    }

    @Test
    public void overflowSize() {
        String pmtSerializedEncoded = "0300ffffff79e7c0da739df8a00f12c0bff55e5438f530aa5859ff9874258cd7bad3fe709746aff89" +
                "7e6a851faa80120d6ae99db30883699ac0428fc7192d6c3fec0ca6409010d";
        byte[] pmtSerialized = Hex.decode(pmtSerializedEncoded);
        Assertions.assertThrows(ArithmeticException.class, () -> PartialMerkleTreeFormatUtils.getFlagBitsCount(pmtSerialized));
    }
}
