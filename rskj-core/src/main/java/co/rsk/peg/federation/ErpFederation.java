package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.RedeemScriptParser;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.peg.bitcoin.ErpRedeemScriptBuilder;
import co.rsk.peg.bitcoin.RedeemScriptCreationException;
import co.rsk.peg.utils.EcKeyUtils;
import java.util.Collections;
import java.util.List;

import static co.rsk.peg.federation.ErpFederationCreationException.Reason.REDEEM_SCRIPT_CREATION_FAILED;

public class ErpFederation extends Federation {
    private final List<BtcECKey> erpPubKeys;
    private final long activationDelay;
    private Script defaultRedeemScript;
    private Script defaultP2SHScript;
    private final ErpRedeemScriptBuilder erpRedeemScriptBuilder;

    protected ErpFederation(
        ErpFederationArgs erpFederationArgs,
        ErpRedeemScriptBuilder erpRedeemScriptBuilder,
        int formatVersion
    ) {
        super(erpFederationArgs, formatVersion);

        this.erpPubKeys = EcKeyUtils.getCompressedPubKeysList(erpFederationArgs.getErpPubKeys());
        this.activationDelay = erpFederationArgs.getActivationDelay();
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
            defaultRedeemScript = redeemScriptParser.extractStandardRedeemScript();
        }
        return defaultRedeemScript;
    }

    @Override
    public Script getRedeemScript() {
        if (redeemScript == null) {
            try {
                redeemScript = erpRedeemScriptBuilder.createRedeemScriptFromKeys(
                    getBtcPublicKeys(),
                    getNumberOfSignaturesRequired(),
                    erpPubKeys,
                    getNumberOfEmergencySignaturesRequired(),
                    activationDelay
                );
            } catch (RedeemScriptCreationException e) {
                throw new ErpFederationCreationException(e.getMessage(), e, REDEEM_SCRIPT_CREATION_FAILED);
            }
        }
        return redeemScript;
    }

    private RedeemScriptParser getRedeemScriptParser() {
        Script redeemScript = getRedeemScript();
        List<ScriptChunk> chunks = redeemScript.getChunks();
        return RedeemScriptParserFactory.get(chunks);
    }

    public Script getDefaultP2SHScript() {
        if (defaultP2SHScript == null) {
            defaultP2SHScript = ScriptBuilder
                .createP2SHOutputScript(getDefaultRedeemScript());
        }

        return defaultP2SHScript;
    }

}
