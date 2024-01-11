package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static co.rsk.peg.federation.ErpFederationCreationException.Reason.NULL_OR_EMPTY_EMERGENCY_KEYS;

public class ErpFederationArgs extends FederationArgs{
    private final List<BtcECKey> erpPubKeys;
    private final long activationDelay;

    public ErpFederationArgs(
        List<FederationMember> members,
        Instant creationTime,
        long creationBlockNumber,
        NetworkParameters btcParams,
        List<BtcECKey> erpPubKeys,
        long activationDelay
    ) {
        super(members, creationTime, creationBlockNumber, btcParams);

        validateEmergencyKeys(erpPubKeys);
        this.erpPubKeys = erpPubKeys;
        this.activationDelay = activationDelay;
    }

    public List<BtcECKey> getErpPubKeys() { return erpPubKeys; }

    public long getActivationDelay() { return activationDelay; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        ErpFederationArgs otherErpFederationArgs = (ErpFederationArgs) other;
        return allValuesAreEqual(otherErpFederationArgs);
    }

    @Override
    public int hashCode() {
        return
            Objects.hash(getMembers(), getCreationTime(), getCreationBlockNumber(), getBtcParams(), getErpPubKeys(), getActivationDelay());
    }

    private boolean allValuesAreEqual(ErpFederationArgs otherErpFederationArgs) {
        return
            otherErpFederationArgs.getMembers().equals(this.getMembers())
            && otherErpFederationArgs.getCreationTime().equals(this.getCreationTime())
            && otherErpFederationArgs.getCreationBlockNumber() == this.getCreationBlockNumber()
            && otherErpFederationArgs.getBtcParams().equals(this.getBtcParams())
            && otherErpFederationArgs.getErpPubKeys().equals(this.getErpPubKeys())
            && otherErpFederationArgs.getActivationDelay() == this.getActivationDelay();
    }

    public static ErpFederationArgs fromFederationArgs(FederationArgs federationArgs, List<BtcECKey> erpPubKeys, long activationDelay){
        return new ErpFederationArgs(federationArgs.getMembers(), federationArgs.getCreationTime(),
            federationArgs.getCreationBlockNumber(), federationArgs.getBtcParams(), erpPubKeys, activationDelay);
    }

    private void validateEmergencyKeys(List<BtcECKey> erpPubKeys) {
        if (erpPubKeys == null || erpPubKeys.isEmpty()) {
            String message = "Emergency keys are not provided";
            throw new ErpFederationCreationException(message, NULL_OR_EMPTY_EMERGENCY_KEYS);
        }
    }
}
