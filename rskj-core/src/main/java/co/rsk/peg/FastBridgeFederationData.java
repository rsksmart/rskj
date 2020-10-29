package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;

public class FastBridgeFederationData {
    private final Script fastBridgeScriptHash;
    private final Sha256Hash derivationArgumentsHash;

    public FastBridgeFederationData(Script fastBridgeScriptHash, Sha256Hash derivationArgumentsHash) {
        this.fastBridgeScriptHash = fastBridgeScriptHash;
        this.derivationArgumentsHash = derivationArgumentsHash;
    }

    public Script getFastBridgeScriptHash() {
        return fastBridgeScriptHash;
    }

    public Sha256Hash getDerivationArgumentsHash() {
        return derivationArgumentsHash;
    }
}
