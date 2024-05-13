package co.rsk.peg.federation.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

import java.time.Instant;
import java.util.List;

public class FederationConstants {
    protected List<BtcECKey> genesisFederationPublicKeys;
    protected Instant genesisFederationCreationTime;

    protected AddressBasedAuthorizer federationChangeAuthorizer;
    protected String oldFederationAddress;
    protected long federationActivationAge;
    protected long federationActivationAgeLegacy;
    protected long fundsMigrationAgeSinceActivationBegin;
    protected long fundsMigrationAgeSinceActivationEnd;
    protected long specialCaseFundsMigrationAgeSinceActivationEnd;

    protected long erpFedActivationDelay;
    protected List<BtcECKey> erpFedPubKeysList;


    public List<BtcECKey> getGenesisFederationPublicKeys() {
        return genesisFederationPublicKeys;
    }

    public Instant getGenesisFederationCreationTime() {
        return genesisFederationCreationTime;
    }

    public long getFederationActivationAge(ActivationConfig.ForBlock activations) {
        return activations.isActive(ConsensusRule.RSKIP383) ? federationActivationAge  : federationActivationAgeLegacy;
    }

    public long getFundsMigrationAgeSinceActivationBegin() {
        return fundsMigrationAgeSinceActivationBegin;
    }
    public long getFundsMigrationAgeSinceActivationEnd(ActivationConfig.ForBlock activations) {
        if (activations.isActive(ConsensusRule.RSKIP357) && !activations.isActive(ConsensusRule.RSKIP374)){
            return specialCaseFundsMigrationAgeSinceActivationEnd;
        }

        return fundsMigrationAgeSinceActivationEnd;
    }

    public AddressBasedAuthorizer getFederationChangeAuthorizer() { return federationChangeAuthorizer; }

    public long getErpFedActivationDelay() {
        return erpFedActivationDelay;
    }

    public List<BtcECKey> getErpFedPubKeysList() {
        return erpFedPubKeysList;
    }

    public String getOldFederationAddress() {
        return oldFederationAddress;
    }
}
