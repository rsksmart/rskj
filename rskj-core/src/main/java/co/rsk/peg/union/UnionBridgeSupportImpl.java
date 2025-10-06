package co.rsk.peg.union;

import static org.ethereum.vm.PrecompiledContracts.BRIDGE_ADDR;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import java.math.BigInteger;
import javax.annotation.Nonnull;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnionBridgeSupportImpl implements UnionBridgeSupport {

    private static final Logger logger = LoggerFactory.getLogger(UnionBridgeSupportImpl.class);

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

    private boolean isCurrentEnvironmentMainnet() {
        String currentNetworkId = constants.getBtcParams().getId();
        return currentNetworkId.equals(NetworkParameters.ID_MAINNET);
    }

    @Override
    public UnionResponseCode setUnionBridgeContractAddressForTestnet(@Nonnull Transaction tx, RskAddress unionBridgeContractAddress) {
        final String SET_UNION_BRIDGE_ADDRESS_TAG = "setUnionBridgeContractAddressForTestnet";
        logger.info("[{}] Setting new union bridge contract address: {}", SET_UNION_BRIDGE_ADDRESS_TAG, unionBridgeContractAddress);

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

        if (!isValidLockingCap(newCap)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        RskAddress txSender = tx.getSender(signatureCache);
        Coin lockingCapBeforeUpdate = getLockingCap();
        storageProvider.setLockingCap(newCap);
        eventLogger.logUnionLockingCapIncreased(txSender, lockingCapBeforeUpdate, newCap);
        logger.info("[{}] Union Locking Cap has been increased. Previous value: {}. New value: {}",
            INCREASE_LOCKING_CAP_TAG, lockingCapBeforeUpdate, newCap);
        return UnionResponseCode.SUCCESS;
    }

    private Coin getWeisTransferredToUnionBridge() {
        return storageProvider.getWeisTransferredToUnionBridge().orElse(Coin.ZERO);
    }

    @Override
    public UnionResponseCode requestUnionRbtc(Transaction tx, Coin amount) {
        final String REQUEST_UNION_RBTC_TAG = "requestUnionRbtc";

        RskAddress caller = tx.getSender(signatureCache);
        if (callerIsNotUnionBridge(caller)) {
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
        logger.trace("[isRequestEnabled] Union Bridge request enabled: {}", isRequestEnabled);

        return isRequestEnabled;
    }

    private void validateCallerIsUnionBridge(RskAddress callerAddress) {
        if (callerIsNotUnionBridge(callerAddress)) {
            throw new IllegalArgumentException("Caller is not the Union Bridge contract address");
        }
    }

    private boolean callerIsNotUnionBridge(RskAddress callerAddress) {
        RskAddress unionBridgeContractAddress = getUnionBridgeContractAddress();
        boolean callerIsNotUnionBridge = !callerAddress.equals(unionBridgeContractAddress);
        if (callerIsNotUnionBridge) {
            logger.warn(
                "[callerIsNotUnionBridge] Caller address {} does not match Union Bridge address {}",
                callerAddress,
                unionBridgeContractAddress
            );
        }
        return callerIsNotUnionBridge;
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

        if (callerIsNotUnionBridge(caller)) {
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
        storageProvider.setUnionBridgeRequestEnabled(requestEnabled);
        storageProvider.setUnionBridgeReleaseEnabled(releaseEnabled);

        eventLogger.logUnionBridgeTransferPermissionsUpdated(txSender, requestEnabled, releaseEnabled);
        logger.info(
            "[{}] Transfer permissions have been updated. Request enabled: {}, Release enabled: {}",
            SET_TRANSFER_PERMISSIONS_TAG,
            requestEnabled,
            releaseEnabled
        );
        return UnionResponseCode.SUCCESS;
    }

    private boolean isTransferPermissionStateAlreadySet(boolean requestEnabled, boolean releaseEnabled) {
        boolean currentRequestEnabled = isRequestEnabled();
        boolean currentReleaseEnabled = isReleaseEnabled();

        return currentRequestEnabled == requestEnabled && currentReleaseEnabled == releaseEnabled;
    }

    @Override
    public byte[] getSuperEvent() {
        return storageProvider.getSuperEvent();
    }

    @Override
    public void setSuperEvent(Transaction tx, @Nonnull byte[] data) {
        RskAddress caller = tx.getSender(signatureCache);
        validateCallerIsUnionBridge(caller);

        int maximumDataLength = 128;
        int dataLength = data.length;
        if (dataLength > maximumDataLength) {
            throw new IllegalArgumentException(String.format("Super event data length %d is above maximum.", dataLength));
        }

        byte[] previousSuperEventData = getSuperEvent();
        storageProvider.setSuperEvent(data);
        logger.info(
            "[setSuperEvent] Super event info was updated from {} to {}", previousSuperEventData, data
        );
    }

    @Override
    public void clearSuperEvent(Transaction tx) {
        RskAddress caller = tx.getSender(signatureCache);
        validateCallerIsUnionBridge(caller);

        byte[] previousSuperEventData = getSuperEvent();
        storageProvider.setSuperEvent(ByteUtil.EMPTY_BYTE_ARRAY);
        logger.info("[clearSuperEvent] Super event info was cleared. Previous value: {}", previousSuperEventData);
    }

    @Override
    public void save() {
        storageProvider.save();
    }
}
