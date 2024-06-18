package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import org.ethereum.crypto.ECKey;

import java.util.ArrayList;
import java.util.List;

public class PendingFederationBuilder {
    private List<BtcECKey> membersBtcPublicKeys;
    private List<ECKey> membersRskPublicKeys;
    private List<ECKey> membersMstPublicKeys;

    public PendingFederationBuilder(){
        this.membersBtcPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09"}, true
        );
        this.membersRskPublicKeys = new ArrayList<>();
        this.membersMstPublicKeys = new ArrayList<>();
    }

    public PendingFederationBuilder withMembersBtcPublicKeys(List<BtcECKey> btcPublicKeys) {
        this.membersBtcPublicKeys = btcPublicKeys;
        return this;
    }

    public PendingFederationBuilder withMembersRskPublicKeys(List<ECKey> rskPublicKeys) {
        this.membersRskPublicKeys = rskPublicKeys;
        return this;
    }

    public PendingFederationBuilder withMembersMstPublicKeys(List<ECKey> mstPublicKeys) {
        this.membersMstPublicKeys = mstPublicKeys;
        return this;
    }

    public PendingFederation build() {
        List<FederationMember> federationMembers = getFederationMembers();
        return new PendingFederation(federationMembers);
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
