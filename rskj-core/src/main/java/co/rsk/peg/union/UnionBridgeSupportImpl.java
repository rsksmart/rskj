package co.rsk.peg.union;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnionBridgeSupportImpl implements UnionBridgeSupport {

    private static final Logger logger = LoggerFactory.getLogger(UnionBridgeSupportImpl.class);

    private static final RskAddress EMPTY_ADDRESS = new RskAddress(new byte[20]);
    public static final String LOG_PATTERN = "[{}] {}";

    private final ActivationConfig.ForBlock activations;
    private final UnionBridgeConstants constants;
    private final UnionBridgeStorageProvider storageProvider;
    private final SignatureCache signatureCache;

    public UnionBridgeSupportImpl(
        ActivationConfig.ForBlock activations,
        UnionBridgeConstants constants,
        UnionBridgeStorageProvider storageProvider,
        SignatureCache signatureCache
    ) {
        this.activations = activations;
        this.constants = constants;
        this.storageProvider = storageProvider;
        this.signatureCache = signatureCache;
    }

    @Override
    public RskAddress getUnionBridgeContractAddress() {
        if (isCurrentEnvironmentMainnet()) {
            return constants.getAddress();
        }

        return storageProvider.getAddress(activations).orElse(constants.getAddress());
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

        if (!isAuthorizedCaller(tx)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (!isValidAddress(unionBridgeContractAddress)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        RskAddress currentUnionBridgeAddress = storageProvider.getAddress(activations).orElse(constants.getAddress());
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

    private boolean isAuthorizedCaller(Transaction tx) {
        AddressBasedAuthorizer authorizer = constants.getChangeAuthorizer();
        boolean isAuthorized = authorizer.isAuthorized(tx, signatureCache);
        if (!isAuthorized) {
            String baseMessage = String.format("Caller is not authorized to execute this method. Caller address: %s", tx.getSender());
            logger.warn(LOG_PATTERN, "isAuthorizedCaller", baseMessage);
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
    public Optional<Coin> getLockingCap() {
        if (!activations.isActive(ConsensusRule.RSKIP502)) {
            return Optional.empty();
        }
        return Optional.of(storageProvider.getLockingCap(activations).orElse(constants.getInitialLockingCap()));
    }

    @Override
    public UnionResponseCode increaseLockingCap(Transaction tx, Coin newCap) {
        final String INCREASE_LOCKING_CAP_TAG = "increaseLockingCap";

        if (!isAuthorizedCaller(tx)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (!isValidLockingCap(newCap)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        storageProvider.setLockingCap(newCap);
        logger.info("[{}] Union Locking Cap has been increased. New value: {}",
            INCREASE_LOCKING_CAP_TAG, newCap.value);
        return UnionResponseCode.SUCCESS;
    }

    @Override
    public UnionResponseCode requestUnionRbtc(Transaction tx, co.rsk.core.Coin amount) {
        final String REQUEST_UNION_RBTC_TAG = "requestUnionRbtc";

        if (!isCallerUnionBridgeContractAddress(tx)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (!isValidAmount(amount)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        storageProvider.setWeisTransferredToUnionBridge(amount);
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

    private boolean isValidAmount(co.rsk.core.Coin amountRequested) {
        boolean isAmountNullOrLessThanOne = amountRequested == null || amountRequested.compareTo(co.rsk.core.Coin.ZERO) < 1;
        if (isAmountNullOrLessThanOne) {
            logger.warn(
                "[isValidAmount] Amount requested cannot be negative or zero. Amount requested: {}", amountRequested);
            return false;
        }

        co.rsk.core.Coin lockingCap = co.rsk.core.Coin.fromBitcoin(getLockingCap().orElse(constants.getInitialLockingCap()));

        co.rsk.core.Coin previousAmountRequested = storageProvider.getWeisTransferredToUnionBridge(activations)
            .orElse(co.rsk.core.Coin.ZERO);

        co.rsk.core.Coin newAmountRequested = previousAmountRequested.add(amountRequested);
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
        Coin currentLockingCap = storageProvider.getLockingCap(activations)
            .orElse(constants.getInitialLockingCap());

        if (newCap.compareTo(currentLockingCap) < 1) {
            logger.warn(
                "[isValidLockingCap] Attempted value doesn't increase Union Locking Cap. Value attempted: {} . currentLockingCap: {}",
                newCap.value, currentLockingCap.value);
            return false;
        }

        Coin maxLockingCapIncreaseAllowed = currentLockingCap.multiply(
            constants.getLockingCapIncrementsMultiplier());
        if (newCap.compareTo(maxLockingCapIncreaseAllowed) > 0) {
            logger.warn(
                "[isValidLockingCap] Attempted value tries to increase Union Locking Cap above its limit. Value attempted: {} . maxLockingCapIncreasedAllowed: {}",
                newCap.value, maxLockingCapIncreaseAllowed.value);
            return false;
        }

        return true;
    }

    @Override
    public void save() {
        storageProvider.save(activations);
    }
}
