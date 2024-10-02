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

    /**
     * Retrieves the Bitcoin address of the proposed federation, if it exists.
     *
     * <p>
     * This method checks if there is a proposed federation available and
     * returns its associated Bitcoin address. The proposed federation is typically
     * a federation that is awaiting approval or activation.
     * </p>
     *
     * @return an {@link Optional} containing the Bitcoin {@link Address} of the proposed federation,
     *         or an empty {@link Optional} if no proposed federation is available.
     */
    Optional<Address> getProposedFederationAddress();

    /**
     * Retrieves the size of the proposed federation, if it exists.
     *
     * <p>
     * This method checks if a proposed federation is available and returns the number of members
     * in the proposed federation. If no proposed federation exists, it returns an empty {@link Optional}.
     * </p>
     *
     * @return an {@link Optional} containing the size of the proposed federation
     *         (i.e., the number of members), or an empty {@link Optional} if no
     *         proposed federation is available.
     */
    Optional<Integer> getProposedFederationSize();

    /**
     * Retrieves the creation time of the proposed federation, if available.
     *
     * <p>
     * This method checks if a proposed federation exists and, if present,
     * returns the time at which it was created. The proposed federation is
     * typically one that is awaiting approval or activation.
     * </p>
     *
     * @return an {@link Optional} containing the {@link Instant} of the
     *         proposed federation's creation, or an empty {@link Optional}
     *         if no proposed federation exists.
     */
    Optional<Instant> getProposedFederationCreationTime();

    /**
     * Retrieves the block number at which the proposed federation was created, if available.
     *
     * <p>
     * This method checks if there is a proposed federation and, if present,
     * returns the block number during which it was created. The proposed federation
     * is typically a federation awaiting approval or activation.
     * </p>
     *
     * @return an {@link Optional} containing the block number of the proposed
     *         federation's creation, or an empty {@link Optional} if no proposed
     *         federation exists.
     */
    Optional<Long> getProposedFederationCreationBlockNumber();
    Optional<byte[]> getProposedFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType);

    /**
     * Retrieves the public key of the specified type for a federator at the given index
     * from the proposed federation, if it exists.
     *
     * <p>
     * This method checks whether a proposed federation is available and retrieves the
     * public key for a federator at the specified index. The key type can be of various
     * types (e.g., BTC, RSK), depending on the key used by the federation member.
     * </p>
     *
     * @param index the zero-based index of the federator in the federation's member list.
     * @param keyType the type of public key to retrieve (e.g., {@link FederationMember.KeyType#BTC}).
     * @return an {@link Optional} containing the public key as a byte array if the proposed
     *         federation and federator at the specified index exist, or an empty {@link Optional}
     *         if no proposed federation or member at the given index is found.
     * @throws IndexOutOfBoundsException if the index is out of the federation member list bounds.
     */
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
