package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Sha256Hash;

public class CoinbaseInformation {

    private final Sha256Hash witnessCommitment;
    private final byte[] reservedValue;

    public CoinbaseInformation(Sha256Hash witnessCommitment, byte[] reservedValue) {
        this.witnessCommitment = witnessCommitment;
        this.reservedValue = reservedValue;
    }

    public Sha256Hash getWitnessCommitment() {
        return witnessCommitment;
    }

    public byte[] getReservedValue() {
        return reservedValue;
    }
}
