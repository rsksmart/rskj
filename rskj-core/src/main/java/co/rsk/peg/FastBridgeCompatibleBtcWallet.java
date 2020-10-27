package co.rsk.peg;

import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.script.RedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

public class FastBridgeCompatibleBtcWallet extends BridgeBtcWallet {
    private final BridgeStorageProvider bridgeStorageProvider;

    public FastBridgeCompatibleBtcWallet(
        Context btcContext,
        List<Federation> federations,
        BridgeStorageProvider bridgeStorageProvider
    ) {
        super(btcContext, federations);
        this.bridgeStorageProvider = bridgeStorageProvider;
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash) {
        Optional<FastBridgeFederationInformation> fastBridgeFederationInformation =
            this.bridgeStorageProvider.getFastBridgeFederationInformation(payToScriptHash);

        if (fastBridgeFederationInformation.isPresent()) {
            FastBridgeFederationInformation fastBridgeFederationInformationInstance =
                fastBridgeFederationInformation.get();

            Optional<Federation> destinationFederation = getDestinationFederation(
                fastBridgeFederationInformationInstance.getFederationScriptHash()
            );

            if (!destinationFederation.isPresent()) {
                return null;
            }

            Federation destinationFederationInstance = destinationFederation.get();
            Script fedRedeemScript = destinationFederationInstance.getRedeemScript();
            Script fastBridgeRedeemScript = RedeemScriptParser
                .createMultiSigFastBridgeRedeemScript(fedRedeemScript,
                    fastBridgeFederationInformationInstance.getDerivationHash());

            return RedeemData.of(destinationFederationInstance.getBtcPublicKeys(), fastBridgeRedeemScript);
        }

        return super.findRedeemDataFromScriptHash(payToScriptHash);
    }
}
