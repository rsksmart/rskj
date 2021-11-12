package co.rsk.peg;

import co.rsk.bitcoinj.deprecated.core.Context;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.util.List;
import java.util.Optional;

public class FastBridgeCompatibleBtcWalletWithSingleScriptDeprecated extends FastBridgeCompatibleBtcWalletDeprecated {
    private final FastBridgeFederationInformation fastBridgeFederationInformation;

    public FastBridgeCompatibleBtcWalletWithSingleScriptDeprecated(
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
