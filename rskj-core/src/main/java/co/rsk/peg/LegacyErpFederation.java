package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.ErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import java.time.Instant;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class LegacyErpFederation extends ErpFederation {
    private static final Logger logger = LoggerFactory.getLogger(LegacyErpFederation.class);

    public LegacyErpFederation(
        List<FederationMember> members,
        Instant creationTime,
        long creationBlockNumber,
        NetworkParameters btcParams,
        List<BtcECKey> erpPubKeys,
        long activationDelay,
        ActivationConfig.ForBlock activations) {

        super(members, creationTime, creationBlockNumber, btcParams, erpPubKeys, activationDelay, activations);
    }

    @Override
    public Script getRedeemScript() {
        if (!activations.isActive(ConsensusRule.RSKIP284) &&
            btcParams.getId().equals(NetworkParameters.ID_TESTNET)) {
            logger.debug("[getRedeemScript] Returning hardcoded redeem script");
            return new Script(ERP_TESTNET_REDEEM_SCRIPT_BYTES);
        }

        if (redeemScript == null) {
            logger.debug("[getRedeemScript] Creating the redeem script from the keys");
            redeemScript = ErpFederationRedeemScriptParser.createErpRedeemScriptDeprecated(
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
            // i think is not quite right to just "fix" the standardRedeemScript in the buggy fed.
            // the real case is that the OP_CHECKMULTISIG is not before the OP_ELSE... as the method adds.
            standardRedeemScript = ErpFederationRedeemScriptParser.extractStandardRedeemScript(
                getRedeemScript().getChunks()
            );
        }
        return standardRedeemScript;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        LegacyErpFederation otherErpFederation = (LegacyErpFederation) other;

        return this.getNumberOfSignaturesRequired() == otherErpFederation.getNumberOfSignaturesRequired() &&
            this.getSize() == otherErpFederation.getSize() &&
            this.getCreationTime().equals(otherErpFederation.getCreationTime()) &&
            this.creationBlockNumber == otherErpFederation.creationBlockNumber &&
            this.btcParams.equals(otherErpFederation.btcParams) &&
            this.members.equals(otherErpFederation.members) &&
            this.getRedeemScript().equals(otherErpFederation.getRedeemScript()) &&
            this.erpPubKeys.equals(otherErpFederation.erpPubKeys) &&
            this.activationDelay == otherErpFederation.activationDelay;
    }
}

