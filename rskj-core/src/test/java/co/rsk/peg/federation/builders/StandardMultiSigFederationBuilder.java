package co.rsk.peg.federation.builders;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;

import java.time.Instant;
import java.util.List;

public class StandardMultiSigFederationBuilder {
    private List<BtcECKey> membersPublicKeys;
    private Instant creationTime;
    private long creationBlockNumber;
    private NetworkParameters networkParameters;

    public StandardMultiSigFederationBuilder() {
        this.membersPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09"}, true
        );
        this.creationTime = Instant.ofEpochMilli(0);
        this.creationBlockNumber = 0;
        this.networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
    }

    public StandardMultiSigFederationBuilder withMembersPublicKeys(List<BtcECKey> membersPublicKeys) {
        this.membersPublicKeys = membersPublicKeys;
        return this;
    }

    public StandardMultiSigFederationBuilder withCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
        return this;
    }

    public StandardMultiSigFederationBuilder withCreationBlockNumber(long creationBlockNumber) {
        this.creationBlockNumber = creationBlockNumber;
        return this;
    }

    public StandardMultiSigFederationBuilder withNetworkParameters(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
        return this;
    }

    public Federation build() {
        List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(membersPublicKeys);
        FederationArgs federationArgs = new FederationArgs(federationMembers, creationTime, creationBlockNumber, networkParameters);

        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }
}
