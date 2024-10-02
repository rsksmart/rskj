package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.vote.ABICallSpec;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;

public interface FederationSupport {

    Federation getActiveFederation();
    Optional<Script> getActiveFederationRedeemScript();
    Address getActiveFederationAddress();
    int getActiveFederationSize();
    int getActiveFederationThreshold();
    Instant getActiveFederationCreationTime();
    long getActiveFederationCreationBlockNumber();
    byte[] getActiveFederatorBtcPublicKey(int index);
    byte[] getActiveFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType);
    List<UTXO> getActiveFederationBtcUTXOs();

    void clearRetiredFederation();

    @Nullable
    Federation getRetiringFederation();
    Address getRetiringFederationAddress();
    int getRetiringFederationSize();
    int getRetiringFederationThreshold();
    Instant getRetiringFederationCreationTime();
    long getRetiringFederationCreationBlockNumber();
    byte[] getRetiringFederatorBtcPublicKey(int index);
    byte[] getRetiringFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType);
    List<UTXO> getRetiringFederationBtcUTXOs();

    List<UTXO> getNewFederationBtcUTXOs();

    Keccak256 getPendingFederationHash();
    int getPendingFederationSize();
    byte[] getPendingFederatorBtcPublicKey(int index);
    byte[] getPendingFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType);

    Optional<Federation> getProposedFederation();
    Optional<Address> getProposedFederationAddress();
    Optional<Integer> getProposedFederationSize();
    Optional<Instant> getProposedFederationCreationTime();
    Optional<Long> getProposedFederationCreationBlockNumber();
    Optional<byte[]> getProposedFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType);

    int voteFederationChange(
        Transaction tx,
        ABICallSpec callSpec,
        SignatureCache signatureCache,
        BridgeEventLogger eventLogger
    );
    long getActiveFederationCreationBlockHeight();
    Optional<Script> getLastRetiredFederationP2SHScript();

    void updateFederationCreationBlockHeights();

    void save();
}
