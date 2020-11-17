package co.rsk.peg;

import co.rsk.bitcoinj.core.Context;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.util.List;
import java.util.Optional;

public class FastBridgeCompatibleBtcWalletWithSingleScript extends FastBridgeCompatibleBtcWallet {
    private final FastBridgeFederationInformation fastBridgeFederationInformation;

    public FastBridgeCompatibleBtcWalletWithSingleScript(
        Context btcContext,
        List<Federation> federations,
        FastBridgeFederationInformation fastBridgeFederationInformation
    ) {
        super(btcContext, federations);
        this.fastBridgeFederationInformation = fastBridgeFederationInformation;
    }

    @Override
    protected Optional<FastBridgeFederationInformation> getFastBridgeFederationInformation(
        byte[] payToScriptHash) {
        return Optional.of(fastBridgeFederationInformation);
    }
}
