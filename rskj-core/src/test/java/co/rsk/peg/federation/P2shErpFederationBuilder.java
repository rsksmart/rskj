package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import org.ethereum.crypto.ECKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        this.erpPublicKeys = FederationMainNetConstants.getInstance().getErpFedPubKeysList();
        this.erpActivationDelay = 52_560;
        this.creationTime = Instant.ofEpochMilli(10L);
        this.creationBlockNumber = 1L;
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
        List<FederationMember> federationMembers = getFederationMembers();
        FederationArgs federationArgs = new FederationArgs(federationMembers, creationTime, creationBlockNumber, networkParameters);

        return FederationFactory.buildP2shErpFederation(federationArgs, erpPublicKeys, erpActivationDelay);
    }

    private List<FederationMember> getFederationMembers() {
        if (membersRskPublicKeys.isEmpty()) {
            membersRskPublicKeys = membersBtcPublicKeys.stream()
                .map(btcKey -> ECKey.fromPublicOnly(btcKey.getPubKey()))
                .collect(Collectors.toList());
        }
        if (membersMstPublicKeys.isEmpty()) {
            membersMstPublicKeys = new ArrayList<>(membersRskPublicKeys);
        }

        return IntStream.range(0, membersBtcPublicKeys.size())
            .mapToObj(i -> new FederationMember(
                membersBtcPublicKeys.get(i),
                membersRskPublicKeys.get(i),
                membersMstPublicKeys.get(i)
            ))
            .collect(Collectors.toList());
    }
}
