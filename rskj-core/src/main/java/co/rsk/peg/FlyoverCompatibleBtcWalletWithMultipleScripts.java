package co.rsk.peg;

import co.rsk.bitcoinj.core.Context;
import co.rsk.peg.flyover.FlyoverFederationInformation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FlyoverCompatibleBtcWalletWithMultipleScripts extends FlyoverCompatibleBtcWallet {
    private final Map<Integer, FlyoverFederationInformation> fbFederations;

    public FlyoverCompatibleBtcWalletWithMultipleScripts(
        Context btcContext,
        List<Federation> federations,
        List<FlyoverFederationInformation> fbFederations
    ) {
        super(btcContext, federations);
        this.fbFederations = fbFederations.stream().collect(
            Collectors.toMap(
                flyoverFederationInformation -> Arrays.hashCode(flyoverFederationInformation.getFlyoverFederationRedeemScriptHash()),
                Function.identity()
            )
        );
    }

    @Override
    protected Optional<FlyoverFederationInformation> getFlyoverFederationInformation(
        byte[] payToScriptHash) {
        FlyoverFederationInformation fbFedInfo = fbFederations.get(Arrays.hashCode(payToScriptHash));
        return fbFedInfo == null? Optional.empty(): Optional.of(fbFedInfo);
    }
}
