package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.FederationMember;
import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;
import co.rsk.peg.bitcoin.NonStandardErpRedeemScriptBuilderFactory;
import co.rsk.peg.bitcoin.P2shErpRedeemScriptBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import java.time.Instant;
import java.util.List;

import static co.rsk.peg.federation.FederationFormatVersion.*;

public class FederationFactory {

    public StandardMultisigFederation buildStandardMultiSigFederation(List<FederationMember> members,
                                                                      Instant creationTime,
                                                                      long creationBlockNumber,
                                                                      NetworkParameters btcParams) {
        return new StandardMultisigFederation(
            members,
            creationTime,
            creationBlockNumber,
            btcParams,
            STANDARD_MULTISIG_FEDERATION.getFormatVersion()
        );
    }

    public ErpFederation buildNonStandardErpFederation(List<FederationMember> members,
                                                       Instant creationTime,
                                                       long creationBlockNumber,
                                                       NetworkParameters btcParams,
                                                       List<BtcECKey> erpPubKeys,
                                                       long activationDelay,
                                                       ActivationConfig.ForBlock activations) {
        ErpRedeemScriptBuilder erpRedeemScriptBuilder =
            NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(activations, btcParams);

        return new ErpFederation(
            members,
            creationTime,
            creationBlockNumber,
            btcParams,
            erpPubKeys,
            activationDelay,
            erpRedeemScriptBuilder,
            NON_STANDARD_ERP_FEDERATION.getFormatVersion()
        );
    }

    public ErpFederation buildP2shErpFederation(List<FederationMember> members,
                                                       Instant creationTime,
                                                       long creationBlockNumber,
                                                       NetworkParameters btcParams,
                                                       List<BtcECKey> erpPubKeys,
                                                       long activationDelay) {

        ErpRedeemScriptBuilder erpRedeemScriptBuilder = new P2shErpRedeemScriptBuilder();
        return new ErpFederation(
            members,
            creationTime,
            creationBlockNumber,
            btcParams,
            erpPubKeys,
            activationDelay,
            erpRedeemScriptBuilder,
            P2SH_ERP_FEDERATION.getFormatVersion()
        );
    }

}
