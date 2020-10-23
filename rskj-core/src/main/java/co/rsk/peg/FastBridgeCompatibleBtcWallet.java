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

    public FastBridgeCompatibleBtcWallet(Context btcContext, List<Federation> federations,
        BridgeStorageProvider bridgeStorageProvider) {
        super(btcContext, federations);
        this.bridgeStorageProvider = bridgeStorageProvider;
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash) {
        Optional<FastBridgeFederationInformation> fastBridgeFederationInformation =
            this.bridgeStorageProvider.getFastBridgeFederationInformation(payToScriptHash);

        if (fastBridgeFederationInformation.isPresent()) {
            Optional<Federation> destinationFederation = getDestinationFederation(payToScriptHash);

            if (!destinationFederation.isPresent()) {
                return null;
            }

            Script fedRedeemScript = destinationFederation.get().getRedeemScript();
            Script fastBridgeRedeemScript = RedeemScriptParser
                .createMultiSigFastBridgeRedeemScript(fedRedeemScript,
                    fastBridgeFederationInformation.get().getDerivationHash());

            return RedeemData.of(destinationFederation.get().getBtcPublicKeys(), fastBridgeRedeemScript);
        }

        return super.findRedeemDataFromScriptHash(payToScriptHash);
    }
}
