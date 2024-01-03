package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;
import co.rsk.peg.bitcoin.NonStandardErpRedeemScriptBuilderFactory;
import co.rsk.peg.bitcoin.P2shErpRedeemScriptBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import static co.rsk.peg.federation.FederationFormatVersion.*;

public class FederationFactory {

    private FederationFactory() {}

    public static StandardMultisigFederation buildStandardMultiSigFederation(FederationArgs federationArgs) {
        return new StandardMultisigFederation(
            federationArgs,
            STANDARD_MULTISIG_FEDERATION.getFormatVersion()
        );
    }

    public static ErpFederation buildNonStandardErpFederation(ErpFederationArgs erpFederationArgs,
                                                              ActivationConfig.ForBlock activations) {
        NetworkParameters btcParams = erpFederationArgs.btcParams;
        ErpRedeemScriptBuilder erpRedeemScriptBuilder =
            NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(activations, btcParams);

        return new ErpFederation(erpFederationArgs, erpRedeemScriptBuilder, NON_STANDARD_ERP_FEDERATION.getFormatVersion()
        );
    }

    public static ErpFederation buildP2shErpFederation(ErpFederationArgs erpFederationArgs) {
        ErpRedeemScriptBuilder erpRedeemScriptBuilder = new P2shErpRedeemScriptBuilder();
        return new ErpFederation(erpFederationArgs, erpRedeemScriptBuilder, P2SH_ERP_FEDERATION.getFormatVersion()
        );
    }
}
