package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface FederationStorageProvider {

    List<UTXO> getNewFederationBtcUTXOs(NetworkParameters networkParameters, ActivationConfig.ForBlock activations);
    List<UTXO> getOldFederationBtcUTXOs();

    Federation getNewFederation(FederationConstants federationConstants, ActivationConfig.ForBlock activations);
    void setNewFederation(Federation federation);

    Federation getOldFederation(FederationConstants federationConstants, ActivationConfig.ForBlock activations);
    void setOldFederation(Federation federation);

    PendingFederation getPendingFederation();
    void setPendingFederation(PendingFederation federation);

    ABICallElection getFederationElection(AddressBasedAuthorizer authorizer);

    Optional<Long> getActiveFederationCreationBlockHeight(ActivationConfig.ForBlock activations);
    void setActiveFederationCreationBlockHeight(long activeFederationCreationBlockHeight);

    Optional<Long> getNextFederationCreationBlockHeight(ActivationConfig.ForBlock activations);
    void setNextFederationCreationBlockHeight(long nextFederationCreationBlockHeight);
    void clearNextFederationCreationBlockHeight();

    Optional<Script> getLastRetiredFederationP2SHScript(ActivationConfig.ForBlock activations);
    void setLastRetiredFederationP2SHScript(Script lastRetiredFederationP2SHScript);

    void save(NetworkParameters networkParameters, ActivationConfig.ForBlock activations) throws IOException;
}
