package co.rsk.peg.federation;

import java.time.Instant;
import java.util.List;

public class FederationArgs {
    protected final List<FederationMember> members;
    protected final Instant creationTime;
    protected final long creationBlockNumber;

    public FederationArgs(List<FederationMember> members, Instant creationTime, long blockNumber) {
        this.members = members;
        this.creationTime = creationTime;
        this.creationBlockNumber = blockNumber;
    }
}
