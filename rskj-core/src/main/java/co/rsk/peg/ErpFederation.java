package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.RedeemScriptParser;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.peg.utils.EcKeyUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import static co.rsk.peg.ErpFederationCreationException.Reason.NULL_OR_EMPTY_EMERGENCY_KEYS;

public class ErpFederation extends Federation {
    private final List<BtcECKey> erpPubKeys;
    private final long activationDelay;
    private final ActivationConfig.ForBlock activations;
    private Script defaultRedeemScript;
    private Script defaultP2SHScript;
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
        if (defaultRedeemScript == null) {
            defaultRedeemScript = getRedeemScriptParser(getRedeemScript())
                .extractStandardRedeemScript();
        }
        return defaultRedeemScript;
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

    private RedeemScriptParser getRedeemScriptParser(Script redeemScript) {
        List<ScriptChunk> chunks = redeemScript.getChunks();
        return RedeemScriptParserFactory.get(chunks);
    }

    public Script getDefaultP2SHScript() {
        if (defaultP2SHScript == null) {
            defaultP2SHScript = ScriptBuilder.createP2SHOutputScript(getDefaultRedeemScript());
        }

        return defaultP2SHScript;
    }

}
