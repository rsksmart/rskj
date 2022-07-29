package co.rsk.peg.flyover;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import org.spongycastle.util.encoders.Hex;

public class FlyoverFederationInformation {
    private final Keccak256 derivationHash;

    // The hash of the p2sh redeem script corresponding to the active Federation
    // (but without the flyover derivation hash).
    // This field must also correspond to the flyoverScript P2SH redeem script hash
    // stored in flyoverFederationRedeemScriptHash
    private final byte[] federationRedeemScriptHash;

    // The hash of the p2sh redeem script corresponding to the Flyover federation
    private final byte[] flyoverFederationRedeemScriptHash;

    public FlyoverFederationInformation(
        Keccak256 derivationHash,
        byte[] federationRedeemScriptHash,
        byte[] flyoverFederationRedeemScriptHash
    ) {
        this.derivationHash = derivationHash;
        this.federationRedeemScriptHash = federationRedeemScriptHash.clone();
        this.flyoverFederationRedeemScriptHash = flyoverFederationRedeemScriptHash.clone();
    }

    public Keccak256 getDerivationHash() {
        return derivationHash;
    }

    public byte[] getFederationRedeemScriptHash() {
        return federationRedeemScriptHash.clone();
    }

    public byte[] getFlyoverFederationRedeemScriptHash() {
        return this.flyoverFederationRedeemScriptHash.clone();
    }

    public Address getFlyoverFederationAddress(NetworkParameters networkParameters) {
        return Address.fromP2SHHash(networkParameters, this.flyoverFederationRedeemScriptHash);
    }

    @Override
    public String toString() {
        return String.format(
            "derivationHash: %s, flyoverFederationRedeemScriptHash: %s, federationRedeemScriptHash: %s",
            derivationHash,
            Hex.toHexString(flyoverFederationRedeemScriptHash),
            Hex.toHexString(federationRedeemScriptHash)
        );
    }
}
