package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.ErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.utils.EcKeyUtils;
import java.time.Instant;
import java.util.List;

public class ErpFederation extends Federation {
    private final List<BtcECKey> erpPubKeys;
    private final long activationDelay;

    public ErpFederation(
        List<FederationMember> members,
        Instant creationTime,
        long creationBlockNumber,
        NetworkParameters btcParams,
        List<BtcECKey> erpPubKeys,
        long activationDelay
    ) {
        super(members, creationTime, creationBlockNumber, btcParams);
        this.erpPubKeys = EcKeyUtils.getCompressedPubKeysList(erpPubKeys);
        this.activationDelay = activationDelay;
    }

    public List<BtcECKey> getErpPubKeys() {
        return erpPubKeys;
    }

    public long getActivationDelay() {
        return activationDelay;
    }

    @Override
    public Script getRedeemScript() {
        if (redeemScript == null) {
            redeemScript = ErpFederationRedeemScriptParser.createErpRedeemScript(
                ScriptBuilder.createRedeemScript(getNumberOfSignaturesRequired(), getBtcPublicKeys()),
                ScriptBuilder.createRedeemScript(2, erpPubKeys),
                activationDelay
            );
        }

        return redeemScript;
    }

    @Override
    public Script getP2SHScript() {
        if (p2shScript == null) {
            p2shScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        }

        return p2shScript;
    }
}
