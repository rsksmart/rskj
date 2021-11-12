package co.rsk.peg;

import co.rsk.bitcoinj.deprecated.core.Context;
import co.rsk.bitcoinj.deprecated.core.Sha256Hash;
import co.rsk.bitcoinj.deprecated.script.FastBridgeErpRedeemScriptParser;
import co.rsk.bitcoinj.deprecated.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.deprecated.script.RedeemScriptParser;
import co.rsk.bitcoinj.deprecated.script.RedeemScriptParser.MultiSigType;
import co.rsk.bitcoinj.deprecated.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.deprecated.script.Script;
import co.rsk.bitcoinj.deprecated.wallet.RedeemData;
import co.rsk.bitcoinj.utils.DeprecatedConverter;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

public abstract class FastBridgeCompatibleBtcWalletDeprecated extends BridgeBtcWalletDeprecated {

    protected FastBridgeCompatibleBtcWalletDeprecated(Context btcContext, List<Federation> federations) {
        super(btcContext, federations);
    }

    protected abstract Optional<FastBridgeFederationInformation> getFastBridgeFederationInformation(byte[] payToScriptHash);

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash) {
        Optional<FastBridgeFederationInformation> fastBridgeFederationInformation =
            this.getFastBridgeFederationInformation(payToScriptHash);

        if (fastBridgeFederationInformation.isPresent()) {
            FastBridgeFederationInformation fastBridgeFederationInformationInstance =
                fastBridgeFederationInformation.get();

            Optional<Federation> destinationFederation = getDestinationFederation(
                fastBridgeFederationInformationInstance.getFederationRedeemScriptHash()
            );

            if (!destinationFederation.isPresent()) {
                return null;
            }

            Federation destinationFederationInstance = destinationFederation.get();
            Script fedRedeemScript = DeprecatedConverter.toDeprecated(destinationFederationInstance.getRedeemScript());

            RedeemScriptParser parser = RedeemScriptParserFactory.get(fedRedeemScript.getChunks());
            Script fastBridgeRedeemScript;

            if (parser.getMultiSigType() == MultiSigType.ERP_FED) {
                fastBridgeRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
                    fedRedeemScript,
                    Sha256Hash.wrap(fastBridgeFederationInformationInstance
                        .getDerivationHash()
                        .getBytes()
                    )
                );
            } else {
                fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
                    fedRedeemScript,
                    Sha256Hash.wrap(fastBridgeFederationInformationInstance
                        .getDerivationHash()
                        .getBytes()
                    )
                );
            }

            return RedeemData.of(
                DeprecatedConverter.toDeprecatedKeys(destinationFederationInstance.getBtcPublicKeys()),
                fastBridgeRedeemScript
            );
        }

        return super.findRedeemDataFromScriptHash(payToScriptHash);
    }
}
