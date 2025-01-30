package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import org.ethereum.crypto.ECKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class P2shP2wshErpFederationBuilder {
    private List<BtcECKey> membersBtcPublicKeys;
    private List<ECKey> membersRskPublicKeys;
    private List<ECKey> membersMstPublicKeys;
    private List<BtcECKey> erpPublicKeys;
    private long erpActivationDelay;
    private Instant creationTime;
    private long creationBlockNumber;
    private NetworkParameters networkParameters;

    private P2shP2wshErpFederationBuilder() {
        this.membersBtcPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{
            "member01",
            "member02",
            "member03",
            "member04",
            "member05",
            "member06",
            "member07",
            "member08",
            "member09"
        }, true);
        this.membersRskPublicKeys = new ArrayList<>();
        this.membersMstPublicKeys = new ArrayList<>();
        this.erpPublicKeys = FederationMainNetConstants.getInstance().getErpFedPubKeysList();
        this.erpActivationDelay = 52_560L;
        this.creationTime = Instant.ofEpochSecond(100_000_000L);
        this.creationBlockNumber = 1L;
        this.networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
    }

    public static P2shP2wshErpFederationBuilder builder() {
        return new P2shP2wshErpFederationBuilder();
    }

    public P2shP2wshErpFederationBuilder withMembersBtcPublicKeys(List<BtcECKey> btcPublicKeys) {
        this.membersBtcPublicKeys = btcPublicKeys;
        return this;
    }

    public P2shP2wshErpFederationBuilder withMembersRskPublicKeys(List<ECKey> rskPublicKeys) {
        this.membersRskPublicKeys = rskPublicKeys;
        return this;
    }

    public P2shP2wshErpFederationBuilder withMembersMstPublicKeys(List<ECKey> mstPublicKeys) {
        this.membersMstPublicKeys = mstPublicKeys;
        return this;
    }

    public P2shP2wshErpFederationBuilder withErpPublicKeys(List<BtcECKey> erpPublicKeys) {
        this.erpPublicKeys = erpPublicKeys;
        return this;
    }

    public P2shP2wshErpFederationBuilder withErpActivationDelay(long erpActivationDelay) {
        this.erpActivationDelay = erpActivationDelay;
        return this;
    }

    public P2shP2wshErpFederationBuilder withCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
        return this;
    }

    public P2shP2wshErpFederationBuilder withCreationBlockNumber(long creationBlockNumber) {
        this.creationBlockNumber = creationBlockNumber;
        return this;
    }

    public P2shP2wshErpFederationBuilder withNetworkParameters(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
        return this;
    }

    public ErpFederation build() {
        List<FederationMember> federationMembers = getFederationMembers();
        FederationArgs federationArgs = new FederationArgs(federationMembers, creationTime, creationBlockNumber, networkParameters);

        return FederationFactory.buildP2shP2wshErpFederation(federationArgs, erpPublicKeys, erpActivationDelay);
    }

    private List<FederationMember> getFederationMembers() {
        if (membersRskPublicKeys.isEmpty()) {
            this.membersRskPublicKeys = membersBtcPublicKeys.stream()
                .map(BtcECKey::getPubKey)
                .map(ECKey::fromPublicOnly)
                .toList();
        }

        if (membersMstPublicKeys.isEmpty()) {
            this.membersMstPublicKeys = new ArrayList<>(membersRskPublicKeys);
        }

        return IntStream.range(0, membersBtcPublicKeys.size())
            .mapToObj(i -> new FederationMember(
                membersBtcPublicKeys.get(i),
                membersRskPublicKeys.get(i),
                membersMstPublicKeys.get(i)))
            .toList();
    }
}
