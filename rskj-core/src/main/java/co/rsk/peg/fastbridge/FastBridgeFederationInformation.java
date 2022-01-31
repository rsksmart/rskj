package co.rsk.peg.fastbridge;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import org.spongycastle.util.encoders.Hex;

public class FastBridgeFederationInformation {
    private final Keccak256 derivationHash;

    // The hash of the p2sh redeem script corresponding to the active Federation
    // (but without the flyover derivation hash).
    // This field must also correspond to the fastBridgeScript P2SH redeem script hash
    // stored in fastBridgeFederationRedeemScriptHash
    private final byte[] federationRedeemScriptHash;

    // The hash of the p2sh redeem script corresponding to the Flyover federation
    private final byte[] fastBridgeFederationRedeemScriptHash;

    public FastBridgeFederationInformation(
        Keccak256 derivationHash,
        byte[] federationRedeemScriptHash,
        byte[] fastBridgeFederationRedeemScriptHash
    ) {
        this.derivationHash = derivationHash;
        this.federationRedeemScriptHash = federationRedeemScriptHash.clone();
        this.fastBridgeFederationRedeemScriptHash = fastBridgeFederationRedeemScriptHash.clone();
    }

    public Keccak256 getDerivationHash() {
        return derivationHash;
    }

    public byte[] getFederationRedeemScriptHash() {
        return federationRedeemScriptHash.clone();
    }

    public byte[] getFastBridgeFederationRedeemScriptHash() {
        return this.fastBridgeFederationRedeemScriptHash.clone();
    }

    public Address getFastBridgeFederationAddress(NetworkParameters networkParameters) {
        return Address.fromP2SHHash(networkParameters, this.fastBridgeFederationRedeemScriptHash);
    }

    @Override
    public String toString() {
        return String.format(
            "derivationHash: %s, fastBridgeFederationRedeemScriptHash: %s, federationRedeemScriptHash: %s",
            derivationHash,
            Hex.toHexString(fastBridgeFederationRedeemScriptHash),
            Hex.toHexString(federationRedeemScriptHash)
        );
    }
}
