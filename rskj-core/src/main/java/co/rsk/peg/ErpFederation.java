package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.utils.EcKeyUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import static co.rsk.peg.FederationCreationException.Reason.INVALID_CSV_VALUE;
import static co.rsk.peg.FederationCreationException.Reason.NULL_OR_EMPTY_EMERGENCY_KEYS;

public class ErpFederation extends Federation {
    protected static final long MAX_CSV_VALUE = 65_535L; // 2^16 - 1, since bitcoin will interpret up to 16 bits as the CSV value
    protected final List<BtcECKey> erpPubKeys;
    protected final long activationDelay;
    protected final ActivationConfig.ForBlock activations;
    protected Script standardRedeemScript;
    protected Script standardP2SHScript;

    protected ErpRedeemScriptBuilder erpRedeemScriptBuilder;

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
        validateErpFederationValues(erpPubKeys, activationDelay);

        this.erpPubKeys = EcKeyUtils.getCompressedPubKeysList(erpPubKeys);
        this.activationDelay = activationDelay;
        this.activations = activations;
        this.erpRedeemScriptBuilder = erpRedeemScriptBuilder;

        // TODO discuss if this validation should be here.
        FederationUtils.validateScriptSize(getRedeemScript());
    }

    public List<BtcECKey> getDefaultPublicKeys() {
        List<BtcECKey> defaultPubKeys = new ArrayList<>();
        for (FederationMember member : members) {
            defaultPubKeys.add(member.getBtcPublicKey());
        }
        return Collections.unmodifiableList(defaultPubKeys);
    }

    public List<BtcECKey> getErpPubKeys() {
        return Collections.unmodifiableList(erpPubKeys);
    }

    public long getActivationDelay() {
        return activationDelay;
    }

    public Script getStandardRedeemScript() {
        if (standardRedeemScript == null) {
            standardRedeemScript = RedeemScriptParserFactory.get(getRedeemScript().getChunks())
                .extractStandardRedeemScript();
        }
        return standardRedeemScript;
    }


    @Override
    public Script getRedeemScript() {
        if (redeemScript == null) {
                redeemScript = erpRedeemScriptBuilder.createRedeemScript(getDefaultPublicKeys(), erpPubKeys, activationDelay);
        }
        return redeemScript;
    }

    public Script getStandardP2SHScript() {
        if (standardP2SHScript == null) {
            standardP2SHScript = ScriptBuilder.createP2SHOutputScript(getStandardRedeemScript());
        }

        return standardP2SHScript;
    }

    private void validateErpFederationValues(List<BtcECKey> erpPubKeys, long activationDelay) {
        if (erpPubKeys == null || erpPubKeys.isEmpty()) {
            String message = "Emergency keys are not provided";
            throw new FederationCreationException(message, NULL_OR_EMPTY_EMERGENCY_KEYS);
        }

        if (activationDelay <= 0 || activationDelay > MAX_CSV_VALUE) {
            String message = String.format(
                "Provided csv value %d must be larger than 0 and lower than %d",
                activationDelay,
                MAX_CSV_VALUE
            );
            throw new FederationCreationException(message, INVALID_CSV_VALUE);
        }
    }
}
