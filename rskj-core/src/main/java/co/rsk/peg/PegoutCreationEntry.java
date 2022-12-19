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

        if (!getBtcTxHash().equals(that.getBtcTxHash())) {
            return false;
        }
        return getRskTxHash() != null ? getRskTxHash().equals(that.getRskTxHash()) : that.getRskTxHash() == null;
    }

    @Override
    public int hashCode() {
        int result = getBtcTxHash().hashCode();
        result = 31 * result + (getRskTxHash() != null ? getRskTxHash().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PegoutCreationEntry{" +
                   "btcTxHash=" + btcTxHash +
                   ", rskTxHash=" + (rskTxHash == null? "null":rskTxHash) +
                   '}';
    }
}
