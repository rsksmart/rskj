package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.NetworkParameters;

import java.time.Instant;
import java.util.List;

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

    public List<FederationMember> getMembers() {
        return members;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public long getCreationBlockNumber() { return creationBlockNumber; }
    public NetworkParameters getBtcParams() { return btcParams; }

}
