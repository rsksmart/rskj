package co.rsk.peg;

import co.rsk.bitcoinj.core.Context;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FastBridgeCompatibleBtcWalletWithMultipleScripts extends FastBridgeCompatibleBtcWallet {
    private final Map<Integer, FastBridgeFederationInformation> fbFederations;

    public FastBridgeCompatibleBtcWalletWithMultipleScripts(
        Context btcContext,
        List<Federation> federations,
        List<FastBridgeFederationInformation> fbFederations
    ) {
        super(btcContext, federations);
        this.fbFederations = fbFederations.stream().collect(
            Collectors.toMap(
                fastBridgeFederationInformation -> Arrays.hashCode(fastBridgeFederationInformation.getFastBridgeFederationRedeemScriptHash()),
                Function.identity()
            )
        );
    }

    @Override
    protected Optional<FastBridgeFederationInformation> getFastBridgeFederationInformation(
        byte[] payToScriptHash) {
        FastBridgeFederationInformation fbFedInfo = fbFederations.get(Arrays.hashCode(payToScriptHash));
        return fbFedInfo == null? Optional.empty(): Optional.of(fbFedInfo);
    }
}
