package co.rsk.peg.fastbridge;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;

public class FastBridgeFederationInformation {
    private final Sha256Hash derivationHash;
    private final byte[] federationScriptHash;
    private final byte[] fastBridgeScriptHash;

    public FastBridgeFederationInformation(
        Sha256Hash derivationHash,
        byte[] federationScriptHash,
        byte[] fastBridgeScriptHash
    ) {
        this.derivationHash = derivationHash;
        this.federationScriptHash = federationScriptHash.clone();
        this.fastBridgeScriptHash = fastBridgeScriptHash;
    }

    public Sha256Hash getDerivationHash() {
        return derivationHash;
    }

    public byte[] getFederationScriptHash() {
        return federationScriptHash.clone();
    }

    public byte[] getFastBridgeScriptHash() {
        return this.fastBridgeScriptHash;
    }

    public Address getFastBridgeFederationAddress(NetworkParameters networkParameters) {
        return Address.fromP2SHHash(networkParameters, this.fastBridgeScriptHash);
    }
}
