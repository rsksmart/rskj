package co.rsk.peg;

import co.rsk.bitcoinj.core.Context;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import java.util.List;
import java.util.Optional;

public class FlyoverCompatibleBtcWalletWithStorage extends FlyoverCompatibleBtcWallet {
    private final BridgeStorageProvider bridgeStorageProvider;

    public FlyoverCompatibleBtcWalletWithStorage(
        Context btcContext,
        List<Federation> federations,
        BridgeStorageProvider bridgeStorageProvider
    ) {
        super(btcContext, federations);
        this.bridgeStorageProvider = bridgeStorageProvider;
    }

    @Override
    protected Optional<FlyoverFederationInformation> getFlyoverFederationInformation(
        byte[] payToScriptHash) {
        return this.bridgeStorageProvider.getFlyoverFederationInformation(payToScriptHash);
    }
}
