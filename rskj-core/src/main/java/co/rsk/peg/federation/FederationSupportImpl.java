package co.rsk.peg.federation;

import static co.rsk.peg.federation.FederationChangeResponseCode.*;
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
        return switch (getActiveFederationReference()) {
            case NEW -> provider.getNewFederation(constants, activations);
            case OLD -> provider.getOldFederation(constants, activations);
            default -> getGenesisFederation();
        };
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
        long federationAge = getFederationAge(federation);
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
        if (getActiveFederationReference() == StorageFederationReference.OLD) {
            return provider.getOldFederationBtcUTXOs();
        }

        return provider.getNewFederationBtcUTXOs(constants.getBtcParams(), activations);
    }

    @Override
    public void clearRetiredFederation() {
        provider.setOldFederation(null);
    }

    @Override
    public Optional<Federation> getRetiringFederation() {
        StorageFederationReference retiringFederationReference = getRetiringFederationReference();

        if (retiringFederationReference != StorageFederationReference.OLD) {
            return Optional.empty();
        }

        Federation oldFederation = provider.getOldFederation(constants, activations);
        return Optional.ofNullable(oldFederation);
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
    public Optional<Address> getRetiringFederationAddress() {
        Optional<Federation> retiringFederation = getRetiringFederation();
        return retiringFederation.map(Federation::getAddress);
    }

    @Override
    public Optional<Integer> getRetiringFederationSize() {
        Optional<Federation> retiringFederation = getRetiringFederation();
        return retiringFederation.map(Federation::getSize);
    }

    @Override
    public Optional<Integer> getRetiringFederationThreshold() {
        Optional<Federation> retiringFederation = getRetiringFederation();
        return retiringFederation.map(Federation::getNumberOfSignaturesRequired);
    }

    @Override
    public Optional<Instant> getRetiringFederationCreationTime() {
        Optional<Federation> retiringFederation = getRetiringFederation();
        return retiringFederation.map(Federation::getCreationTime);
    }

    @Override
    public Optional<Long> getRetiringFederationCreationBlockNumber() {
        Optional<Federation> retiringFederation = getRetiringFederation();
        return retiringFederation.map(Federation::getCreationBlockNumber);
    }

    @Override
    public Optional<BtcECKey> getRetiringFederatorBtcPublicKey(int index) {
        Optional<Federation> retiringFederation = getRetiringFederation();
        if (retiringFederation.isEmpty()) {
            return Optional.empty();
        }

        List<BtcECKey> publicKeys = retiringFederation.get().getBtcPublicKeys();

        if (index < 0 || index >= publicKeys.size()) {
            throw new IndexOutOfBoundsException(String.format("Retiring federator index must be between 0 and %d", publicKeys.size() - 1));
        }

        BtcECKey retiringFederatorBtcPublicKey = publicKeys.get(index);
        return Optional.of(retiringFederatorBtcPublicKey);
    }

    @Override
    public byte[] getRetiringFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        Optional<Federation> retiringFederation = getRetiringFederation();
        return retiringFederation
            .map(retiringFed -> getFederationMemberPublicKeyOfType(retiringFed.getMembers(), index, keyType, "Retiring federator"))
            .orElse(null);
    }

    @Override
    public List<Federation> getLiveFederations() {
        return getFederationContext().getLiveFederations();
    }

    @Override
    public FederationContext getFederationContext() {
        FederationContext.FederationContextBuilder federationContextBuilder = FederationContext.builder();
        federationContextBuilder.withActiveFederation(getActiveFederation());

        getRetiringFederation()
            .ifPresent(federationContextBuilder::withRetiringFederation);

        provider.getLastRetiredFederationP2SHScript(activations)
            .ifPresent(federationContextBuilder::withLastRetiredFederationP2SHScript);

        return federationContextBuilder.build();
    }

    @Override
    public List<UTXO> getNewFederationBtcUTXOs() {
        return provider.getNewFederationBtcUTXOs(constants.getBtcParams(), activations);
    }

    @Override
    public List<UTXO> getRetiringFederationBtcUTXOs() {
        if (getRetiringFederationReference() == StorageFederationReference.OLD) {
            return provider.getOldFederationBtcUTXOs();
        }

        return Collections.emptyList();
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
            return FEDERATION_NON_EXISTENT.getCode();
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
        // Must be one of the allowed functions
        Optional<FederationChangeFunction> federationChangeFunction = Arrays.stream(FederationChangeFunction.values())
                .filter(function -> function.getKey().equals(calledFunction))
                .findAny();
        if (federationChangeFunction.isEmpty()) {
            logger.warn("[voteFederationChange] Federation change function \"{}\" does not exist.", StringUtils.trim(calledFunction));
            return NON_EXISTING_FUNCTION_CALLED.getCode();
        }

        AddressBasedAuthorizer authorizer = constants.getFederationChangeAuthorizer();

        // Must be authorized to vote (checking for signature)
        if (!authorizer.isAuthorized(tx, signatureCache)) {
            RskAddress voter = tx.getSender(signatureCache);
            logger.warn("[voteFederationChange] Unauthorized voter {}.", voter);
            return UNAUTHORIZED_CALLER.getCode();
        }

        // Try to do a dry-run and only register the vote if the
        // call would be successful
        ABICallVoteResult result;
        try {
            result = executeVoteFederationChangeFunction(true, callSpec, federationChangeFunction.get(), eventLogger);
        } catch (BridgeIllegalArgumentException e) {
            logger.warn("[voteFederationChange] Unexpected federation change vote exception: {}", e.getMessage());
            result = new ABICallVoteResult(false, GENERIC_ERROR.getCode());
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
            return GENERIC_ERROR.getCode();
        }

        // If enough votes have been reached, then actually execute the function
        Optional<ABICallSpec> winnerSpecOptional = election.getWinner();
        if (winnerSpecOptional.isPresent()) {
            ABICallSpec winnerSpec = winnerSpecOptional.get();
            try {
                result = executeVoteFederationChangeFunction(false, winnerSpec, federationChangeFunction.get(), eventLogger);
            } catch (BridgeIllegalArgumentException e) {
                logger.warn("[voteFederationChange] Unexpected federation change vote exception: {}", e.getMessage());
                return GENERIC_ERROR.getCode();
            } finally {
                // Clear the winner so that we don't repeat ourselves
                election.clearWinners();
            }
        }

        return (int) result.getResult();
    }

    private ABICallVoteResult executeVoteFederationChangeFunction(boolean dryRun, ABICallSpec callSpec, FederationChangeFunction federationChangeFunction, BridgeEventLogger eventLogger) throws BridgeIllegalArgumentException {
        int executionResult = 0;
        byte[][] callSpecArguments = callSpec.getArguments();

        switch (federationChangeFunction) {
            case CREATE -> executionResult = createPendingFederation(dryRun);
            case ADD -> {
                if (activations.isActive(RSKIP123)) {
                    throw new IllegalStateException("The \"add\" function is disabled.");
                }
                byte[] btcPublicKeyBytes = callSpecArguments[0];
                BtcECKey publicKey;
                ECKey publicKeyEc;
                try {
                    publicKey = BtcECKey.fromPublicOnly(btcPublicKeyBytes);
                    publicKeyEc = ECKey.fromPublicOnly(btcPublicKeyBytes);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("Public key could not be parsed " + ByteUtil.toHexString(btcPublicKeyBytes), e);
                }
                executionResult = addFederatorPublicKeyMultikey(dryRun, publicKey, publicKeyEc, publicKeyEc);
            }
            case ADD_MULTI -> {
                byte[] btcPublicKeyBytes = callSpecArguments[0];
                BtcECKey btcPublicKey;
                ECKey rskPublicKey;
                ECKey mstPublicKey;
                try {
                    btcPublicKey = BtcECKey.fromPublicOnly(btcPublicKeyBytes);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("BTC public key could not be parsed " + Bytes.of(btcPublicKeyBytes), e);
                }

                byte[] rskPublicKeyBytes = callSpecArguments[1];
                try {
                    rskPublicKey = ECKey.fromPublicOnly(rskPublicKeyBytes);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("RSK public key could not be parsed " + Bytes.of(rskPublicKeyBytes), e);
                }

                byte[] mstPublicKeyBytes = callSpecArguments[2];
                try {
                    mstPublicKey = ECKey.fromPublicOnly(mstPublicKeyBytes);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("MST public key could not be parsed " + Bytes.of(mstPublicKeyBytes), e);
                }
                executionResult = addFederatorPublicKeyMultikey(dryRun, btcPublicKey, rskPublicKey, mstPublicKey);
            }
            case COMMIT -> {
                Keccak256 pendingFederationHash = new Keccak256(callSpecArguments[0]);
                executionResult = commitFederation(dryRun, pendingFederationHash, eventLogger).getCode();
            }
            case ROLLBACK -> executionResult = rollbackFederation(dryRun);
        }

        boolean executionWasSuccessful = executionResult == 1;
        return new ABICallVoteResult(executionWasSuccessful, executionResult);
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
            return PENDING_FEDERATION_ALREADY_EXISTS.getCode();
        }

        if (proposedFederationExists()) {
            logger.warn("[createPendingFederation] A proposed federation already exists.");
            return PROPOSED_FEDERATION_ALREADY_EXISTS.getCode();
        }

        if (amAwaitingFederationActivation()) {
            logger.warn("[createPendingFederation] There is an existing federation awaiting for activation.");
            return EXISTING_FEDERATION_AWAITING_ACTIVATION.getCode();
        }

        if (getRetiringFederation().isPresent()) {
            logger.warn("[createPendingFederation] There is an existing retiring federation.");
            return RETIRING_FEDERATION_ALREADY_EXISTS.getCode();
        }

        if (dryRun) {
            logger.info("[createPendingFederation] DryRun execution successful.");
            return SUCCESSFUL.getCode();
        }

        PendingFederation pendingFederation = new PendingFederation(Collections.emptyList());

        provider.setPendingFederation(pendingFederation);

        // Clear votes on election
        provider.getFederationElection(constants.getFederationChangeAuthorizer()).clear();

        logger.info("[createPendingFederation] Pending federation created successfully.");
        return SUCCESSFUL.getCode();
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
            return FEDERATION_NON_EXISTENT.getCode();
        }

        if (currentPendingFederation.getBtcPublicKeys().contains(btcKey) ||
            currentPendingFederation.getMembers().stream().map(FederationMember::getRskPublicKey).anyMatch(k -> k.equals(rskKey)) ||
            currentPendingFederation.getMembers().stream().map(FederationMember::getMstPublicKey).anyMatch(k -> k.equals(mstKey))) {
            logger.warn("[addFederatorPublicKeyMultikey] Federator is already part of pending federation.");
            return FEDERATOR_ALREADY_PRESENT.getCode();
        }

        if (dryRun) {
            logger.info("[addFederatorPublicKeyMultikey] DryRun execution successful.");
            return SUCCESSFUL.getCode();
        }

        FederationMember member = new FederationMember(btcKey, rskKey, mstKey);
        currentPendingFederation = currentPendingFederation.addMember(member);

        provider.setPendingFederation(currentPendingFederation);

        logger.info("[addFederatorPublicKeyMultikey] Federator public key added successfully.");
        return SUCCESSFUL.getCode();
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
            return FEDERATION_NON_EXISTENT;
        }

        if (!currentPendingFederation.isComplete()) {
            logger.warn("[commitFederation] Pending federation has {} members, so it does not meet the minimum required.", currentPendingFederation.getMembers().size());
            return INSUFFICIENT_MEMBERS;
        }

        if (!pendingFederationHash.equals(currentPendingFederation.getHash())) {
            logger.warn("[commitFederation] Provided hash {} does not match pending federation hash {}.", pendingFederationHash, currentPendingFederation.getHash());
            return PENDING_FEDERATION_MISMATCHED_HASH;
        }

        if (dryRun) {
            logger.info("[commitFederation] DryRun execution successful.");
            return SUCCESSFUL;
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
        Federation newFederation = buildFederationFromPendingFederation(currentPendingFederation);
        handoverToNewFederation(newFederation);

        clearPendingFederationVoting();

        Federation currentOldFederation = provider.getOldFederation(constants, activations);
        Federation currentNewFederation = provider.getNewFederation(constants, activations);
        logCommitmentWithVotedFederation(eventLogger, currentOldFederation, currentNewFederation);

        return SUCCESSFUL;
    }

    public void commitProposedFederation() {
        Federation proposedFederation = provider.getProposedFederation(constants, activations)
            .orElseThrow(IllegalStateException::new);

        handoverToNewFederation(proposedFederation);
        clearProposedFederation();
    }

    private void handoverToNewFederation(Federation newFederation) {
        moveUTXOsFromNewToOldFederation();

        setOldAndNewFederations(getActiveFederation(), newFederation);

        if (activations.isActive(RSKIP186)) {
            saveLastRetiredFederationScript();
            provider.setNextFederationCreationBlockHeight(newFederation.getCreationBlockNumber());
        }
    }

    private void setOldAndNewFederations(Federation oldFederation, Federation newFederation) {
        provider.setOldFederation(oldFederation);
        provider.setNewFederation(newFederation);
    }

    private void moveUTXOsFromNewToOldFederation() {
        // since the current active fed reference will change from being 'new' to 'old',
        // we have to change the UTXOs reference to match it
        List<UTXO> activeFederationUTXOs = List.copyOf(provider.getNewFederationBtcUTXOs(constants.getBtcParams(), activations));

        // Clear new and old federation's UTXOs
        provider.getNewFederationBtcUTXOs(constants.getBtcParams(), activations).clear();
        List<UTXO> oldFederationUTXOs = provider.getOldFederationBtcUTXOs();
        oldFederationUTXOs.clear();

        // Move UTXOs reference to the old federation
        oldFederationUTXOs.addAll(activeFederationUTXOs);
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

        clearPendingFederationVoting();

        logCommitmentWithVotedFederation(eventLogger, getActiveFederation(), proposedFederation);

        return SUCCESSFUL;
    }

    private Federation buildFederationFromPendingFederation(PendingFederation pendingFederation) {
        Instant federationCreationTime = getFederationCreationTime(rskExecutionBlock.getTimestamp());
        long federationCreationBlockNumber = rskExecutionBlock.getNumber();

        return pendingFederation.buildFederation(federationCreationTime, federationCreationBlockNumber, constants, activations);
    }

    private Instant getFederationCreationTime(long rskExecutionBlockTimestamp) {
        if (!activations.isActive(RSKIP419)) {
            return Instant.ofEpochMilli(rskExecutionBlockTimestamp);
        }

        return Instant.ofEpochSecond(rskExecutionBlockTimestamp);
    }

    private static Script getFederationMembersP2SHScript(ActivationConfig.ForBlock activations, Federation federation) {
        // when the federation is a standard multisig, the members p2sh script is the p2sh script
        if (!activations.isActive(RSKIP377)) {
            return federation.getP2SHScript();
        }
        if (!(federation instanceof ErpFederation)) {
            return federation.getP2SHScript();
        }

        // when the federation also has erp keys, the members p2sh script is the default p2sh script
        return ((ErpFederation) federation).getDefaultP2SHScript();
    }

    private void clearPendingFederationVoting() {
        // Clear pending federation and votes on election
        provider.setPendingFederation(null);
        provider.getFederationElection(constants.getFederationChangeAuthorizer()).clear();
    }

    private void saveLastRetiredFederationScript() {
        Federation activeFederation = getActiveFederation();
        Script activeFederationMembersP2SHScript = getFederationMembersP2SHScript(activations, activeFederation);
        provider.setLastRetiredFederationP2SHScript(activeFederationMembersP2SHScript);
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
            return FEDERATION_NON_EXISTENT.getCode();
        }

        if (dryRun) {
            logger.info("[rollbackFederation] DryRun execution successful.");
            return SUCCESSFUL.getCode();
        }

        provider.setPendingFederation(null);

        // Clear votes on election
        provider.getFederationElection(constants.getFederationChangeAuthorizer()).clear();

        logger.info("[rollbackFederation] Successfully rolled back pending federation.");
        return SUCCESSFUL.getCode();
    }

    @Override
    public boolean isActiveFederationInMigrationAge() {
        long ageBegin = getMigrationAgeStart();
        long ageEnd = getMigrationAgeEnd();
        Federation activeFederation = getActiveFederation();
        long federationAge = getFederationAge(activeFederation);
        boolean isInMigrationAge = ageBegin < federationAge && federationAge < ageEnd;

        logger.trace("[isActiveFederationInMigrationAge] Active federation [address={}] [age={}], is in migration age? [{}].",
            getActiveFederationAddress(), federationAge, isInMigrationAge);

        return isInMigrationAge;
    }

    private long getMigrationAgeEnd() {
        return constants.getFederationActivationAge(activations) + constants.getFundsMigrationAgeSinceActivationEnd(activations);
    }

    private long getMigrationAgeStart() {
        return constants.getFederationActivationAge(activations) + constants.getFundsMigrationAgeSinceActivationBegin();
    }

    private long getFederationAge(Federation federation) {
        return rskExecutionBlock.getNumber() - federation.getCreationBlockNumber();
    }

    @Override
    public boolean isActiveFederationPastMigrationAge() {
        Federation activeFederation = getActiveFederation();
        long federationAge = getFederationAge(activeFederation);
        long ageEnd = getMigrationAgeEnd();

        boolean isPastMigrationAge = federationAge >= ageEnd;

        logger.trace("[isActiveFederationPastMigrationAge] Active federation [address={}] [age={}], is past migration age? [{}].",
            getActiveFederationAddress(), federationAge, isPastMigrationAge);

        return isPastMigrationAge;
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
     * given index. Throws a custom index out of bounds exception when appropriate.
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
