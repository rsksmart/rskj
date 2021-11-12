package co.rsk.peg;

import co.rsk.bitcoinj.deprecated.core.Context;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.util.List;
import java.util.Optional;

public class FastBridgeCompatibleBtcWalletWithStorageDeprecated extends FastBridgeCompatibleBtcWalletDeprecated {
    private final BridgeStorageProvider bridgeStorageProvider;

    public FastBridgeCompatibleBtcWalletWithStorageDeprecated(
        Context btcContext,
        List<Federation> federations,
        BridgeStorageProvider bridgeStorageProvider
    ) {
        super(btcContext, federations);
        this.bridgeStorageProvider = bridgeStorageProvider;
    }

    @Override
    protected Optional<FastBridgeFederationInformation> getFastBridgeFederationInformation(
        byte[] payToScriptHash) {
        return this.bridgeStorageProvider.getFastBridgeFederationInformation(payToScriptHash);
    }
}
