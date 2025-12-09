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

    /**
     * Returns the redeemScript of the current active federation
     *
     * @return Returns the redeemScript of the current active federation
     */
    Optional<Script> getActiveFederationRedeemScript();

    /**
     * Returns the active federation bitcoin address.
     * @return the active federation bitcoin address.
     */
    Address getActiveFederationAddress();

    /**
     * Returns the active federation's size
     * @return the active federation size
     */
    int getActiveFederationSize();

    /**
     * Returns the active federation's minimum required signatures
     * @return the active federation minimum required signatures
     */
    int getActiveFederationThreshold();

    /**
     * Returns the active federation's creation time
     * @return the active federation creation time
     */
    Instant getActiveFederationCreationTime();

    /**
     * Returns the active federation's creation block number
     * @return the active federation creation block number
     */
    long getActiveFederationCreationBlockNumber();

    /**
     * Returns the public key of the active federation's federator at the given index
     * @param index the federator's index (zero-based)
     * @return the federator's public key
     */
    byte[] getActiveFederatorBtcPublicKey(int index);

    /**
     * Returns the public key of given type of the active federation's federator at the given index
     * @param index the federator's index (zero-based)
     * @param keyType the key type
     * @return the federator's public key
     */
    byte[] getActiveFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType);

    List<UTXO> getActiveFederationBtcUTXOs();

    void clearRetiredFederation();

    /**
     * Returns the currently retiring federation.
     * See getRetiringFederationReference() for details.
     *
     * @return the retiring federation.
     */
    Optional<Federation> getRetiringFederation();

    /**
     * Returns the retiring federation bitcoin address.
     * @return the retiring federation bitcoin address, null if no retiring federation exists
     */
    Address getRetiringFederationAddress();

    /**
     * Returns the retiring federation's size
     * @return the retiring federation size, -1 if no retiring federation exists
     */
    int getRetiringFederationSize();

    /**
     * Returns the retiring federation's minimum required signatures
     * @return the retiring federation minimum required signatures, -1 if no retiring federation exists
     */
    int getRetiringFederationThreshold();

    /**
     * Returns the retiring federation's creation time
     * @return the retiring federation creation time, null if no retiring federation exists
     */
    Instant getRetiringFederationCreationTime();

    /**
     * Returns the retiring federation's creation block number
     * @return the retiring federation creation block number,
     * -1 if no retiring federation exists
     */
    long getRetiringFederationCreationBlockNumber();

    /**
     * Returns the public key of the retiring federation's federator at the given index
     * @param index the retiring federator's index (zero-based)
     * @return the retiring federator's public key, null if no retiring federation exists
     */
    byte[] getRetiringFederatorBtcPublicKey(int index);

    /**
     * Returns the public key of the given type of the retiring federation's federator at the given index
     * @param index the retiring federator's index (zero-based)
     * @param keyType the key type
     * @return the retiring federator's public key of the given type, null if no retiring federation exists
     */
    byte[] getRetiringFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType);

    /**
     * Returns the currently live federations
     * This would be the active federation plus potentially the retiring federation
     * @return a list of live federations
     */
    List<Federation> getLiveFederations();

    FederationContext getFederationContext();

    List<UTXO> getNewFederationBtcUTXOs();
    List<UTXO> getRetiringFederationBtcUTXOs();

    /**
     * Returns the currently pending federation hash, or null if none exists
     * @return the currently pending federation hash, or null if none exists
     */
    Keccak256 getPendingFederationHash();

    /**
     * Returns the currently pending federation size, or -1 if none exists
     * @return the currently pending federation size, or -1 if none exists
     */
    int getPendingFederationSize();

    /**
     * Returns the currently pending federation federator's public key at the given index, or null if none exists
     * @param index the federator's index (zero-based)
     * @return the pending federation's federator public key
     */
    byte[] getPendingFederatorBtcPublicKey(int index);

    /**
     * Returns the public key of the given type of the pending federation's federator at the given index
     * @param index the federator's index (zero-based)
     * @param keyType the key type
     * @return the pending federation's federator public key of given type
     */
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

    void clearProposedFederation();

    void commitProposedFederation();

    int voteFederationChange(
        Transaction tx,
        ABICallSpec callSpec,
        SignatureCache signatureCache,
        BridgeEventLogger eventLogger
    );
    long getActiveFederationCreationBlockHeight();

    void updateFederationCreationBlockHeights();

    void save();
}
