package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.utils.EcKeyUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import static co.rsk.peg.FederationCreationException.Reason.INVALID_CSV_VALUE;
import static co.rsk.peg.FederationCreationException.Reason.NULL_OR_EMPTY_EMERGENCY_KEYS;

public abstract class ErpFederation extends Federation {
    protected static final long MAX_CSV_VALUE = 65_535L; // 2^16 - 1, since bitcoin will interpret up to 16 bits as the CSV value
    protected final List<BtcECKey> erpPubKeys;
    protected final long activationDelay;
    protected final ActivationConfig.ForBlock activations;
    protected Script standardRedeemScript;
    protected Script standardP2SHScript;

    protected ErpFederation(
        List<FederationMember> members,
        Instant creationTime,
        long creationBlockNumber,
        NetworkParameters btcParams,
        List<BtcECKey> erpPubKeys,
        long activationDelay,
        ActivationConfig.ForBlock activations) {

        super(members, creationTime, creationBlockNumber, btcParams);
        validateErpFederationValues(erpPubKeys, activationDelay);

        this.erpPubKeys = EcKeyUtils.getCompressedPubKeysList(erpPubKeys);
        this.activationDelay = activationDelay;
        this.activations = activations;
    }

    public List<BtcECKey> getErpPubKeys() {
        return Collections.unmodifiableList(erpPubKeys);
    }

    public long getActivationDelay() {
        return activationDelay;
    }

    public abstract Script getStandardRedeemScript();

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
