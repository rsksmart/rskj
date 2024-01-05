package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;

import java.time.Instant;
import java.util.List;

import static co.rsk.peg.federation.ErpFederationCreationException.Reason.NULL_OR_EMPTY_EMERGENCY_KEYS;

public class ErpFederationArgs extends FederationArgs{
    protected final List<BtcECKey> erpPubKeys;
    protected final long activationDelay;
    public ErpFederationArgs(
        List<FederationMember> members,
        Instant creationTime,
        long blockNumber,
        NetworkParameters btcParams,
        List<BtcECKey> erpPubKeys,
        long activationDelay
    ) {
        super(members, creationTime, blockNumber, btcParams);

        validateEmergencyKeys(erpPubKeys);
        this.erpPubKeys = erpPubKeys;
        this.activationDelay = activationDelay;
    }

    private void validateEmergencyKeys(List<BtcECKey> erpPubKeys) {
        if (erpPubKeys == null || erpPubKeys.isEmpty()) {
            String message = "Emergency keys are not provided";
            throw new ErpFederationCreationException(message, NULL_OR_EMPTY_EMERGENCY_KEYS);
        }
    }
}
