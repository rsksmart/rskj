package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.NetworkParameters;

import java.time.Instant;
import java.util.List;

public class FederationArgs {
    protected final List<FederationMember> members;
    protected final Instant creationTime;
    protected final long creationBlockNumber;
    protected final NetworkParameters btcParams;

    public FederationArgs(List<FederationMember> members, Instant creationTime,
                          long blockNumber, NetworkParameters btcParams) {
        this.members = members;
        this.creationTime = creationTime;
        this.creationBlockNumber = blockNumber;
        this.btcParams = btcParams;
    }
}
