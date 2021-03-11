package co.rsk.peg;

import co.rsk.bitcoinj.core.Context;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.util.List;
import java.util.Optional;

public class FastBridgeCompatibleBtcWalletWithStorage extends FastBridgeCompatibleBtcWallet {
    private final BridgeStorageProvider bridgeStorageProvider;

    public FastBridgeCompatibleBtcWalletWithStorage(
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
