package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import org.ethereum.crypto.ECKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class StandardMultiSigFederationBuilder {
    private List<BtcECKey> membersBtcPublicKeys;
    private List<ECKey> membersRskPublicKeys;
    private List<ECKey> membersMstPublicKeys;
    private Instant creationTime;
    private long creationBlockNumber;
    private NetworkParameters networkParameters;

    public StandardMultiSigFederationBuilder() {
        this.membersBtcPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09"}, true
        );
        this.membersRskPublicKeys = new ArrayList<>();
        this.membersMstPublicKeys = new ArrayList<>();
        this.creationTime = Instant.ofEpochMilli(0);
        this.creationBlockNumber = 0;
        this.networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
    }

    public StandardMultiSigFederationBuilder withMembersBtcPublicKeys(List<BtcECKey> membersPublicKeys) {
        this.membersBtcPublicKeys = membersPublicKeys;
        return this;
    }

    public StandardMultiSigFederationBuilder withMembersRskPublicKeys(List<ECKey> rskPublicKeys) {
        this.membersRskPublicKeys = rskPublicKeys;
        return this;
    }

    public StandardMultiSigFederationBuilder withMembersMstPublicKeys(List<ECKey> mstPublicKeys) {
        this.membersMstPublicKeys = mstPublicKeys;
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
        List<FederationMember> federationMembers = getFederationMembers();
        FederationArgs federationArgs = new FederationArgs(federationMembers, creationTime, creationBlockNumber, networkParameters);

        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    private List<FederationMember> getFederationMembers() {
        if (membersRskPublicKeys.isEmpty()) {
            getRskPublicKeysFromBtcPublicKeys();
        }
        if (membersMstPublicKeys.isEmpty()) {
            getMstPublicKeysFromRskPublicKeys();
        }

        List<FederationMember> federationMembers = new ArrayList<>();

        for (int i = 0; i < membersBtcPublicKeys.size(); i++) {
            FederationMember federationMember;

            BtcECKey memberBtcPublicKey = membersBtcPublicKeys.get(i);
            ECKey memberRskPublicKey = membersRskPublicKeys.get(i);
            ECKey memberMstPublicKey = membersMstPublicKeys.get(i);

            federationMember = new FederationMember(memberBtcPublicKey, memberRskPublicKey, memberMstPublicKey);
            federationMembers.add(federationMember);
        }
        return federationMembers;
    }

    private void getRskPublicKeysFromBtcPublicKeys() {
        for (BtcECKey btcPublicKey : membersBtcPublicKeys) {
            membersRskPublicKeys.add(ECKey.fromPublicOnly(btcPublicKey.getPubKey()));
        }
    }

    private void getMstPublicKeysFromRskPublicKeys() {
        membersMstPublicKeys.addAll(membersRskPublicKeys);
    }
}
