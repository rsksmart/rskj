package co.rsk.peg.federation;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.core.RskAddress;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeIllegalArgumentException;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.vote.*;
import co.rsk.util.StringUtils;
import java.time.Instant;
import java.util.*;
import javax.annotation.Nullable;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FederationSupportImpl implements FederationSupport {

    private static final Logger logger = LoggerFactory.getLogger(FederationSupportImpl.class);

    private enum StorageFederationReference { NONE, NEW, OLD, GENESIS }

    private final FederationStorageProvider provider;
    private final FederationConstants constants;
    private final Block rskExecutionBlock;
    private final ActivationConfig.ForBlock activations;

    public FederationSupportImpl(
        FederationConstants constants,
        FederationStorageProvider provider,
        Block rskExecutionBlock,
        ActivationConfig.ForBlock activations) {

        this.constants = constants;
        this.provider = provider;
        this.rskExecutionBlock = rskExecutionBlock;
        this.activations = activations;
    }

    @Override
    public Federation getActiveFederation() {
        switch (getActiveFederationReference()) {
            case NEW:
                return provider.getNewFederation(constants, activations);
            case OLD:
                return provider.getOldFederation(constants, activations);
            case GENESIS:
            default:
                return getGenesisFederation();
        }
    }

    /**
     * Returns the currently active federation reference.
     * Logic is as follows:
     * When no "new" federation is recorded in the blockchain, then return GENESIS
     * When a "new" federation is present and no "old" federation is present, then return NEW
     * When both "new" and "old" federations are present, then
     * 1) If the "new" federation is at least bridgeConstants::getFederationActivationAge() blocks old,
     * return the NEW
     * 2) Otherwise, return OLD
     *
     * @return a reference to where the currently active federation is stored.
     */
    private StorageFederationReference getActiveFederationReference() {
        Federation newFederation = provider.getNewFederation(constants, activations);

        // No new federation in place, then the active federation
        // is the genesis federation
        if (newFederation == null) {
            return StorageFederationReference.GENESIS;
        }

        Federation oldFederation = provider.getOldFederation(constants, activations);

        // No old federation in place, then the active federation
        // is the new federation
        if (oldFederation == null) {
            return StorageFederationReference.NEW;
        }

        // Both new and old federations in place
        // If the minimum age has gone by for the new federation's
        // activation, then that federation is the currently active.
        // Otherwise, the old federation is still the currently active.
        if (shouldFederationBeActive(newFederation)) {
            return StorageFederationReference.NEW;
        }

        return StorageFederationReference.OLD;
    }

    private boolean shouldFederationBeActive(Federation federation) {
        long federationAge = rskExecutionBlock.getNumber() - federation.getCreationBlockNumber();
        return federationAge >= constants.getFederationActivationAge(activations);
    }

    private Federation getGenesisFederation() {
        long genesisFederationCreationBlockNumber = 1L;
        List<BtcECKey> genesisFederationPublicKeys = constants.getGenesisFederationPublicKeys();
        List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(genesisFederationPublicKeys);
        Instant genesisFederationCreationTime = constants.getGenesisFederationCreationTime();
        FederationArgs federationArgs = new FederationArgs(federationMembers, genesisFederationCreationTime, genesisFederationCreationBlockNumber, constants.getBtcParams());
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    @Override
    public Optional<Script> getActiveFederationRedeemScript() {
        return activations.isActive(RSKIP293) ?
            Optional.of(getActiveFederation().getRedeemScript()) :
            Optional.empty();
    }

    @Override
    public Address getActiveFederationAddress() {
        return getActiveFederation().getAddress();
    }

    @Override
    public int getActiveFederationSize() {
        return getActiveFederation().getSize();
    }

    @Override
    public int getActiveFederationThreshold() {
        return getActiveFederation().getNumberOfSignaturesRequired();
    }

    @Override
    public Instant getActiveFederationCreationTime() {
        return getActiveFederation().getCreationTime();
    }

    @Override
    public long getActiveFederationCreationBlockNumber() {
        return getActiveFederation().getCreationBlockNumber();
    }

    @Override
    public byte[] getActiveFederatorBtcPublicKey(int index) {
        List<BtcECKey> publicKeys = getActiveFederation().getBtcPublicKeys();

        if (index < 0 || index >= publicKeys.size()) {
            throw new IndexOutOfBoundsException(String.format("Federator index must be between 0 and %d", publicKeys.size() - 1));
        }

        return publicKeys.get(index).getPubKey();
    }

    @Override
    public byte[] getActiveFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        return getFederationMemberPublicKeyOfType(getActiveFederation().getMembers(), index, keyType, "Federator");
    }

    @Override
    public List<UTXO> getActiveFederationBtcUTXOs() {
        switch (getActiveFederationReference()) {
            case OLD:
                return provider.getOldFederationBtcUTXOs();
            case NEW:
            case GENESIS:
            default:
                return provider.getNewFederationBtcUTXOs(constants.getBtcParams(), activations);
        }
    }

    @Override
    public void clearRetiredFederation() {
        provider.setOldFederation(null);
    }

    @Override
    @Nullable
    public Federation getRetiringFederation() {
        switch (getRetiringFederationReference()) {
            case OLD:
                return provider.getOldFederation(constants, activations);
            case NONE:
            default:
                return null;
        }
    }

    /**
     * Returns the currently retiring federation reference.
     * Logic is as follows:
     * When no "new" or "old" federation is recorded in the blockchain, then return empty.
     * When both "new" and "old" federations are present, then
     * 1) If the "new" federation is at least bridgeConstants::getFederationActivationAge() blocks old,
     * return OLD
     * 2) Otherwise, return empty
     *
     * @return the retiring federation.
     */
    private StorageFederationReference getRetiringFederationReference() {
        Federation newFederation = provider.getNewFederation(constants, activations);
        Federation oldFederation = provider.getOldFederation(constants, activations);

        if (oldFederation == null || newFederation == null) {
            return StorageFederationReference.NONE;
        }

        // Both new and old federations in place
        // If the minimum age has gone by for the new federation's
        // activation, then the old federation is the currently retiring.
        // Otherwise, there is no retiring federation.
        if (shouldFederationBeActive(newFederation)) {
            return StorageFederationReference.OLD;
        }

        return StorageFederationReference.NONE;
    }

    @Override
    public Address getRetiringFederationAddress() {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return null;
        }

        return retiringFederation.getAddress();
    }

    @Override
    public int getRetiringFederationSize() {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return FederationChangeResponseCode.FEDERATION_NON_EXISTENT.getCode();
        }

        return retiringFederation.getSize();
    }

    @Override
    public int getRetiringFederationThreshold() {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return FederationChangeResponseCode.FEDERATION_NON_EXISTENT.getCode();
        }

        return retiringFederation.getNumberOfSignaturesRequired();
    }

    @Override
    public Instant getRetiringFederationCreationTime() {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return null;
        }

        return retiringFederation.getCreationTime();
    }

    @Override
    public long getRetiringFederationCreationBlockNumber() {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return FederationChangeResponseCode.FEDERATION_NON_EXISTENT.getCode();
        }
        return retiringFederation.getCreationBlockNumber();
    }

    @Override
    public byte[] getRetiringFederatorBtcPublicKey(int index) {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return null;
        }

        List<BtcECKey> publicKeys = retiringFederation.getBtcPublicKeys();

        if (index < 0 || index >= publicKeys.size()) {
            throw new IndexOutOfBoundsException(String.format("Retiring federator index must be between 0 and %d", publicKeys.size() - 1));
        }

        return publicKeys.get(index).getPubKey();
    }

    @Override
    public byte[] getRetiringFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return null;
        }

        return getFederationMemberPublicKeyOfType(retiringFederation.getMembers(), index, keyType, "Retiring federator");
    }

    @Override
    public List<UTXO> getRetiringFederationBtcUTXOs() {
        switch (getRetiringFederationReference()) {
            case OLD:
                return provider.getOldFederationBtcUTXOs();
            case NONE:
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public List<UTXO> getNewFederationBtcUTXOs() {
        return provider.getNewFederationBtcUTXOs(constants.getBtcParams(), activations);
    }

    @Nullable
    private PendingFederation getPendingFederation() {
        return provider.getPendingFederation();
    }

    @Override
    public Keccak256 getPendingFederationHash() {
        PendingFederation currentPendingFederation = getPendingFederation();

        if (currentPendingFederation == null) {
            return null;
        }

        return currentPendingFederation.getHash();
    }

    @Override
    public int getPendingFederationSize() {
        PendingFederation currentPendingFederation = getPendingFederation();

        if (currentPendingFederation == null) {
            return FederationChangeResponseCode.FEDERATION_NON_EXISTENT.getCode();
        }

        return currentPendingFederation.getSize();
    }

    @Override
    public byte[] getPendingFederatorBtcPublicKey(int index) {
        PendingFederation currentPendingFederation = getPendingFederation();

        if (currentPendingFederation == null) {
            return null;
        }

        List<BtcECKey> publicKeys = currentPendingFederation.getBtcPublicKeys();

        return publicKeys.get(index).getPubKey();
    }

    @Override
    public byte[] getPendingFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            return null;
        }

        return getFederationMemberPublicKeyOfType(currentPendingFederation.getMembers(), index, keyType, "Federator");
    }

    @Override
    public Optional<Federation> getProposedFederation() {
        return provider.getProposedFederation(constants, activations);
    }
    
    @Override
    public Optional<byte[]> getProposedFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        return getProposedFederation()
            .map(Federation::getMembers)
            .map(members -> getFederationMemberPublicKeyOfType(members, index, keyType, "Proposed Federator"));
    }

    @Override
    public Optional<Address> getProposedFederationAddress() {
        return getProposedFederation()
            .map(Federation::getAddress);
    }

    @Override
    public Optional<Long> getProposedFederationCreationBlockNumber() {
        return getProposedFederation()
            .map(Federation::getCreationBlockNumber);
    }

    @Override
    public Optional<Integer> getProposedFederationSize() {
        return getProposedFederation()
            .map(Federation::getSize);
    }

    @Override
    public Optional<Instant> getProposedFederationCreationTime() {
        return getProposedFederation()
            .map(Federation::getCreationTime);
    }

    @Override
    public void clearProposedFederation() {
        provider.setProposedFederation(null);
    }

    @Override
    public int voteFederationChange(Transaction tx, ABICallSpec callSpec, SignatureCache signatureCache, BridgeEventLogger eventLogger) {
        String calledFunction = callSpec.getFunction();
        // Must be on one of the allowed functions
        if (unknownFederationChangeFunction(calledFunction)) {
            logger.warn("[voteFederationChange] Federation change function \"{}\" does not exist.", StringUtils.trim(calledFunction));
            return FederationChangeResponseCode.NON_EXISTING_FUNCTION_CALLED.getCode();
        }

        AddressBasedAuthorizer authorizer = constants.getFederationChangeAuthorizer();

        // Must be authorized to vote (checking for signature)
        if (!authorizer.isAuthorized(tx, signatureCache)) {
            RskAddress voter = tx.getSender(signatureCache);
            logger.warn("[voteFederationChange] Unauthorized voter {}.", voter);
            return FederationChangeResponseCode.UNAUTHORIZED_CALLER.getCode();
        }

        // Try to do a dry-run and only register the vote if the
        // call would be successful
        ABICallVoteResult result;
        try {
            result = executeVoteFederationChangeFunction(true, callSpec, eventLogger);
        } catch (BridgeIllegalArgumentException e) {
            logger.warn("[voteFederationChange] Unexpected federation change vote exception: {}", e.getMessage());
            result = new ABICallVoteResult(false, FederationChangeResponseCode.GENERIC_ERROR.getCode());
        }

        // Return if the dry run failed, or we are on a reversible execution
        if (!result.wasSuccessful()) {
            logger.warn("[voteFederationChange] Unsuccessful execution, voting result was {}.", result);
            return (int) result.getResult();
        }

        ABICallElection election = provider.getFederationElection(authorizer);
        // Register the vote. It is expected to succeed, since all previous checks succeeded
        if (!election.vote(callSpec, tx.getSender(signatureCache))) {
            logger.warn("[voteFederationChange] Unexpected federation change vote failure.");
            return FederationChangeResponseCode.GENERIC_ERROR.getCode();
        }

        // If enough votes have been reached, then actually execute the function
        Optional<ABICallSpec> winnerSpecOptional = election.getWinner();
        if (winnerSpecOptional.isPresent()) {
            ABICallSpec winnerSpec = winnerSpecOptional.get();
            try {
                result = executeVoteFederationChangeFunction(false, winnerSpec, eventLogger);
            } catch (BridgeIllegalArgumentException e) {
                logger.warn("[voteFederationChange] Unexpected federation change vote exception: {}", e.getMessage());
                return FederationChangeResponseCode.GENERIC_ERROR.getCode();
            } finally {
                // Clear the winner so that we don't repeat ourselves
                election.clearWinners();
            }
        }

        return (int) result.getResult();
    }

    private boolean unknownFederationChangeFunction(String calledFunction) {
        return Arrays.stream(FederationChangeFunction.values()).noneMatch(fedChangeFunction -> fedChangeFunction.getKey().equals(calledFunction));
    }

    private ABICallVoteResult executeVoteFederationChangeFunction(boolean dryRun, ABICallSpec callSpec, BridgeEventLogger eventLogger) throws BridgeIllegalArgumentException {
        // Try to do a dry-run and only register the vote if the
        // call would be successful
        ABICallVoteResult result;
        Integer executionResult;
        switch (callSpec.getFunction()) {
            case "create":
                executionResult = createPendingFederation(dryRun);
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            case "add":
                if(activations.isActive(RSKIP123)) {
                    throw new IllegalStateException("The \"add\" function is disabled.");
                }
                byte[] publicKeyBytes = callSpec.getArguments()[0];
                BtcECKey publicKey;
                ECKey publicKeyEc;
                try {
                    publicKey = BtcECKey.fromPublicOnly(publicKeyBytes);
                    publicKeyEc = ECKey.fromPublicOnly(publicKeyBytes);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("Public key could not be parsed " + ByteUtil.toHexString(publicKeyBytes), e);
                }
                executionResult = addFederatorPublicKeyMultikey(dryRun, publicKey, publicKeyEc, publicKeyEc);
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            case "add-multi":
                BtcECKey btcPublicKey;
                ECKey rskPublicKey;
                ECKey mstPublicKey;
                try {
                    btcPublicKey = BtcECKey.fromPublicOnly(callSpec.getArguments()[0]);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("BTC public key could not be parsed " + Bytes.of(callSpec.getArguments()[0]), e);
                }

                try {
                    rskPublicKey = ECKey.fromPublicOnly(callSpec.getArguments()[1]);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("RSK public key could not be parsed " + Bytes.of(callSpec.getArguments()[1]), e);
                }

                try {
                    mstPublicKey = ECKey.fromPublicOnly(callSpec.getArguments()[2]);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("MST public key could not be parsed " + Bytes.of(callSpec.getArguments()[2]), e);
                }
                executionResult = addFederatorPublicKeyMultikey(dryRun, btcPublicKey, rskPublicKey, mstPublicKey);
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            case "commit":
                Keccak256 pendingFederationHash = new Keccak256(callSpec.getArguments()[0]);
                executionResult = commitFederation(dryRun, pendingFederationHash, eventLogger).getCode();
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            case "rollback":
                executionResult = rollbackFederation(dryRun);
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            default:
                // Fail by default
                logger.warn("[executeVoteFederationChangeFunction] Unrecognized called function.");
                result = new ABICallVoteResult(false, FederationChangeResponseCode.GENERIC_ERROR.getCode());
        }

        return result;
    }

    /**
     * Creates a new pending federation
     * If there's currently no pending federation and no funds remain
     * to be moved from a previous federation, a new one is created.
     * Otherwise, -1 is returned if there's already a pending federation,
     * -2 is returned if there is a federation awaiting to be active,
     * or -3 if funds are left from a previous one.
     * @param dryRun whether to just do a dry run
     * @return 1 upon success, -1 when a pending federation is present,
     * -2 when a federation is to be activated,
     * and if -3 funds are still to be moved between federations.
     */
    private Integer createPendingFederation(boolean dryRun) {
        if (pendingFederationExists()) {
            logger.warn("[createPendingFederation] A pending federation already exists.");
            return FederationChangeResponseCode.PENDING_FEDERATION_ALREADY_EXISTS.getCode();
        }

        if (proposedFederationExists()) {
            logger.warn("[createPendingFederation] A proposed federation already exists.");
            return FederationChangeResponseCode.PROPOSED_FEDERATION_ALREADY_EXISTS.getCode();
        }

        if (amAwaitingFederationActivation()) {
            logger.warn("[createPendingFederation] There is an existing federation awaiting for activation.");
            return FederationChangeResponseCode.EXISTING_FEDERATION_AWAITING_ACTIVATION.getCode();
        }

        if (getRetiringFederation() != null) {
            logger.warn("[createPendingFederation] There is an existing retiring federation.");
            return FederationChangeResponseCode.RETIRING_FEDERATION_ALREADY_EXISTS.getCode();
        }

        if (dryRun) {
            logger.info("[createPendingFederation] DryRun execution successful.");
            return FederationChangeResponseCode.SUCCESSFUL.getCode();
        }

        PendingFederation pendingFederation = new PendingFederation(Collections.emptyList());

        provider.setPendingFederation(pendingFederation);

        // Clear votes on election
        provider.getFederationElection(constants.getFederationChangeAuthorizer()).clear();

        logger.info("[createPendingFederation] Pending federation created successfully.");
        return FederationChangeResponseCode.SUCCESSFUL.getCode();
    }

    private boolean pendingFederationExists() {
        PendingFederation currentPendingFederation = provider.getPendingFederation();
        return currentPendingFederation != null;
    }

    private boolean proposedFederationExists() {
        Optional<Federation> currentProposedFederation = provider.getProposedFederation(constants, activations);
        return currentProposedFederation.isPresent();
    }

    private boolean amAwaitingFederationActivation() {
        Federation newFederation = provider.getNewFederation(constants, activations);
        Federation oldFederation = provider.getOldFederation(constants, activations);

        return newFederation != null && oldFederation != null && !shouldFederationBeActive(newFederation);
    }

    /**
     * Adds the given keys to the current pending federation.
     *
     * @param dryRun whether to just do a dry run
     * @param btcKey the BTC public key to add
     * @param rskKey the RSK public key to add
     * @param mstKey the MST public key to add
     * @return 1 upon success, -1 if there was no pending federation, -2 if the key was already in the pending federation
     */
    private Integer addFederatorPublicKeyMultikey(boolean dryRun, BtcECKey btcKey, ECKey rskKey, ECKey mstKey) {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            logger.warn("[addFederatorPublicKeyMultikey] Pending federation does not exist.");
            return FederationChangeResponseCode.FEDERATION_NON_EXISTENT.getCode();
        }

        if (currentPendingFederation.getBtcPublicKeys().contains(btcKey) ||
            currentPendingFederation.getMembers().stream().map(FederationMember::getRskPublicKey).anyMatch(k -> k.equals(rskKey)) ||
            currentPendingFederation.getMembers().stream().map(FederationMember::getMstPublicKey).anyMatch(k -> k.equals(mstKey))) {
            logger.warn("[addFederatorPublicKeyMultikey] Federator is already part of pending federation.");
            return FederationChangeResponseCode.FEDERATOR_ALREADY_PRESENT.getCode();
        }

        if (dryRun) {
            logger.info("[addFederatorPublicKeyMultikey] DryRun execution successful.");
            return FederationChangeResponseCode.SUCCESSFUL.getCode();
        }

        FederationMember member = new FederationMember(btcKey, rskKey, mstKey);
        currentPendingFederation = currentPendingFederation.addMember(member);

        provider.setPendingFederation(currentPendingFederation);

        logger.info("[addFederatorPublicKeyMultikey] Federator public key added successfully.");
        return FederationChangeResponseCode.SUCCESSFUL.getCode();
    }

    /**
     * Commits the currently pending federation
     * after checking conditions are met to do so.
     * @param dryRun whether to just do a dry run
     * @param pendingFederationHash the pending federation's hash. This is checked to match the execution block's pending federation hash.
     * @return PENDING_FEDERATION_NON_EXISTENT if there was no pending federation,
     * INSUFFICIENT_MEMBERS if the pending federation was incomplete,
     * PENDING_FEDERATION_MISMATCHED_HASH if the given hash doesn't match the current pending federation's hash.
     * SUCCESSFUL upon success.
     */
    private FederationChangeResponseCode commitFederation(boolean dryRun, Keccak256 pendingFederationHash, BridgeEventLogger eventLogger) {
        // first check that we can commit the pending federation
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            logger.warn("[commitFederation] Pending federation does not exist.");
            return FederationChangeResponseCode.FEDERATION_NON_EXISTENT;
        }

        if (!currentPendingFederation.isComplete()) {
            logger.warn("[commitFederation] Pending federation has {} members, so it does not meet the minimum required.", currentPendingFederation.getMembers().size());
            return FederationChangeResponseCode.INSUFFICIENT_MEMBERS;
        }

        if (!pendingFederationHash.equals(currentPendingFederation.getHash())) {
            logger.warn("[commitFederation] Provided hash {} does not match pending federation hash {}.", pendingFederationHash, currentPendingFederation.getHash());
            return FederationChangeResponseCode.PENDING_FEDERATION_MISMATCHED_HASH;
        }

        if (dryRun) {
            logger.info("[commitFederation] DryRun execution successful.");
            return FederationChangeResponseCode.SUCCESSFUL;
        }

        // proceed with the commitment
        return commitPendingFederationAccordingToActivations(currentPendingFederation, eventLogger);
    }

    private FederationChangeResponseCode commitPendingFederationAccordingToActivations(PendingFederation currentPendingFederation, BridgeEventLogger eventLogger) {
        if (!activations.isActive(ConsensusRule.RSKIP419)) {
            return legacyCommitPendingFederation(currentPendingFederation, eventLogger);
        }
        return commitPendingFederation(currentPendingFederation, eventLogger);
    }

    /**
     * UTXOs are moved from active to retiring federation so that the transfer of funds can begin.
     * Then, the retiring federation (old federation) is set to be the currently active federation,
     * the active federation (new federation) is replaced with a new federation generated from the pending federation,
     * and the pending federation is wiped out.
     * The federation change info is preserved, and the commitment with the voted federation is logged.
     */
    private FederationChangeResponseCode legacyCommitPendingFederation(PendingFederation currentPendingFederation, BridgeEventLogger eventLogger) {
        moveUTXOsFromNewToOldFederation();

        // set old and new federations
        Federation activeFederation = getActiveFederation();
        provider.setOldFederation(activeFederation);
        Federation newFederation = buildFederationFromPendingFederation(currentPendingFederation);
        provider.setNewFederation(newFederation);

        clearPendingFederationVoting();

        if (activations.isActive(RSKIP186)) {
            setNewActiveFederationCreationBlockHeight();
            preserveLastRetiredFederationScript();
        }

        Federation currentOldFederation = provider.getOldFederation(constants, activations);
        Federation currentNewFederation = provider.getNewFederation(constants, activations);
        logCommitmentWithVotedFederation(eventLogger, currentOldFederation, currentNewFederation);

        return FederationChangeResponseCode.SUCCESSFUL;
    }

    private void moveUTXOsFromNewToOldFederation() {
        List<UTXO> utxosToMove = new ArrayList<>(provider.getNewFederationBtcUTXOs(constants.getBtcParams(), activations));

        // Clear new and old federation's UTXOs
        provider.getNewFederationBtcUTXOs(constants.getBtcParams(), activations).clear();
        List<UTXO> oldFederationUTXOs = provider.getOldFederationBtcUTXOs();
        oldFederationUTXOs.clear();

        // Move UTXOs from the new federation into the old federation
        oldFederationUTXOs.addAll(utxosToMove);
    }

    /**
     * The proposed federation is set to be a federation generated from the currently pending federation,
     * and the pending federation is wiped out.
     * The federation change info is preserved, and the commitment with the voted federation is logged.
     */
    private FederationChangeResponseCode commitPendingFederation(PendingFederation currentPendingFederation, BridgeEventLogger eventLogger) {
        // set proposed federation
        Federation proposedFederation = buildFederationFromPendingFederation(currentPendingFederation);
        provider.setProposedFederation(proposedFederation);
        setNewActiveFederationCreationBlockHeight();

        clearPendingFederationVoting();

        logCommitmentWithVotedFederation(eventLogger, getActiveFederation(), proposedFederation);

        return FederationChangeResponseCode.SUCCESSFUL;
    }

    private Federation buildFederationFromPendingFederation(PendingFederation pendingFederation) {
        Instant federationCreationTime = Instant.ofEpochMilli(rskExecutionBlock.getTimestamp());
        long federationCreationBlockNumber = rskExecutionBlock.getNumber();

        return pendingFederation.buildFederation(federationCreationTime, federationCreationBlockNumber, constants, activations);
    }

    private void clearPendingFederationVoting() {
        // Clear pending federation and votes on election
        provider.setPendingFederation(null);
        provider.getFederationElection(constants.getFederationChangeAuthorizer()).clear();
    }

    private void preserveLastRetiredFederationScript() {
        Federation activeFederation = getActiveFederation();
        Script activeFederationMembersP2SHScript = getFederationMembersP2SHScript(activeFederation);
        provider.setLastRetiredFederationP2SHScript(activeFederationMembersP2SHScript);
    }

    private void setNewActiveFederationCreationBlockHeight() {
        // since we are creating the to-be-active-fed in this block,
        // its creation block height is this block number
        long newActiveFederationCreationBlockHeight = rskExecutionBlock.getNumber();
        provider.setNextFederationCreationBlockHeight(newActiveFederationCreationBlockHeight);
    }

    private Script getFederationMembersP2SHScript(Federation federation) {
        // when the federation is a standard multisig,
        // the members p2sh script is the p2sh script
        if (!activations.isActive(RSKIP377)) {
            return federation.getP2SHScript();
        }
        if (!(federation instanceof ErpFederation)) {
            return federation.getP2SHScript();
        }

        // when the federation also has erp keys,
        // the members p2sh script is the default p2sh script
        return ((ErpFederation) federation).getDefaultP2SHScript();
    }

    private void logCommitmentWithVotedFederation(BridgeEventLogger eventLogger, Federation federationToBeRetired, Federation votedFederation) {
        eventLogger.logCommitFederation(rskExecutionBlock, federationToBeRetired, votedFederation);
        logger.debug("[logCommitmentWithVotedFederation] Voted federation committed: {}", votedFederation.getAddress());
    }

    /**
     * Rolls back the currently pending federation
     * That is, the pending federation is wiped out.
     * @param dryRun whether to just do a dry run
     * @return 1 upon success, 1 if there was no pending federation
     */
    private Integer rollbackFederation(boolean dryRun) {

        if (!pendingFederationExists()) {
            logger.warn("[rollbackFederation] Pending federation does not exist.");
            return FederationChangeResponseCode.FEDERATION_NON_EXISTENT.getCode();
        }

        if (dryRun) {
            logger.info("[rollbackFederation] DryRun execution successful.");
            return FederationChangeResponseCode.SUCCESSFUL.getCode();
        }

        provider.setPendingFederation(null);

        // Clear votes on election
        provider.getFederationElection(constants.getFederationChangeAuthorizer()).clear();

        logger.info("[rollbackFederation] Successfully rolled back pending federation.");
        return FederationChangeResponseCode.SUCCESSFUL.getCode();
    }

    @Override
    public long getActiveFederationCreationBlockHeight() {
        if (!activations.isActive(RSKIP186)) {
            return 0L;
        }

        Optional<Long> nextFederationCreationBlockHeightOpt = provider.getNextFederationCreationBlockHeight(activations);
        if (nextFederationCreationBlockHeightOpt.isPresent()) {
            long nextFederationCreationBlockHeight = nextFederationCreationBlockHeightOpt.get();
            long curBlockHeight = rskExecutionBlock.getNumber();
            if (curBlockHeight >= nextFederationCreationBlockHeight + constants.getFederationActivationAge(activations)) {
                return nextFederationCreationBlockHeight;
            }
        }

        Optional<Long> activeFederationCreationBlockHeightOpt = provider.getActiveFederationCreationBlockHeight(activations);
        return activeFederationCreationBlockHeightOpt.orElse(0L);
    }

    /**
     * Returns the compressed public key of given type of the member list at the
     * given index. Throws a custom index out of bounds exception when appropiate.
     * 
     * @param members     list of federation members
     * @param index       federator's index (zero-based)
     * @param keyType     key type
     * @param errorPrefix index out of bounds error prefix
     * @return federation member's public key
     */
    private byte[] getFederationMemberPublicKeyOfType(
          List<FederationMember> members, int index, FederationMember.KeyType keyType, String errorPrefix) {
        if (index < 0 || index >= members.size()) {
            throw new IndexOutOfBoundsException(
                String.format("%s index must be between 0 and %d (found: %d)", errorPrefix, members.size() - 1, index));
        }

        return members.get(index).getPublicKey(keyType).getPubKey(true);
    }

    @Override
    public Optional<Script> getLastRetiredFederationP2SHScript() {
        return provider.getLastRetiredFederationP2SHScript(activations);
    }

    @Override
    public void updateFederationCreationBlockHeights() {
        if (!activations.isActive(RSKIP186)) {
            return;
        }

        Optional<Long> nextFederationCreationBlockHeightOpt = provider.getNextFederationCreationBlockHeight(activations);
        if (!nextFederationCreationBlockHeightOpt.isPresent()) {
            return;
        }

        long nextFederationCreationBlockHeight = nextFederationCreationBlockHeightOpt.get();
        long currentBlockHeight = rskExecutionBlock.getNumber();

        if (newFederationShouldNotBeActiveYet(currentBlockHeight, nextFederationCreationBlockHeight)) {
            return;
        }

        provider.setActiveFederationCreationBlockHeight(nextFederationCreationBlockHeight);
        provider.clearNextFederationCreationBlockHeight();
    }

    private boolean newFederationShouldNotBeActiveYet(long currentBlockHeight, long nextFederationCreationBlockHeight) {
        return currentBlockHeight < nextFederationCreationBlockHeight + constants.getFederationActivationAge(activations);
    }

    @Override
    public void save() {
        provider.save(constants.getBtcParams(), activations);
    }
}
