package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.NetworkParameters;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class FederationArgs {
    private final List<FederationMember> members;
    private final Instant creationTime;
    private final long creationBlockNumber;
    private final NetworkParameters btcParams;

    public FederationArgs(List<FederationMember> members, Instant creationTime,
                          long creationBlockNumber, NetworkParameters btcParams) {
        this.members = members;
        this.creationTime = creationTime;
        this.creationBlockNumber = creationBlockNumber;
        this.btcParams = btcParams;
    }

    public List<FederationMember> getMembers() { return members; }
    public Instant getCreationTime() { return creationTime; }
    public long getCreationBlockNumber() { return creationBlockNumber; }
    public NetworkParameters getBtcParams() { return btcParams; }

    @Override
    public String toString() {
        return String.format("Got federation args with values %s, %s, %d, %s",
            getMembers(), getCreationTime(), getCreationBlockNumber(), getBtcParams()
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        FederationArgs otherFederationArgs = (FederationArgs) other;
        return allValuesAreEqual(otherFederationArgs);
    }

    @Override
    public int hashCode() {
        return
            Objects.hash(getMembers(), getCreationTime(), getCreationBlockNumber(), getBtcParams());
    }

    private boolean allValuesAreEqual(FederationArgs otherFederationArgs) {
        return
            otherFederationArgs.getMembers().equals(this.getMembers())
            && otherFederationArgs.getCreationTime().equals(this.getCreationTime())
            && otherFederationArgs.getCreationBlockNumber() == this.getCreationBlockNumber()
            && otherFederationArgs.getBtcParams().equals(this.getBtcParams());
    }

}
