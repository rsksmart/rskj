package co.rsk.peg.fastbridge;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import org.spongycastle.util.encoders.Hex;

public class FastBridgeFederationInformation {
    private final Keccak256 derivationHash;
    private final byte[] federationScriptHash;
    private final byte[] fastBridgeScriptHash;

    public FastBridgeFederationInformation(
        Keccak256 derivationHash,
        byte[] federationScriptHash,
        byte[] fastBridgeScriptHash
    ) {
        this.derivationHash = derivationHash;
        this.federationScriptHash = federationScriptHash.clone();
        this.fastBridgeScriptHash = fastBridgeScriptHash;
    }

    public Keccak256 getDerivationHash() {
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

    @Override
    public String toString() {
        return String.format(
            "derivationHash: %s, fastBridgeScriptHash: %s, federationScriptHash: %s",
            derivationHash,
            Hex.toHexString(fastBridgeScriptHash),
            Hex.toHexString(federationScriptHash)
        );
    }
}
