package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;


class PegoutCreationEntryTest {

    @Test
    void testGetPegoutCreationEntryBtcTxHash() {
        Sha256Hash btcTxHash = PegTestUtils.createHash(3);
        Keccak256 rskTxHash = PegTestUtils.createHash3(5);

        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(btcTxHash, rskTxHash);

        assertEquals(btcTxHash, pegoutCreationEntry.getBtcTxHash());
        assertNotNull(pegoutCreationEntry.getBtcTxHash());
    }

    @Test
    void testGetPegoutCreationEntryRskTxHash() {
        Sha256Hash btcTxHash = PegTestUtils.createHash(5);
        Keccak256 rskTxHash = PegTestUtils.createHash3(3);

        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(btcTxHash, rskTxHash);

        assertNotNull(pegoutCreationEntry.getRskTxHash());
        assertEquals(rskTxHash, pegoutCreationEntry.getRskTxHash());
    }

    @Test
    void testTestEquals() {
        Sha256Hash btcTxHash = PegTestUtils.createHash(8);
        Keccak256 rskTxHash = PegTestUtils.createHash3(13);

        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(btcTxHash, rskTxHash);
        PegoutCreationEntry pegoutCreationEntryWithSameValues = new PegoutCreationEntry(btcTxHash, rskTxHash);

        assertEquals(pegoutCreationEntry, pegoutCreationEntryWithSameValues);

        PegoutCreationEntry pegoutCreationEntryWithDifferentValues = new PegoutCreationEntry(
            PegTestUtils.createHash(2),
            PegTestUtils.createHash3(2)
        );

        assertNotSame(pegoutCreationEntry, pegoutCreationEntryWithDifferentValues);
    }

    @Test
    void testTestHashCode() {
        Sha256Hash btcTxHash = PegTestUtils.createHash(13); // hashcode = 0
        Keccak256 rskTxHash = new Keccak256(
            Keccak256Helper.keccak256(ByteUtil.toHexString("rsk".getBytes()))
        ); // hashcode = -1150029211

        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(btcTxHash, rskTxHash);

        int hashCodeExpected = -1150029211;
        assertEquals(hashCodeExpected, pegoutCreationEntry.hashCode());
    }

    @Test
    void testTestToString() {
        Sha256Hash btcTxHash = PegTestUtils.createHash(3);
        Keccak256 rskTxHash = PegTestUtils.createHash3(10);

        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(btcTxHash, rskTxHash);

        String expectedResult = "PegoutCreationEntry{btcTxHash=0300000000000000000000000000000000000000000000000000000000000000, rskTxHash=0a00000000000000000000000000000000000000000000000000000000000000}";
        assertEquals(expectedResult, pegoutCreationEntry.toString());
    }
}
