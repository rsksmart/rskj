package co.rsk.peg.fastbridge;

import co.rsk.bitcoinj.core.Sha256Hash;

public class FastBridgeFederationInformation {
    private final Sha256Hash derivationHash;
    private final byte[] federationScriptHash;

    public FastBridgeFederationInformation(Sha256Hash derivationHash, byte[] federationScriptHash) {
        this.derivationHash = derivationHash;
        this.federationScriptHash = federationScriptHash.clone();
    }

    public Sha256Hash getDerivationHash() {
        return derivationHash;
    }

    public byte[] getFederationScriptHash() {
        return federationScriptHash.clone();
    }
}
