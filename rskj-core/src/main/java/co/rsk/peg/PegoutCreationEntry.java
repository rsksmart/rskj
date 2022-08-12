package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;

public class PegoutCreationEntry {
    private Sha256Hash btcTxHash;
    private Keccak256 rskTxHash;

    public PegoutCreationEntry(Sha256Hash btcTxHash, Keccak256 rskTxHash) {
        this.btcTxHash = btcTxHash;
        this.rskTxHash = rskTxHash;
    }

    public Sha256Hash getBtcTxHash() {
        return btcTxHash;
    }

    public Keccak256 getRskTxHash() {
        return rskTxHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PegoutCreationEntry)) {
            return false;
        }

        PegoutCreationEntry that = (PegoutCreationEntry) o;
        return getBtcTxHash().equals(that.getBtcTxHash()) && getRskTxHash().equals(that.getRskTxHash());
    }

    @Override
    public int hashCode() {
        return getBtcTxHash().hashCode() + getRskTxHash().hashCode();
    }

    @Override
    public String toString() {
        return "PegoutCreationEntry{" +
                   "btcTxHash=" + btcTxHash +
                   ", rskTxHash=" + rskTxHash +
                   '}';
    }
}
