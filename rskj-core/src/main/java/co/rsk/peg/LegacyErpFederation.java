package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.ErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import java.time.Instant;
import java.util.List;

import co.rsk.rules.Standardness;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated This class represents a Legacy ERP Federation that is non-standard.
 *             It has been deprecated but it must be kept because of backwards compatibility.
 */

@Deprecated
public class LegacyErpFederation extends ErpFederation {
    private static final Logger logger = LoggerFactory.getLogger(LegacyErpFederation.class);
    private static final byte[] LEGACY_ERP_TESTNET_REDEEM_SCRIPT_BYTES = Hex.decode("6453210208f40073a9e43b3e9103acec79767a6de9b0409749884e989960fee578012fce210225e892391625854128c5c4ea4340de0c2a70570f33db53426fc9c746597a03f42102afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da210344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a0921039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb955670300cd50b27552210216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3210275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f1421034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f5368ae");

    public LegacyErpFederation(
        List<FederationMember> members,
        Instant creationTime,
        long creationBlockNumber,
        NetworkParameters btcParams,
        List<BtcECKey> erpPubKeys,
        long activationDelay,
        ActivationConfig.ForBlock activations) {

        super(members, creationTime, creationBlockNumber, btcParams, erpPubKeys, activationDelay, activations);

        validateRedeemScript();
    }

    @Override
    public Script getRedeemScript() {
        if (!activations.isActive(ConsensusRule.RSKIP284) &&
            btcParams.getId().equals(NetworkParameters.ID_TESTNET)) {
            logger.debug("[getRedeemScript] Returning hardcoded redeem script");
            return new Script(LEGACY_ERP_TESTNET_REDEEM_SCRIPT_BYTES);
        }

        if (redeemScript == null) {
            logger.debug("[getRedeemScript] Creating the redeem script from the keys");
            redeemScript = activations.isActive(ConsensusRule.RSKIP293) ?
                ErpFederationRedeemScriptParser.createErpRedeemScript(
                    ScriptBuilder.createRedeemScript(getNumberOfSignaturesRequired(), getBtcPublicKeys()),
                    ScriptBuilder.createRedeemScript(erpPubKeys.size() / 2 + 1, erpPubKeys),
                    activationDelay
                ) :
                ErpFederationRedeemScriptParser.createErpRedeemScriptDeprecated(
                    ScriptBuilder.createRedeemScript(getNumberOfSignaturesRequired(), getBtcPublicKeys()),
                    ScriptBuilder.createRedeemScript(erpPubKeys.size() / 2 + 1, erpPubKeys),
                    activationDelay
                );
        }

        return redeemScript;
    }

    @Override
    public Script getStandardRedeemScript() {
        if (standardRedeemScript == null) {
            standardRedeemScript = ErpFederationRedeemScriptParser.extractStandardRedeemScript(
                getRedeemScript().getChunks()
            );
        }
        return standardRedeemScript;
    }

    private void validateRedeemScript() {
        if (activations.isActive(ConsensusRule.RSKIP293) &&
            this.getRedeemScript().equals(new Script(LEGACY_ERP_TESTNET_REDEEM_SCRIPT_BYTES))) {

            String message = "Unable to create ERP Federation. The obtained redeem script matches the one hardcoded for testnet. "
                + "This would cause bitcoinj-thin to identify it as invalid";
            logger.debug("[validateRedeemScript] {}", message);
            throw new FederationCreationException(message);
        }
    }

    @Override
    public void validateScriptSigSize() {
        // we have to check if the size of every script inside the scriptSig is not above the maximum
        // this scriptSig contains the signatures, the redeem script and some other bytes
        // so it is ok to just check the redeem script size

        int bytesFromRedeemScript = getRedeemScript().getProgram().length;

        if (bytesFromRedeemScript > Standardness.MAX_SCRIPT_ELEMENT_SIZE
        ) {
            String message = "Unable to create Federation. The scriptSig size is above the maximum allowed.";
            throw new FederationCreationException(message);
        }
    }
}

