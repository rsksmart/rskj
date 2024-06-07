package co.rsk.test.builders;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.constants.FederationConstants;

import java.time.Instant;
import java.util.List;

public class P2shErpFederationBuilder {
    private List<BtcECKey> membersPublicKeys;
    private List<BtcECKey> erpPublicKeys;
    private long erpActivationDelay;
    private Instant creationTime;
    private long creationBlockNumber;
    private NetworkParameters networkParameters;

    public P2shErpFederationBuilder() {
        this.membersPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09"}, true
        );
        this.erpPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"erp01", "erp02", "erp03", "erp04"}, true
        );
        this.erpActivationDelay = 52_560;
        this.creationTime = Instant.ofEpochMilli(0);
        this.creationBlockNumber = 0;
        this.networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
    }

    public P2shErpFederationBuilder withFederationConstants(FederationConstants federationConstants) {
        this.erpPublicKeys = federationConstants.getErpFedPubKeysList();
        this.erpActivationDelay = federationConstants.getErpFedActivationDelay();
        this.networkParameters = federationConstants.getBtcParams();
        return this;
    }

    public P2shErpFederationBuilder withMembersPublicKeys(List<BtcECKey> membersPublicKeys) {
        this.membersPublicKeys = membersPublicKeys;
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
        List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(membersPublicKeys);
        FederationArgs federationArgs = new FederationArgs(federationMembers, creationTime, creationBlockNumber, networkParameters);

        return FederationFactory.buildP2shErpFederation(federationArgs, erpPublicKeys, erpActivationDelay);
    }
}
