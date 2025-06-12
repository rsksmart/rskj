package co.rsk.peg.union;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.math.BigInteger;
import javax.annotation.Nonnull;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnionBridgeSupportImpl implements UnionBridgeSupport {

    private static final Logger logger = LoggerFactory.getLogger(UnionBridgeSupportImpl.class);

    private static final RskAddress EMPTY_ADDRESS = new RskAddress(new byte[20]);
    public static final String LOG_PATTERN = "[{}] {}";

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

        // Check if the network is MAINNET as the contract address can only be set in testnet or regtest
        if (!isEnvironmentTestnetOrRegtest()) {
            return UnionResponseCode.ENVIRONMENT_DISABLED;
        }

        if (!isChangeUnionAddressAuthorizedCaller(tx)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (!isValidAddress(unionBridgeContractAddress)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        RskAddress currentUnionBridgeAddress = getUnionBridgeContractAddress();
        if (isAddressAlreadyStored(currentUnionBridgeAddress, unionBridgeContractAddress)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        storageProvider.setAddress(unionBridgeContractAddress);
        logger.info("[{}] Union Bridge Contract Address has been updated. Previous address: {} New address: {}", SET_UNION_BRIDGE_ADDRESS_TAG, currentUnionBridgeAddress, unionBridgeContractAddress);
        return UnionResponseCode.SUCCESS;
    }

    private boolean isEnvironmentTestnetOrRegtest() {
        String currentNetworkId = constants.getBtcParams().getId();

        boolean isTestnetOrRegtest = currentNetworkId.equals(NetworkParameters.ID_TESTNET) || currentNetworkId.equals(NetworkParameters.ID_REGTEST);
        if (!isTestnetOrRegtest) {
            String baseMessage = String.format("Union Bridge Contract Address can only be set in Testnet and RegTest environments. Current network: %s", currentNetworkId);
            logger.warn(LOG_PATTERN, "isEnvironmentTestnetOrRegtest", baseMessage);
        }
        return isTestnetOrRegtest;
    }

    private boolean isChangeUnionAddressAuthorizedCaller(Transaction tx) {
        AddressBasedAuthorizer authorizer = constants.getChangeUnionBridgeContractAddressAuthorizer();
        boolean isAuthorized = authorizer.isAuthorized(tx, signatureCache);
        if (!isAuthorized) {
            String baseMessage = String.format("Caller is not authorized to change union bridge contract address. Caller address: %s", tx.getSender());
            logger.warn(LOG_PATTERN, "isChangeUnionAddressAuthorizedCaller", baseMessage);
        }
        return isAuthorized;
    }

    private boolean isValidAddress(RskAddress unionBridgeContractAddress) {
        boolean isValidAddress = unionBridgeContractAddress != null && !unionBridgeContractAddress.equals(EMPTY_ADDRESS);
        if (!isValidAddress) {
            String baseMessage = "Union Bridge Contract Address cannot be null or empty";
            logger.warn(LOG_PATTERN, "isValidAddress", baseMessage);
        }
        return isValidAddress;
    }

    private boolean isAddressAlreadyStored(RskAddress currentUnionBridgeContractAddress, RskAddress newUnionBridgeContractAddress) {
        // Check if the address is already set
        boolean isAddressAlreadyStored = currentUnionBridgeContractAddress.equals(newUnionBridgeContractAddress);
        if (isAddressAlreadyStored) {
            String baseMessage = String.format("The given union bridge contract address is already the current address. Current address: %s", currentUnionBridgeContractAddress);
            logger.warn(LOG_PATTERN, "isAddressAlreadyStored", baseMessage);
        }
        return isAddressAlreadyStored;
    }

    @Override
    public Coin getLockingCap() {
        return storageProvider.getLockingCap().orElse(constants.getInitialLockingCap());
    }

    @Override
    public UnionResponseCode increaseLockingCap(Transaction tx, Coin newCap) {
        final String INCREASE_LOCKING_CAP_TAG = "increaseLockingCap";

        if (!isChangeLockingCapAuthorizedCaller(tx)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (!isValidLockingCap(newCap)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        Coin lockingCapBeforeUpdate = getLockingCap();
        storageProvider.setLockingCap(newCap);
        RskAddress caller = tx.getSender(signatureCache);
        eventLogger.logUnionLockingCapIncreased(caller, lockingCapBeforeUpdate, newCap);

        logger.info("[{}] Union Locking Cap has been increased. Previous value: {}. New value: {}",
            INCREASE_LOCKING_CAP_TAG, lockingCapBeforeUpdate, newCap);
        return UnionResponseCode.SUCCESS;
    }

    private boolean isChangeLockingCapAuthorizedCaller(Transaction tx) {
        AddressBasedAuthorizer authorizer = constants.getChangeLockingCapAuthorizer();
        boolean isAuthorized = authorizer.isAuthorized(tx, signatureCache);
        if (!isAuthorized) {
            String baseMessage = String.format("Caller is not authorized to increase locking cap. Caller address: %s", tx.getSender());
            logger.warn(LOG_PATTERN, "isChangeLockingCapAuthorizedCaller", baseMessage);
        }
        return isAuthorized;
    }

    private Coin getWeisTransferredToUnionBridge() {
        return storageProvider.getWeisTransferredToUnionBridge().orElse(Coin.ZERO);
    }

    @Override
    public UnionResponseCode requestUnionRbtc(Transaction tx, Coin amount) {
        final String REQUEST_UNION_RBTC_TAG = "requestUnionRbtc";

        if (!isCallerUnionBridgeContractAddress(tx)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (!isValidAmount(amount)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        storageProvider.increaseWeisTransferredToUnionBridge(amount);
        logger.info("[{}] Amount requested by the union bridge has been transferred. Amount Requested: {}.", REQUEST_UNION_RBTC_TAG, amount);
        return UnionResponseCode.SUCCESS;
    }

    private boolean isCallerUnionBridgeContractAddress(Transaction tx) {
        RskAddress unionBridgeContractAddress = getUnionBridgeContractAddress();
        boolean isCallerUnionBridgeContractAddress = tx.getSender(signatureCache).equals(unionBridgeContractAddress);
        if (!isCallerUnionBridgeContractAddress) {
            String baseMessage = String.format("Caller is not the Union Bridge Contract Address. Caller address: %s, Union Bridge Contract Address: %s", tx.getSender(), unionBridgeContractAddress);
            logger.warn(LOG_PATTERN, "isCallerUnionBridgeContractAddress", baseMessage);
        }
        return isCallerUnionBridgeContractAddress;
    }

    private boolean isValidAmount(Coin amountRequested) {
        boolean isAmountNullOrLessThanOne = amountRequested == null || amountRequested.compareTo(Coin.ZERO) < 1;
        if (isAmountNullOrLessThanOne) {
            logger.warn(
                "[isValidAmount] Amount requested cannot be negative or zero. Amount requested: {}", amountRequested);
            return false;
        }

        Coin lockingCap = getLockingCap();

        Coin previousAmountRequested = getWeisTransferredToUnionBridge();

        Coin newAmountRequested = previousAmountRequested.add(amountRequested);
        boolean doesNewAmountAndPreviousAmountRequestedSurpassLockingCap =
            newAmountRequested.compareTo(lockingCap) > 0;
        if (doesNewAmountAndPreviousAmountRequestedSurpassLockingCap) {
            logger.warn(
                "[isValidAmount] New amount request + previous amount requested cannot be greater than the Union Locking Cap. Previous amount requested: {}. New amount request: {} . Union Locking Cap: {}", previousAmountRequested, newAmountRequested, lockingCap
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
                newCap, currentLockingCap);
            return false;
        }

        Coin maxLockingCapIncreaseAllowed = currentLockingCap.multiply(
            BigInteger.valueOf(constants.getLockingCapIncrementsMultiplier()));
        if (newCap.compareTo(maxLockingCapIncreaseAllowed) > 0) {
            logger.warn(
                "[isValidLockingCap] Attempted value tries to increase Union Locking Cap above its limit. Value attempted: {} . maxLockingCapIncreasedAllowed: {}",
                newCap, maxLockingCapIncreaseAllowed);
            return false;
        }

        return true;
    }

    @Override
    public UnionResponseCode setTransferPermissions(Transaction tx, boolean requestEnabled,
        boolean releaseEnabled) {
        final String SET_TRANSFER_PERMISSIONS_TAG = "setTransferPermissions";

        if (!isChangeTransferPermissionsAuthorizedCaller(tx)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        storageProvider.setUnionBridgeRequestEnabled(requestEnabled);
        storageProvider.setUnionBridgeReleaseEnabled(releaseEnabled);

        RskAddress caller = tx.getSender(signatureCache);
        eventLogger.logUnionBridgeTransferPermissionsUpdated(caller, requestEnabled, releaseEnabled);
        logger.info("[{}] Transfer permissions have been updated. Request enabled: {}, Release enabled: {}",
            SET_TRANSFER_PERMISSIONS_TAG, requestEnabled, releaseEnabled);
        return UnionResponseCode.SUCCESS;
    }

    private boolean isChangeTransferPermissionsAuthorizedCaller(Transaction tx) {
        AddressBasedAuthorizer authorizer = constants.getChangeTransferPermissionsAuthorizer();
        boolean isAuthorized = authorizer.isAuthorized(tx, signatureCache);
        if (!isAuthorized) {
            String baseMessage = String.format("Caller is not authorized to change transfer permissions. Caller address: %s", tx.getSender());
            logger.warn(LOG_PATTERN, "isChangeTransferPermissionsAuthorizedCaller", baseMessage);
        }
        return isAuthorized;
    }

    @Override
    public void save() {
        storageProvider.save();
    }
}
