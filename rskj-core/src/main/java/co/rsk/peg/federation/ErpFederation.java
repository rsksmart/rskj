package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.RedeemScriptParser;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;
import co.rsk.peg.bitcoin.RedeemScriptCreationException;
import co.rsk.peg.bitcoin.ScriptValidations;
import co.rsk.peg.utils.EcKeyUtils;

import java.util.Collections;
import java.util.List;

import static co.rsk.peg.federation.ErpFederationCreationException.Reason.NULL_OR_EMPTY_EMERGENCY_KEYS;
import static co.rsk.peg.federation.ErpFederationCreationException.Reason.REDEEM_SCRIPT_CREATION_FAILED;
import static co.rsk.peg.federation.FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION;

public class ErpFederation extends Federation {
    private final List<BtcECKey> erpPubKeys;
    private final long activationDelay;
    private final ErpRedeemScriptBuilder erpRedeemScriptBuilder;

    private Script defaultRedeemScript;
    private Script defaultP2SHScript;

    protected ErpFederation(
        FederationArgs federationArgs,
        List<BtcECKey> erpPubKeys,
        long activationDelay,
        ErpRedeemScriptBuilder erpRedeemScriptBuilder,
        int formatVersion
    ) {
        super(federationArgs, formatVersion);
        validateEmergencyKeys(erpPubKeys);

        this.erpPubKeys = EcKeyUtils.getCompressedPubKeysList(erpPubKeys);
        this.activationDelay = activationDelay;
        this.erpRedeemScriptBuilder = erpRedeemScriptBuilder;
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
            RedeemScriptParser redeemScriptParser = getRedeemScriptParser();
            List<ScriptChunk> defaultRedeemScriptChunks = redeemScriptParser.extractStandardRedeemScriptChunks();
            defaultRedeemScript = new ScriptBuilder().addChunks(defaultRedeemScriptChunks).build();
        }
        return defaultRedeemScript;
    }

    @Override
    public Script getRedeemScript() {
        if (redeemScript == null) {
            try {
                redeemScript = erpRedeemScriptBuilder.of(
                    getBtcPublicKeys(),
                    getNumberOfSignaturesRequired(),
                    erpPubKeys,
                    getNumberOfEmergencySignaturesRequired(),
                    activationDelay
                );

                validateScriptSize();
            } catch (RedeemScriptCreationException e) {
                throw new ErpFederationCreationException(e.getMessage(), e, REDEEM_SCRIPT_CREATION_FAILED);
            }
        }
        return redeemScript;
    }

    private void validateScriptSize() {
        if (getFormatVersion() != P2SH_P2WSH_ERP_FEDERATION.getFormatVersion()) {
            // since the redeem script is located in the script sig,
            // we need to check it does not surpass the maximum allowed size
            ScriptValidations.validateScriptSigElementSize(redeemScript);
            return;
        }

        ScriptValidations.validateWitnessScriptSize(redeemScript, getNumberOfSignaturesRequired());
    }

    private RedeemScriptParser getRedeemScriptParser() {
        Script redeemScript = getRedeemScript();
        List<ScriptChunk> chunks = redeemScript.getChunks();
        return RedeemScriptParserFactory.get(chunks);
    }

    @Override
    public Script getP2SHScript() {
        if (p2shScript == null) {
            p2shScript = getOutputScript(getRedeemScript());
        }

        return p2shScript;
    }

    public Script getDefaultP2SHScript() {
        if (defaultP2SHScript == null) {
            defaultP2SHScript = getOutputScript(getDefaultRedeemScript());
        }

        return defaultP2SHScript;
    }

    private Script getOutputScript(Script redeemScript) {
        if (formatVersion != P2SH_P2WSH_ERP_FEDERATION.getFormatVersion()){
            return ScriptBuilder.createP2SHOutputScript(redeemScript);
        }

        return ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);
    }

    private void validateEmergencyKeys(List<BtcECKey> erpPubKeys) {
        if (erpPubKeys == null || erpPubKeys.isEmpty()) {
            String message = "Emergency keys are not provided";
            throw new ErpFederationCreationException(message, NULL_OR_EMPTY_EMERGENCY_KEYS);
        }
    }
}
