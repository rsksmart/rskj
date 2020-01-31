package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Sha256Hash;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class CoinbaseInformationTest {

    @Test
    public void gets_witnessCommitment() {
        Sha256Hash witnessCommitment = Sha256Hash.ZERO_HASH;
        byte[] witnessReservedValue = new byte[] { 0 };
        CoinbaseInformation instance = new CoinbaseInformation(witnessCommitment, witnessReservedValue);

        assertEquals(witnessCommitment, instance.getWitnessCommitment());
    }

    @Test
    public void gets_witnessReservedValue() {
        byte[] witnessReservedValue = new byte[] { 0 };
        CoinbaseInformation instance = new CoinbaseInformation(mock(Sha256Hash.class), witnessReservedValue);

        assertEquals(witnessReservedValue, instance.getReservedValue());
    }
}
