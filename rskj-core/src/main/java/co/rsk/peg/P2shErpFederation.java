package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.P2shErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import java.time.Instant;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class P2shErpFederation extends ErpFederation {
    private static final Logger logger = LoggerFactory.getLogger(P2shErpFederation.class);

    public P2shErpFederation(
        List<FederationMember> members,
        Instant creationTime,
        long creationBlockNumber,
        NetworkParameters btcParams,
        List<BtcECKey> erpPubKeys,
        long activationDelay,
        ActivationConfig.ForBlock activations
    ) {
        super(members, creationTime, creationBlockNumber, btcParams, erpPubKeys, activationDelay, activations);
    }

    @Override
    public final Script getRedeemScript() {
        if (redeemScript == null) {
            logger.debug("[getRedeemScript] Creating the redeem script from the keys");
            redeemScript = P2shErpFederationRedeemScriptParser.createP2shErpRedeemScript(
                ScriptBuilder.createRedeemScript(getNumberOfSignaturesRequired(), getBtcPublicKeys()),
                ScriptBuilder.createRedeemScript(erpPubKeys.size() / 2 + 1, erpPubKeys),
                activationDelay
            );
        }

        return redeemScript;
    }

    @Override
    public final Script getStandardRedeemScript() {
        if (standardRedeemScript == null) {
            standardRedeemScript = P2shErpFederationRedeemScriptParser.extractStandardRedeemScript(
                getRedeemScript().getChunks()
            );
        }
        return standardRedeemScript;
    }

    @Override
    public boolean equals(Object other){
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        P2shErpFederation otherErpFederation = (P2shErpFederation) other;
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
