package co.rsk.peg.union;

import static org.ethereum.vm.PrecompiledContracts.BRIDGE_ADDR;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.math.BigInteger;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnionBridgeSupportImpl implements UnionBridgeSupport {

    private static final Logger logger = LoggerFactory.getLogger(UnionBridgeSupportImpl.class);
    private static final String LOG_PATTERN = "[{}] {}";

    private final UnionBridgeConstants constants;
    private final UnionBridgeStorageProvider storageProvider;
    private final SignatureCache signatureCache;
    private final BridgeEventLogger eventLogger;

    public UnionBridgeSupportImpl(
        UnionBridgeConstants constants,
        UnionBridgeStorageProvider storageProvider,
        SignatureCache signatureCache,
        BridgeEventLogger eventLogger
    ) {
        this.constants = constants;
        this.storageProvider = storageProvider;
        this.signatureCache = signatureCache;
        this.eventLogger = eventLogger;
    }

    @Override
    public RskAddress getUnionBridgeContractAddress() {
        if (isCurrentEnvironmentMainnet()) {
            return constants.getAddress();
        }

        return storageProvider.getAddress().orElse(constants.getAddress());
    }

    @Override
    public UnionResponseCode setUnionBridgeContractAddressForTestnet(@Nonnull Transaction tx, RskAddress unionBridgeContractAddress) {
        final String SET_UNION_BRIDGE_ADDRESS_TAG = "setUnionBridgeContractAddressForTestnet";
        logger.info("[{}] Setting new union bridge contract address: {}", SET_UNION_BRIDGE_ADDRESS_TAG, unionBridgeContractAddress);

        // Check if the network is MAINNET as the contract address can only be set in testnet or regtest
        if (isCurrentEnvironmentMainnet()) {
            String baseMessage = String.format(
                "Union Bridge Contract Address can only be set in Testnet and RegTest environments. Current network: %s",
                constants.getBtcParams().getId()
            );
            logger.warn(LOG_PATTERN, "setUnionBridgeContractAddressForTestnet", baseMessage);
            return UnionResponseCode.ENVIRONMENT_DISABLED;
        }

        AddressBasedAuthorizer authorizer = constants.getChangeUnionBridgeContractAddressAuthorizer();
        if (!isAuthorized(tx, authorizer)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        RskAddress currentUnionBridgeAddress = getUnionBridgeContractAddress();
        storageProvider.setAddress(unionBridgeContractAddress);
        logger.info(
            "[{}] Union Bridge Contract Address has been updated. Previous address: {} New address: {}",
            SET_UNION_BRIDGE_ADDRESS_TAG,
            currentUnionBridgeAddress,
            unionBridgeContractAddress
        );
        return UnionResponseCode.SUCCESS;
    }

    @Override
    public Coin getLockingCap() {
        return storageProvider.getLockingCap().orElse(constants.getInitialLockingCap());
    }

    @Override
    public UnionResponseCode increaseLockingCap(Transaction tx, Coin newCap) {
        final String INCREASE_LOCKING_CAP_TAG = "increaseLockingCap";

        AddressBasedAuthorizer authorizer = constants.getChangeLockingCapAuthorizer();
        if (!isAuthorized(tx, authorizer)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (!isValidLockingCap(newCap)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        RskAddress txSender = tx.getSender(signatureCache);

        ABICallElection increaseLockingCapElection = storageProvider.getIncreaseLockingCapElection(
            authorizer);
        ABICallSpec increaseLockingCapVote = new ABICallSpec(INCREASE_LOCKING_CAP_TAG, new byte[][]{
            BridgeSerializationUtils.serializeRskCoin(newCap)});

        boolean successfulVote = increaseLockingCapElection.vote(increaseLockingCapVote, txSender);
        if (!successfulVote) {
            logger.warn("[{}] Unsuccessful {} vote", INCREASE_LOCKING_CAP_TAG, increaseLockingCapVote);
            return UnionResponseCode.GENERIC_ERROR;
        }

        Optional<ABICallSpec> electionWinner = increaseLockingCapElection.getWinner();
        if (electionWinner.isEmpty()) {
            logger.info("[{}}] Successful {} vote.", INCREASE_LOCKING_CAP_TAG, increaseLockingCapVote);
            return UnionResponseCode.SUCCESS;
        }

        ABICallSpec winner = electionWinner.get();
        Coin winnerLockingCap;
        try {
            winnerLockingCap = BridgeSerializationUtils.deserializeRskCoin(winner.getArguments()[0]);
        } catch (Exception e) {
            // This block should not be reached if the serialization and deserialization are consistent.
            logger.warn("[{}] Exception deserializing winner value", INCREASE_LOCKING_CAP_TAG, e);
            return UnionResponseCode.GENERIC_ERROR;
        }

        Coin lockingCapBeforeUpdate = getLockingCap();
        storageProvider.setLockingCap(winnerLockingCap);
        eventLogger.logUnionLockingCapIncreased(txSender, lockingCapBeforeUpdate, winnerLockingCap);
        logger.info("[{}] Union Locking Cap has been increased. Previous value: {}. New value: {}",
            INCREASE_LOCKING_CAP_TAG, lockingCapBeforeUpdate, winnerLockingCap);
        increaseLockingCapElection.clear();
        return UnionResponseCode.SUCCESS;
    }

    private Coin getWeisTransferredToUnionBridge() {
        return storageProvider.getWeisTransferredToUnionBridge().orElse(Coin.ZERO);
    }

    @Override
    public UnionResponseCode requestUnionRbtc(Transaction tx, Coin amount) {
        final String REQUEST_UNION_RBTC_TAG = "requestUnionRbtc";

        RskAddress caller = tx.getSender(signatureCache);
        if (!isCallerUnionBridgeContractAddress(caller)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (!isRequestEnabled()) {
            return UnionResponseCode.REQUEST_DISABLED;
        }

        if (!isAmountRequestedValid(amount)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        storageProvider.increaseWeisTransferredToUnionBridge(amount);
        logger.info("[{}] Amount requested by the union bridge has been transferred. Amount Requested: {}.", REQUEST_UNION_RBTC_TAG, amount);
        return UnionResponseCode.SUCCESS;
    }

    private boolean isRequestEnabled() {
        // By default, the request is enabled if the storage provider does not have a specific value set.
        Boolean isRequestEnabled = storageProvider.isUnionBridgeRequestEnabled().orElse(true);
        logger.trace("[{isRequestEnabled}] Union Bridge request enabled: {}", isRequestEnabled);

        return isRequestEnabled;
    }

    private boolean isCallerUnionBridgeContractAddress(RskAddress callerAddress) {
        RskAddress unionBridgeContractAddress = getUnionBridgeContractAddress();
        boolean isCallerUnionBridgeContractAddress = callerAddress.equals(unionBridgeContractAddress);
        if (!isCallerUnionBridgeContractAddress) {
            String baseMessage = String.format(
                "Caller is not the Union Bridge Contract Address. Caller address: %s, Union Bridge Contract Address: %s",
                callerAddress,
                unionBridgeContractAddress
            );
            logger.warn(LOG_PATTERN, "isCallerUnionBridgeContractAddress", baseMessage);
        }
        return isCallerUnionBridgeContractAddress;
    }

    private boolean isAmountRequestedValid(Coin amountRequested) {
        if (amountRequested.compareTo(Coin.ZERO) <= 0) {
            logger.warn(
                "[isAmountRequestedValid] Amount requested must be a positive number. Amount requested: {}",
                amountRequested
            );
            return false;
        }

        Coin lockingCap = getLockingCap();
        Coin previousAmountRequested = getWeisTransferredToUnionBridge();
        Coin newAmountRequested = previousAmountRequested.add(amountRequested);

        boolean doesNewAmountAndPreviousAmountRequestedSurpassLockingCap =
            newAmountRequested.compareTo(lockingCap) > 0;
        if (doesNewAmountAndPreviousAmountRequestedSurpassLockingCap) {
            logger.warn(
                "[isAmountRequestedValid] New amount request + previous amount requested cannot be greater than the Union Locking Cap. Previous amount requested: {}. New amount request: {} . Union Locking Cap: {}",
                previousAmountRequested,
                newAmountRequested,
                lockingCap
            );
            return false;
        }

        return true;
    }

    private boolean isValidLockingCap(Coin newCap) {
        Coin currentLockingCap = getLockingCap();

        if (newCap.compareTo(currentLockingCap) < 1) {
            logger.warn(
                "[isValidLockingCap] Attempted value doesn't increase Union Locking Cap. Value attempted: {} . currentLockingCap: {}",
                newCap,
                currentLockingCap
            );
            return false;
        }

        Coin maxLockingCapIncreaseAllowed = currentLockingCap.multiply(
            BigInteger.valueOf(constants.getLockingCapIncrementsMultiplier())
        );
        if (newCap.compareTo(maxLockingCapIncreaseAllowed) > 0) {
            logger.warn(
                "[isValidLockingCap] Attempted value tries to increase Union Locking Cap above its limit. Value attempted: {} . maxLockingCapIncreasedAllowed: {}",
                newCap,
                maxLockingCapIncreaseAllowed
            );
            return false;
        }

        return true;
    }

    @Override
    public UnionResponseCode releaseUnionRbtc(Transaction tx) {
        final String RELEASE_UNION_RBTC_TAG = "releaseUnionRbtc";

        final RskAddress caller = tx.getSender(signatureCache);
        final Coin releaseUnionRbtcValueInWeis = tx.getValue();

        if (!isCallerUnionBridgeContractAddress(caller)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (!isReleaseEnabled()) {
            return UnionResponseCode.RELEASE_DISABLED;
        }

        if (releaseUnionRbtcValueInWeis.compareTo(Coin.ZERO) <= 0) {
            logger.warn(
                "[{}] Amount to be released must be a positive number. Amount to release: {}.",
                RELEASE_UNION_RBTC_TAG,
                releaseUnionRbtcValueInWeis
            );
            return UnionResponseCode.INVALID_VALUE;
        }

        Coin weisTransferredToUnionBridge = getWeisTransferredToUnionBridge();

        if (weisTransferredToUnionBridge.compareTo(releaseUnionRbtcValueInWeis) < 0) {
            logger.error("[{}] Amount to be released is greater than current amount transferred to the Union Bridge. Amount to release: {}. Current amount Transferred: {}.", RELEASE_UNION_RBTC_TAG, releaseUnionRbtcValueInWeis, weisTransferredToUnionBridge);

            storageProvider.setUnionBridgeRequestEnabled(false);
            storageProvider.setUnionBridgeReleaseEnabled(false);

            eventLogger.logUnionBridgeTransferPermissionsUpdated(BRIDGE_ADDR, false, false);

            logger.warn("[{}] Union Bridge transfer permissions have been disabled due to an invalid amount to release.", RELEASE_UNION_RBTC_TAG);
            return UnionResponseCode.INVALID_VALUE;
        }

        storageProvider.decreaseWeisTransferredToUnionBridge(releaseUnionRbtcValueInWeis);
        eventLogger.logUnionRbtcReleased(caller, releaseUnionRbtcValueInWeis);
        logger.info("[{}] Amount released by the union bridge has been transferred. Amount Released: {}.", RELEASE_UNION_RBTC_TAG, releaseUnionRbtcValueInWeis);
        return UnionResponseCode.SUCCESS;
    }

    private boolean isReleaseEnabled() {
        // By default, the release is enabled if the storage provider does not have a specific value set.
        Boolean isReleaseEnabled = storageProvider.isUnionBridgeReleaseEnabled().orElse(true);
        logger.trace("[isReleaseEnabled] Union Bridge release enabled: {}", isReleaseEnabled);

        return isReleaseEnabled;
    }

    @Override
    public UnionResponseCode setTransferPermissions(Transaction tx, boolean requestEnabled,
        boolean releaseEnabled) {
        final String SET_TRANSFER_PERMISSIONS_TAG = "setTransferPermissions";

        AddressBasedAuthorizer authorizer = constants.getChangeTransferPermissionsAuthorizer();
        if (!isAuthorized(tx, authorizer)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (isTransferPermissionStateAlreadySet(requestEnabled, releaseEnabled)) {
            logger.info(
                "[{}] Transfer permissions are already set to the requested values. Request enabled: {}, Release enabled: {}",
                SET_TRANSFER_PERMISSIONS_TAG,
                requestEnabled,
                releaseEnabled
            );
            return UnionResponseCode.SUCCESS;
        }

        RskAddress txSender = tx.getSender(signatureCache);
        ABICallElection setTransferPermissionsElection = storageProvider.getTransferPermissionsElection(
            authorizer);
        ABICallSpec setTransferPermissionsVote = new ABICallSpec(SET_TRANSFER_PERMISSIONS_TAG, new byte[][]{
            BridgeSerializationUtils.serializeBoolean(requestEnabled),
            BridgeSerializationUtils.serializeBoolean(releaseEnabled)
        });

        boolean successfulVote = setTransferPermissionsElection.vote(setTransferPermissionsVote, txSender);
        if (!successfulVote) {
            logger.warn("[{}] Unsuccessful {} vote", SET_TRANSFER_PERMISSIONS_TAG, setTransferPermissionsVote);
            return UnionResponseCode.GENERIC_ERROR;
        }

        Optional<ABICallSpec> electionWinner = setTransferPermissionsElection.getWinner();
        if (electionWinner.isEmpty()) {
            logger.info("[{}}] Successful {} vote.", SET_TRANSFER_PERMISSIONS_TAG, setTransferPermissionsVote);
            return UnionResponseCode.SUCCESS;
        }

        ABICallSpec winner = electionWinner.get();
        Boolean winnerRequestEnabled;
        Boolean winnerReleaseEnabled;
        try {
            winnerRequestEnabled = BridgeSerializationUtils.deserializeBoolean(
                winner.getArguments()[0]);
            winnerReleaseEnabled = BridgeSerializationUtils.deserializeBoolean(
                winner.getArguments()[1]);
        } catch (Exception e) {
            // This block should not be reached if the serialization and deserialization are consistent.
            logger.warn("[{}] Exception deserializing winner value", SET_TRANSFER_PERMISSIONS_TAG, e);
            return UnionResponseCode.GENERIC_ERROR;
        }

        storageProvider.setUnionBridgeRequestEnabled(winnerRequestEnabled);
        storageProvider.setUnionBridgeReleaseEnabled(winnerReleaseEnabled);

        eventLogger.logUnionBridgeTransferPermissionsUpdated(txSender, winnerRequestEnabled, winnerReleaseEnabled);
        logger.info(
            "[{}] Transfer permissions have been updated. Request enabled: {}, Release enabled: {}",
            SET_TRANSFER_PERMISSIONS_TAG,
            winnerRequestEnabled,
            winnerReleaseEnabled
        );

        setTransferPermissionsElection.clear();
        return UnionResponseCode.SUCCESS;
    }

    private boolean isTransferPermissionStateAlreadySet(boolean requestEnabled, boolean releaseEnabled) {
        boolean currentRequestEnabled = isRequestEnabled();
        boolean currentReleaseEnabled = isReleaseEnabled();

        return currentRequestEnabled == requestEnabled && currentReleaseEnabled == releaseEnabled;
    }

    @Override
    public void save() {
        storageProvider.save();
    }

    private boolean isCurrentEnvironmentMainnet() {
        String currentNetworkId = constants.getBtcParams().getId();
        return currentNetworkId.equals(NetworkParameters.ID_MAINNET);
    }

    private boolean isAuthorized(Transaction tx, AddressBasedAuthorizer authorizer) {
        boolean isAuthorized = authorizer.isAuthorized(tx, signatureCache);
        if (!isAuthorized) {
            String baseMessage = String.format("Caller is not authorized to call this method. Caller address: %s", tx.getSender());
            logger.warn(LOG_PATTERN, "isAuthorized", baseMessage);
        }
        return isAuthorized;
    }
}
