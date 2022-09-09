package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.util.ByteUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

public class PegoutCreationEntryTest {

    @Test
    public void testGetPegoutCreationEntryBtcTxHash() {
        Sha256Hash btcTxHash = PegTestUtils.createHash(3);
        Keccak256 rskTxHash = PegTestUtils.createHash3(5);

        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(btcTxHash, rskTxHash);

        assertEquals(btcTxHash, pegoutCreationEntry.getBtcTxHash());
        assertNotNull(pegoutCreationEntry.getBtcTxHash());
    }

    @Test
    public void testGetPegoutCreationEntryRskTxHash() {
        Sha256Hash btcTxHash = PegTestUtils.createHash(5);
        Keccak256 rskTxHash = PegTestUtils.createHash3(3);

        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(btcTxHash, rskTxHash);

        assertNotNull(pegoutCreationEntry.getRskTxHash());
        assertEquals(rskTxHash, pegoutCreationEntry.getRskTxHash());
    }

    @Test
    public void testTestEquals() {
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
    public void testTestHashCode() {
        Sha256Hash btcTxHash = PegTestUtils.createHash(13); // hashcode = 0
        Keccak256 rskTxHash = new Keccak256(
            Keccak256Helper.keccak256(ByteUtil.toHexString("rsk".getBytes()))
        ); // hashcode = -1150029211

        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(btcTxHash, rskTxHash);

        int hashCodeExpected = -1150029211;
        assertEquals(hashCodeExpected, pegoutCreationEntry.hashCode());
    }

    @Test
    public void testTestToString() {
        Sha256Hash btcTxHash = PegTestUtils.createHash(3);
        Keccak256 rskTxHash = PegTestUtils.createHash3(10);

        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(btcTxHash, rskTxHash);

        String expectedResult = "PegoutCreationEntry{btcTxHash=0300000000000000000000000000000000000000000000000000000000000000, rskTxHash=0a00000000000000000000000000000000000000000000000000000000000000}";
        assertEquals(expectedResult, pegoutCreationEntry.toString());
    }
}
