package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;

public class FlyoverFederationData {
    private final Script flyoverScriptHash;
    private final Sha256Hash derivationArgumentsHash;

    public FlyoverFederationData(Script flyoverScriptHash, Sha256Hash derivationArgumentsHash) {
        this.flyoverScriptHash = flyoverScriptHash;
        this.derivationArgumentsHash = derivationArgumentsHash;
    }

    public Script getFlyoverScriptHash() {
        return flyoverScriptHash;
    }

    public Sha256Hash getDerivationArgumentsHash() {
        return derivationArgumentsHash;
    }
}
