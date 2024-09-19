package co.rsk.peg;

import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.peg.bitcoin.FlyoverRedeemScriptBuilderImpl;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

public abstract class FlyoverCompatibleBtcWallet extends BridgeBtcWallet {

    protected FlyoverCompatibleBtcWallet(Context btcContext, List<Federation> federations) {
        super(btcContext, federations);
    }

    protected abstract Optional<FlyoverFederationInformation> getFlyoverFederationInformation(byte[] payToScriptHash);

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash) {
        Optional<FlyoverFederationInformation> flyoverFederationInformation =
            this.getFlyoverFederationInformation(payToScriptHash);

        if (flyoverFederationInformation.isPresent()) {
            FlyoverFederationInformation flyoverFederationInformationInstance =
                flyoverFederationInformation.get();

            Optional<Federation> destinationFederation = getDestinationFederation(
                flyoverFederationInformationInstance.getFederationRedeemScriptHash()
            );

            if (!destinationFederation.isPresent()) {
                return null;
            }

            Federation destinationFederationInstance = destinationFederation.get();
            Script fedRedeemScript = destinationFederationInstance.getRedeemScript();

            Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                flyoverFederationInformationInstance.getDerivationHash(),
                fedRedeemScript
            );

            return RedeemData.of(destinationFederationInstance.getBtcPublicKeys(), flyoverRedeemScript);
        }

        return super.findRedeemDataFromScriptHash(payToScriptHash);
    }
}
