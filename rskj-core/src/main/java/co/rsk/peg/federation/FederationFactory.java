package co.rsk.peg.federation;

import static co.rsk.peg.federation.FederationFormatVersion.*;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;
import co.rsk.peg.bitcoin.NonStandardErpRedeemScriptBuilderFactory;
import co.rsk.peg.bitcoin.P2shErpRedeemScriptBuilder;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

public class FederationFactory {

    private FederationFactory() {}

    public static StandardMultisigFederation buildStandardMultiSigFederation(FederationArgs federationArgs) {

        return new StandardMultisigFederation(
            federationArgs,
            STANDARD_MULTISIG_FEDERATION.getFormatVersion()
        );
    }

    public static ErpFederation buildNonStandardErpFederation(
        FederationArgs federationArgs,
        List<BtcECKey> erpPubKeys,
        long activationDelay,
        ActivationConfig.ForBlock activations
    ) {
        NetworkParameters btcParams = federationArgs.getBtcParams();
        ErpRedeemScriptBuilder erpRedeemScriptBuilder =
            NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(activations, btcParams);

        return new ErpFederation(
            federationArgs,
            erpPubKeys,
            activationDelay,
            erpRedeemScriptBuilder,
            NON_STANDARD_ERP_FEDERATION.getFormatVersion()
        );
    }

    public static ErpFederation buildP2shErpFederation(
        FederationArgs federationArgs,
        List<BtcECKey> erpPubKeys,
        long activationDelay
    ) {
        ErpRedeemScriptBuilder erpRedeemScriptBuilder = P2shErpRedeemScriptBuilder.builder();

        return new ErpFederation(
            federationArgs,
            erpPubKeys,
            activationDelay,
            erpRedeemScriptBuilder,
            P2SH_ERP_FEDERATION.getFormatVersion()
        );
    }

    public static ErpFederation buildP2shP2wshErpFederation(
        FederationArgs federationArgs,
        List<BtcECKey> erpPubKeys,
        long activationDelay
    ) {
        ErpRedeemScriptBuilder erpRedeemScriptBuilder = P2shErpRedeemScriptBuilder.builder();

        return new ErpFederation(
            federationArgs,
            erpPubKeys,
            activationDelay,
            erpRedeemScriptBuilder,
            P2SH_P2WSH_ERP_FEDERATION.getFormatVersion()
        );
    }
}
