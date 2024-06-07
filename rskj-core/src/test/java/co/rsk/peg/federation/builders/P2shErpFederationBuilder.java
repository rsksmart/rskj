package co.rsk.peg.federation.builders;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;
import org.ethereum.crypto.ECKey;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class P2shErpFederationBuilder {
    private List<BtcECKey> membersBtcPublicKeys;
    private List<ECKey> membersRskPublicKeys;
    private List<ECKey> membersMstPublicKeys;
    private List<BtcECKey> erpPublicKeys;
    private long erpActivationDelay;
    private Instant creationTime;
    private long creationBlockNumber;
    private NetworkParameters networkParameters;

    public P2shErpFederationBuilder() {
        this.membersBtcPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09"}, true
        );
        this.membersRskPublicKeys = new ArrayList<>();
        this.membersMstPublicKeys = new ArrayList<>();
        this.erpPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"erp01", "erp02", "erp03", "erp04"}, true
        );
        this.erpActivationDelay = 52_560;
        this.creationTime = Instant.ofEpochMilli(0);
        this.creationBlockNumber = 0;
        this.networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
    }

    public P2shErpFederationBuilder withMembersBtcPublicKeys(List<BtcECKey> btcPublicKeys) {
        this.membersBtcPublicKeys = btcPublicKeys;
        return this;
    }

    public P2shErpFederationBuilder withMembersRskPublicKeys(List<ECKey> rskPublicKeys) {
        this.membersRskPublicKeys = rskPublicKeys;
        return this;
    }

    public P2shErpFederationBuilder withMembersMstPublicKeys(List<ECKey> mstPublicKeys) {
        this.membersMstPublicKeys = mstPublicKeys;
        return this;
    }

    public P2shErpFederationBuilder withErpPublicKeys(List<BtcECKey> erpPublicKeys) {
        this.erpPublicKeys = erpPublicKeys;
        return this;
    }

    public P2shErpFederationBuilder withErpActivationDelay(long erpActivationDelay) {
        this.erpActivationDelay = erpActivationDelay;
        return this;
    }

    public P2shErpFederationBuilder withCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
        return this;
    }

    public P2shErpFederationBuilder withCreationBlockNumber(long creationBlockNumber) {
        this.creationBlockNumber = creationBlockNumber;
        return this;
    }

    public P2shErpFederationBuilder withNetworkParameters(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
        return this;
    }

    public ErpFederation build() {
        if (membersRskPublicKeys.isEmpty()) {
            getRskPublicKeysFromBtcPublicKeys();
        }
        if (membersMstPublicKeys.isEmpty()) {
            getMstPublicKeysFromRskPublicKeys();
        }

        List<FederationMember> federationMembers = getFederationMembers();
        FederationArgs federationArgs = new FederationArgs(federationMembers, creationTime, creationBlockNumber, networkParameters);

        return FederationFactory.buildP2shErpFederation(federationArgs, erpPublicKeys, erpActivationDelay);
    }

    private void getRskPublicKeysFromBtcPublicKeys() {
        for (BtcECKey btcPublicKey : membersBtcPublicKeys) {
            membersRskPublicKeys.add(ECKey.fromPublicOnly(btcPublicKey.getPubKey()));
        }
    }
    
    private void getMstPublicKeysFromRskPublicKeys() {
        membersMstPublicKeys.addAll(membersRskPublicKeys);
    }

    private List<FederationMember> getFederationMembers() {
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
}
