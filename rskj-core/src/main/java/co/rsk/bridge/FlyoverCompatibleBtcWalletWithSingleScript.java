package co.rsk.bridge;

import co.rsk.bitcoinj.core.Context;
import co.rsk.bridge.flyover.FlyoverFederationInformation;
import java.util.List;
import java.util.Optional;

public class FlyoverCompatibleBtcWalletWithSingleScript extends FlyoverCompatibleBtcWallet {
    private final FlyoverFederationInformation flyoverFederationInformation;

    public FlyoverCompatibleBtcWalletWithSingleScript(
        Context btcContext,
        List<Federation> federations,
        FlyoverFederationInformation flyoverFederationInformation
    ) {
        super(btcContext, federations);
        this.flyoverFederationInformation = flyoverFederationInformation;
    }

    @Override
    protected Optional<FlyoverFederationInformation> getFlyoverFederationInformation(
        byte[] payToScriptHash) {
        return Optional.of(flyoverFederationInformation);
    }
}
