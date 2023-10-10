package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.P2shErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import java.time.Instant;
import java.util.List;
import co.rsk.rules.Standardness;
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
