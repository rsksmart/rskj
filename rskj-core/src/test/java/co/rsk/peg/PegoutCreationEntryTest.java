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
        Sha256Hash sha256Hash = PegTestUtils.createHash(3);
        Keccak256 keccak256 = PegTestUtils.createHash3(5);
        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(sha256Hash, keccak256);
        assertEquals(sha256Hash, pegoutCreationEntry.getBtcTxHash());
        assertNotNull(pegoutCreationEntry.getBtcTxHash());
    }

    @Test
    public void testGetPegoutCreationEntryRskTxHash() {
        Sha256Hash sha256Hash = PegTestUtils.createHash(5);
        Keccak256 keccak256 = PegTestUtils.createHash3(3);
        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(sha256Hash, keccak256);
        assertNotNull(pegoutCreationEntry.getRskTxHash());
        assertEquals(keccak256, pegoutCreationEntry.getRskTxHash());
    }

    @Test
    public void testTestEquals() {
        Sha256Hash sha256Hash = PegTestUtils.createHash(8);
        Keccak256 keccak256 = PegTestUtils.createHash3(13);
        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(sha256Hash, keccak256);
        PegoutCreationEntry pegoutCreationEntryWithSameValues = new PegoutCreationEntry(sha256Hash, keccak256);

        assertEquals(pegoutCreationEntry, pegoutCreationEntryWithSameValues);

        PegoutCreationEntry pegoutCreationEntryWithDifferentValues = new PegoutCreationEntry(
            PegTestUtils.createHash(2),
            PegTestUtils.createHash3(2)
        );

        assertNotSame(pegoutCreationEntry, pegoutCreationEntryWithDifferentValues);
    }

    @Test
    public void testTestHashCode() {
        Sha256Hash sha256Hash = PegTestUtils.createHash(13); // hashcode = 0
        Keccak256 keccak256 = new Keccak256(
            Keccak256Helper.keccak256(ByteUtil.toHexString("rsk".getBytes()))
        ); // hashcode = -1150029211

        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(sha256Hash, keccak256);

        int hashCodeExpected = -1150029211;
        assertEquals(hashCodeExpected, pegoutCreationEntry.hashCode());
    }

    @Test
    public void testTestToString() {
        Sha256Hash sha256Hash = PegTestUtils.createHash(3);
        Keccak256 keccak256 = PegTestUtils.createHash3(10);
        PegoutCreationEntry pegoutCreationEntry = new PegoutCreationEntry(sha256Hash, keccak256);

        String expectedResult = "PegoutCreationEntry{btcTxHash=0300000000000000000000000000000000000000000000000000000000000000, rskTxHash=0a00000000000000000000000000000000000000000000000000000000000000}";
        assertEquals(expectedResult, pegoutCreationEntry.toString());
    }
}
