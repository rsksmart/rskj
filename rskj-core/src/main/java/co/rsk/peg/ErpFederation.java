package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.utils.EcKeyUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import static co.rsk.peg.ErpFederationCreationException.Reason.NULL_OR_EMPTY_EMERGENCY_KEYS;

public class ErpFederation extends Federation {
    protected final List<BtcECKey> erpPubKeys;
    protected final long activationDelay;
    protected final ActivationConfig.ForBlock activations;
    protected Script standardRedeemScript;
    protected Script standardP2SHScript;
    private ErpRedeemScriptBuilder erpRedeemScriptBuilder;

    protected ErpFederation(
        List<FederationMember> members,
        Instant creationTime,
        long creationBlockNumber,
        NetworkParameters btcParams,
        List<BtcECKey> erpPubKeys,
        long activationDelay,
        ActivationConfig.ForBlock activations,
        ErpRedeemScriptBuilder erpRedeemScriptBuilder) {

        super(members, creationTime, creationBlockNumber, btcParams);
        validateEmergencyKeys(erpPubKeys);

        this.erpPubKeys = EcKeyUtils.getCompressedPubKeysList(erpPubKeys);
        this.activationDelay = activationDelay;
        this.activations = activations;
        this.erpRedeemScriptBuilder = erpRedeemScriptBuilder;
    }

    private void validateEmergencyKeys(List<BtcECKey> erpPubKeys) {
        if (erpPubKeys == null || erpPubKeys.isEmpty()) {
            String message = "Emergency keys are not provided";
            throw new ErpFederationCreationException(message, NULL_OR_EMPTY_EMERGENCY_KEYS);
        }
    }

    public ErpRedeemScriptBuilder getErpRedeemScriptBuilder() { return erpRedeemScriptBuilder; }

    public List<BtcECKey> getErpPubKeys() {
        return Collections.unmodifiableList(erpPubKeys);
    }


    public int getNumberOfEmergencySignaturesRequired() {
        return erpPubKeys.size() / 2 + 1;
    }

    public long getActivationDelay() {
        return activationDelay;
    }

    public Script getDefaultRedeemScript() {
        if (standardRedeemScript == null) {
            standardRedeemScript = RedeemScriptParserFactory.get(getRedeemScript().getChunks())
                .extractStandardRedeemScript();
        }
        return standardRedeemScript;
    }

    @Override
    public Script getRedeemScript() {
        if (redeemScript == null) {
                redeemScript = erpRedeemScriptBuilder.createRedeemScriptFromKeys(
                    getBtcPublicKeys(),
                    getNumberOfSignaturesRequired(),
                    erpPubKeys,
                    getNumberOfEmergencySignaturesRequired(),
                    activationDelay
                );
        }
        return redeemScript;
    }

    public Script getDefaultP2SHScript() {
        if (standardP2SHScript == null) {
            standardP2SHScript = ScriptBuilder.createP2SHOutputScript(getDefaultRedeemScript());
        }

        return standardP2SHScript;
    }

}
