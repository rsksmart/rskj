package co.rsk.bridge.bitcoin;

import co.rsk.bitcoinj.core.Sha256Hash;

public class CoinbaseInformation {

    private final Sha256Hash witnessMerkleRoot;

    public CoinbaseInformation(Sha256Hash witnessMerkleRoot) {
        this.witnessMerkleRoot = witnessMerkleRoot;
    }

    public Sha256Hash getWitnessMerkleRoot() {
        return witnessMerkleRoot;
    }

}
